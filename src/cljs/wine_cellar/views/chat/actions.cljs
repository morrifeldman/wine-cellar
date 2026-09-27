(ns wine-cellar.views.chat.actions
  (:require [clojure.string :as string]
            [wine-cellar.api :as api]
            [wine-cellar.nav :as nav]
            [wine-cellar.state :as state-core]
            [wine-cellar.views.chat.context :as chat-context]
            [wine-cellar.views.chat.utils :as chat-utils]))

(defn ensure-conversation!
  "Ensure an active conversation exists before persisting messages."
  [app-state callback]
  (let [conversation-id (get-in @app-state [:chat :active-conversation-id])
        creating? (get-in @app-state [:chat :creating-conversation?])]
    (cond conversation-id (callback conversation-id)
          creating? (js/setTimeout #(ensure-conversation! app-state callback)
                                   100)
          :else
          (let [chat-type (if (= :bar (:view @app-state)) "bar" "wine")
                payload {:provider (get-in @app-state [:ai :provider])
                         :chat_type chat-type}]
            (swap! app-state assoc-in [:chat :creating-conversation?] true)
            (api/create-conversation!
             app-state
             payload
             (fn [{:keys [success conversation error]}]
               (swap! app-state assoc-in [:chat :creating-conversation?] false)
               (if success
                 (callback (:id conversation))
                 (tap> ["ensure-conversation-failed" error]))))))))

(defn persist-conversation-message!
  "Persist a chat message (user or AI) to the backend conversation store.
   `after-save` gets the conversation id and the saved message."
  ([app-state message-map]
   (persist-conversation-message! app-state message-map nil))
  ([app-state message-map after-save]
   (ensure-conversation!
    app-state
    (fn [conversation-id]
      (api/append-conversation-message!
       app-state
       conversation-id
       message-map
       (fn [{:keys [success message error]}]
         (if success
           (when after-save (after-save conversation-id message))
           (tap> ["conversation-message-persist-failed" error]))))))))

(defn- update-message!
  [app-state messages message-id f & args]
  (let [updated
        (swap! messages (fn [current]
                          (mapv #(if (= message-id (:id %)) (apply f % args) %)
                                current)))]
    (swap! app-state assoc-in [:chat :messages] updated)))

(defn- adopt-saved-message!
  "Give a just-sent message its database id, so it can be edited or asked
   again, and the rendered text of its context note, so later requests show
   Claude the same snapshot."
  [app-state messages local-id saved]
  (when saved
    (update-message! app-state
                     messages
                     local-id
                     (fn [message]
                       (cond-> (assoc message :id (:id saved))
                         (:context_note saved)
                         (assoc :context-note (:context_note saved)))))))

(defn- with-context-note
  [message note]
  (if note (assoc message :context-note note) (dissoc message :context-note)))

(defn- persist-ai-reply!
  [app-state response]
  (persist-conversation-message!
   app-state
   {:is_user false :content response}
   (fn [conversation-id _]
     (when conversation-id
       (api/update-conversation-provider! app-state
                                          conversation-id
                                          (get-in @app-state [:ai :provider])))
     (api/load-conversations! app-state {:force? true}))))

(defn- request-ai-reply!
  "Ask the AI to answer the conversation as it stands in `messages`."
  ([app-state messages is-sending? cancel-fn-atom]
   (request-ai-reply! app-state messages is-sending? cancel-fn-atom nil))
  ([app-state messages is-sending? cancel-fn-atom image]
   (reset! cancel-fn-atom
     (api/send-chat-message
      app-state
      @messages
      image
      (fn [response]
        (reset! cancel-fn-atom nil)
        (let [ai-message {:id (random-uuid)
                          :text response
                          :is-user false
                          :timestamp (.getTime (js/Date.))}]
          (swap! messages conj ai-message)
          (swap! app-state assoc-in [:chat :messages] @messages)
          (chat-utils/set-scroll-intent! app-state {:type :ai-top})
          (persist-ai-reply! app-state response)
          (reset! is-sending? false)))))))

(defn handle-send-message
  "Handle sending a message to the AI assistant with optional image"
  [app-state message-text messages is-sending? cancel-fn-atom & [image]]
  (when (and (not @is-sending?) (or (seq message-text) image))
    (reset! is-sending? true)
    (let [note (chat-context/context-note app-state @messages)
          user-message (with-context-note {:id (random-uuid)
                                           :text (or message-text "")
                                           :is-user true
                                           :timestamp (.getTime (js/Date.))}
                                          note)]
      (swap! messages conj user-message)
      (swap! app-state assoc-in [:chat :messages] @messages)
      (chat-utils/set-scroll-intent! app-state {:type :bottom})
      (persist-conversation-message!
       app-state
       (cond-> {:is_user true :content (or message-text "")}
         image (assoc :image_data image)
         note (assoc :context_note note))
       (fn [_ saved]
         (adopt-saved-message! app-state messages (:id user-message) saved)))
      (request-ai-reply! app-state messages is-sending? cancel-fn-atom image))))

(defn commit-local-edit!
  [app-state messages editing-message-id message-ref on-edit-complete
   is-sending? new-history]
  (reset! messages new-history)
  (swap! app-state assoc-in [:chat :messages] new-history)
  (reset! editing-message-id nil)
  (when @message-ref (set! (.-value @message-ref) ""))
  (swap! app-state update :chat dissoc :draft-message)
  (chat-utils/set-scroll-intent! app-state {:type :bottom})
  (when on-edit-complete (on-edit-complete))
  (reset! is-sending? true))

(defn remove-deleted-messages!
  [app-state messages deleted-ids]
  (when-let [ids (seq deleted-ids)]
    (let [delete-set (set ids)
          pruned (swap! messages #(vec (remove (fn [msg]
                                                 (contains? delete-set
                                                            (:id msg)))
                                               %)))]
      (swap! app-state assoc-in [:chat :messages] pruned))))

(defn apply-server-edit!
  [app-state messages message-idx
   {:keys [message deleted-message-ids] :as data}]
  (when-let [sanitized (chat-utils/api-message->ui message)]
    (let [updated (swap! messages #(assoc (vec %) message-idx sanitized))]
      (swap! app-state assoc-in [:chat :messages] updated)))
  (remove-deleted-messages! app-state messages deleted-message-ids)
  data)

(defn handle-edit-send
  [app-state editing-message-id message-ref messages is-sending? cancel-fn-atom
   on-edit-complete]
  (when @message-ref
    (let [message-text (.-value @message-ref)]
      (if-let [message-idx (chat-utils/find-message-index @messages
                                                          @editing-message-id)]
        (let [current @messages
              original-message (nth current message-idx)
              prior (subvec (vec current) 0 message-idx)
              note
              (let [wanted (chat-context/context-note app-state prior)
                    existing (:context-note original-message)]
                ;; Keep the snapshot Claude already saw when the edit
                ;; leaves the wines unchanged
                (if (chat-context/same-note? wanted existing) existing wanted))
              updated-local (-> original-message
                                (assoc :text message-text)
                                (with-context-note note))
              new-history (conj prior updated-local)
              conversation-id (get-in @app-state
                                      [:chat :active-conversation-id])
              message-db-id (:id original-message)]
          (commit-local-edit! app-state
                              messages
                              editing-message-id
                              message-ref
                              on-edit-complete
                              is-sending?
                              new-history)
          (let [follow-up #(request-ai-reply! app-state
                                              messages
                                              is-sending?
                                              cancel-fn-atom)
                handle-update-success
                (fn [data]
                  (apply-server-edit! app-state messages message-idx data)
                  (follow-up))
                handle-update-error
                (fn [error-msg]
                  (reset! is-sending? false)
                  (swap! app-state assoc-in [:chat :error] error-msg)
                  (when (integer? conversation-id)
                    (api/fetch-conversation-messages! app-state
                                                      conversation-id)))]
            (if (and (integer? conversation-id) (integer? message-db-id))
              (api/update-conversation-message!
               app-state
               conversation-id
               message-db-id
               {:content message-text :context_note note :truncate_after? true}
               (fn [{:keys [success data error]}]
                 (if success
                   (handle-update-success data)
                   (handle-update-error (or error
                                            "Failed to update message")))))
              (do (tap> ["conversation-message-update-skipped"
                         {:conversation-id conversation-id
                          :message-id message-db-id}])
                  (follow-up)))))
        (do (reset! editing-message-id nil)
            (set! (.-value @message-ref) "")
            (swap! app-state update :chat dissoc :draft-message)
            (when on-edit-complete (on-edit-complete)))))))

(defn ask-again!
  "Answer the question the conversation ends on."
  [app-state messages is-sending? cancel-fn-atom]
  (when (and (not @is-sending?) (chat-utils/unanswered-question? @app-state))
    (reset! is-sending? true)
    (let [history (vec @messages)
          question (peek history)
          note (chat-context/context-note app-state (pop history))
          conversation-id (get-in @app-state [:chat :active-conversation-id])]
      ;; The question already carries the note for the context it was asked
      ;; in; it only needs a new one if the context has changed since.
      (when-not (chat-context/same-note? note (:context-note question))
        (update-message! app-state
                         messages
                         (:id question)
                         with-context-note
                         note)
        (when (and (integer? conversation-id) (integer? (:id question)))
          (api/update-conversation-message!
           app-state
           conversation-id
           (:id question)
           {:content (:text question) :context_note note}
           (fn [{:keys [success data]}]
             (when success
               (update-message! app-state
                                messages
                                (:id question)
                                with-context-note
                                (get-in data [:message :context_note])))))))
      (request-ai-reply! app-state messages is-sending? cancel-fn-atom))))

(defn fork-conversation!
  "Start a new conversation holding every message up to `message-id`. A fork
   that ends on a question waits for Send, so effort or provider can be
   changed before it is asked again."
  [app-state messages message-id is-sending?]
  (let [conversation-id (get-in @app-state [:chat :active-conversation-id])
        message-idx (chat-utils/find-message-index @messages message-id)]
    (when (and (integer? conversation-id) message-idx (not @is-sending?))
      (api/fork-conversation!
       app-state
       conversation-id
       (inc message-idx)
       (fn [api-messages]
         (let [forked (mapv chat-utils/api-message->ui api-messages)]
           (reset! messages forked)
           (swap! app-state assoc-in [:chat :messages] forked)
           (chat-utils/set-scroll-intent! app-state {:type :bottom})))))))

(defn clear-chat!
  ([app-state messages] (clear-chat! app-state messages nil nil))
  ([app-state messages message-ref pending-image]
   (reset! messages [])
   (when (and message-ref @message-ref) (set! (.-value @message-ref) ""))
   (when pending-image (reset! pending-image nil))
   (state-core/set-context-mode! app-state :wines)
   (swap! app-state (fn [state]
                      (-> state
                          (assoc-in [:chat :messages] [])
                          (assoc-in [:chat :active-conversation-id] nil)
                          (assoc-in [:chat :active-conversation] nil)
                          (assoc-in [:chat :messages-loading?] false)
                          (assoc-in [:chat :creating-conversation?] false)
                          (assoc-in [:chat :draft-message] nil))))))

(defn close-chat!
  [app-state message-ref]
  (when-let [node @message-ref]
    (swap! app-state assoc-in [:chat :draft-message] (.-value node)))
  ;; Unwinding the entry the FAB pushed is what closes the chat. Without
  ;; the param there is no such entry, and going back would leave the page.
  (if (nav/modal-in-url? :chat)
    (nav/back!)
    (swap! app-state assoc-in [:chat :open?] false)))

(defn delete-conversation-with-confirm!
  [app-state {:keys [id title]}]
  (when (and id
             (js/confirm (str "Delete conversation \""
                              (or title (str "Conversation " id))
                              "\"? This cannot be undone.")))
    (api/delete-conversation! app-state id)))

(defn rename-conversation-with-prompt!
  [app-state {:keys [id title]}]
  (when id
    (when-let [new-title (js/prompt "Rename conversation" (or title ""))]
      (let [trimmed (string/trim new-title)]
        (when (and (not (string/blank? trimmed)) (not= trimmed title))
          (api/rename-conversation! app-state id trimmed))))))

(defn toggle-pin!
  [app-state {:keys [id pinned]}]
  (when id (api/set-conversation-pinned! app-state id (not (true? pinned)))))

(defn open-conversation!
  ([app-state messages conversation]
   (open-conversation! app-state messages conversation false))
  ([app-state messages {:keys [id] :as conversation} close-sidebar?]
   (when id
     (when close-sidebar?
       (swap! app-state assoc-in [:chat :sidebar-open?] false))
     (swap! app-state assoc-in [:chat :active-conversation-id] id)
     (swap! app-state assoc-in [:chat :active-conversation] conversation)
     (when-let [provider (:provider conversation)]
       (swap! app-state assoc-in [:ai :provider] (keyword provider)))
     (reset! messages [])
     (swap! app-state assoc-in [:chat :messages] [])
     (chat-context/hold-conversation-context! app-state)
     (api/fetch-conversation-messages! app-state id))))
