(ns wine-cellar.views.chat.actions
  (:require [clojure.string :as string]
            [wine-cellar.api :as api]
            [wine-cellar.views.components.confirm :refer [confirm!]]
            [wine-cellar.nav :as nav]
            [wine-cellar.state :as state-core]
            [wine-cellar.views.chat.context :as chat-context]
            [wine-cellar.views.chat.utils :as chat-utils]))

;; The conversation lives in app-state under [:chat :messages]; these are
;; the only ways the actions touch it.
(defn- current-messages [app-state] (vec (get-in @app-state [:chat :messages])))

(defn- set-messages!
  [app-state msgs]
  (swap! app-state assoc-in [:chat :messages] (vec msgs)))

(defn- update-messages!
  [app-state f & args]
  (swap! app-state update-in [:chat :messages] #(apply f (vec %) args)))

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
            ;; A failure shows in the chat dialog.
            (-> (api/create-conversation! app-state payload)
                (.then #(callback (:id %)) (fn [_]))
                (.finally #(swap! app-state assoc-in
                             [:chat :creating-conversation?]
                             false)))))))

(defn persist-conversation-message!
  "Persist a chat message (user or AI) to the backend conversation store.
   `after-save` gets the conversation id and the saved message."
  ([app-state message-map]
   (persist-conversation-message! app-state message-map nil))
  ([app-state message-map after-save]
   (ensure-conversation!
    app-state
    (fn [conversation-id]
      (.then
       (api/append-conversation-message! app-state conversation-id message-map)
       #(when after-save (after-save conversation-id %))
       (fn [_]))))))

(defn- update-message!
  [app-state message-id f & args]
  (update-messages! app-state
                    (fn [current]
                      (mapv #(if (= message-id (:id %)) (apply f % args) %)
                            current))))

(defn- adopt-saved-message!
  "Give a just-sent message its database id, so it can be edited or asked
   again, and the rendered text of its context note, so later requests show
   Claude the same snapshot."
  [app-state local-id saved]
  (when saved
    (update-message! app-state
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
  "Ask the AI to answer the conversation as it stands."
  ([app-state is-sending? cancel-fn-atom]
   (request-ai-reply! app-state is-sending? cancel-fn-atom nil))
  ([app-state is-sending? cancel-fn-atom image]
   (reset! cancel-fn-atom (api/send-chat-message
                           app-state
                           (current-messages app-state)
                           image
                           (fn [response]
                             (reset! cancel-fn-atom nil)
                             (let [ai-message {:id (random-uuid)
                                               :text response
                                               :is-user false
                                               :timestamp (.getTime
                                                           (js/Date.))}]
                               (update-messages! app-state conj ai-message)
                               (chat-utils/set-scroll-intent! app-state
                                                              {:type :ai-top})
                               (persist-ai-reply! app-state response)
                               (reset! is-sending? false)))))))

(defn handle-send-message
  "Handle sending a message to the AI assistant with optional image"
  [app-state message-text is-sending? cancel-fn-atom & [image]]
  (when (and (not @is-sending?) (or (seq message-text) image))
    (reset! is-sending? true)
    (let [note (chat-context/context-note app-state
                                          (current-messages app-state))
          user-message (with-context-note {:id (random-uuid)
                                           :text (or message-text "")
                                           :is-user true
                                           :timestamp (.getTime (js/Date.))}
                                          note)]
      (update-messages! app-state conj user-message)
      (chat-utils/set-scroll-intent! app-state {:type :bottom})
      (persist-conversation-message!
       app-state
       (cond-> {:is_user true :content (or message-text "")}
         image (assoc :image_data image)
         note (assoc :context_note note))
       (fn [_ saved] (adopt-saved-message! app-state (:id user-message) saved)))
      (request-ai-reply! app-state is-sending? cancel-fn-atom image))))

(defn commit-local-edit!
  [app-state editing-message-id message-ref on-edit-complete is-sending?
   new-history]
  (set-messages! app-state new-history)
  (reset! editing-message-id nil)
  (when @message-ref (set! (.-value @message-ref) ""))
  (swap! app-state update :chat dissoc :draft-message)
  (chat-utils/set-scroll-intent! app-state {:type :bottom})
  (when on-edit-complete (on-edit-complete))
  (reset! is-sending? true))

(defn remove-deleted-messages!
  [app-state deleted-ids]
  (when-let [ids (seq deleted-ids)]
    (let [delete-set (set ids)]
      (update-messages!
       app-state
       (fn [current] (vec (remove #(contains? delete-set (:id %)) current)))))))

(defn apply-server-edit!
  [app-state message-idx {:keys [message deleted-message-ids] :as data}]
  (when-let [sanitized (chat-utils/api-message->ui message)]
    (update-messages! app-state assoc message-idx sanitized))
  (remove-deleted-messages! app-state deleted-message-ids)
  data)

(defn handle-edit-send
  [app-state editing-message-id message-ref is-sending? cancel-fn-atom
   on-edit-complete]
  (when @message-ref
    (let [message-text (.-value @message-ref)]
      (if-let [message-idx (chat-utils/find-message-index (current-messages
                                                           app-state)
                                                          @editing-message-id)]
        (let [current (current-messages app-state)
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
                              editing-message-id
                              message-ref
                              on-edit-complete
                              is-sending?
                              new-history)
          (let [follow-up
                #(request-ai-reply! app-state is-sending? cancel-fn-atom)
                handle-update-success
                (fn [data]
                  (apply-server-edit! app-state message-idx data)
                  (follow-up))
                ;; The failure itself already shows in the chat dialog.
                handle-update-error (fn [_]
                                      (reset! is-sending? false)
                                      (when (integer? conversation-id)
                                        (api/fetch-conversation-messages!
                                         app-state
                                         conversation-id)))]
            (if (and (integer? conversation-id) (integer? message-db-id))
              (.then (api/update-conversation-message! app-state
                                                       conversation-id
                                                       message-db-id
                                                       {:content message-text
                                                        :context_note note
                                                        :truncate_after? true})
                     handle-update-success
                     handle-update-error)
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
  [app-state is-sending? cancel-fn-atom]
  (when (and (not @is-sending?) (chat-utils/unanswered-question? @app-state))
    (reset! is-sending? true)
    (let [history (vec (current-messages app-state))
          question (peek history)
          note (chat-context/context-note app-state (pop history))
          conversation-id (get-in @app-state [:chat :active-conversation-id])]
      ;; The question already carries the note for the context it was asked
      ;; in; it only needs a new one if the context has changed since.
      (when-not (chat-context/same-note? note (:context-note question))
        (update-message! app-state (:id question) with-context-note note)
        (when (and (integer? conversation-id) (integer? (:id question)))
          (.then (api/update-conversation-message! app-state
                                                   conversation-id
                                                   (:id question)
                                                   {:content (:text question)
                                                    :context_note note})
                 #(update-message! app-state
                                   (:id question)
                                   with-context-note
                                   (get-in % [:message :context_note]))
                 (fn [_]))))
      (request-ai-reply! app-state is-sending? cancel-fn-atom))))

(defn fork-conversation!
  "Start a new conversation holding every message up to `message-id`. A fork
   that ends on a question waits for Send, so effort or provider can be
   changed before it is asked again."
  [app-state message-id is-sending?]
  (let [conversation-id (get-in @app-state [:chat :active-conversation-id])
        message-idx (chat-utils/find-message-index (current-messages app-state)
                                                   message-id)]
    (when (and (integer? conversation-id) message-idx (not @is-sending?))
      (.then
       (api/fork-conversation! app-state conversation-id (inc message-idx))
       (fn [api-messages]
         (let [forked (mapv chat-utils/api-message->ui api-messages)]
           (set-messages! app-state forked)
           (chat-utils/set-scroll-intent! app-state {:type :bottom})))
       (fn [_])))))

(defn clear-chat!
  ([app-state] (clear-chat! app-state nil nil))
  ([app-state message-ref pending-image]
   (set-messages! app-state [])
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
  (when id
    (confirm! app-state
              {:title
               (str "Delete \"" (or title (str "Conversation " id)) "\"?")
               :message "This conversation will be gone for good."
               :confirm-label "Delete"
               :danger? true
               :on-confirm #(api/delete-conversation! app-state id)})))

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
  ([app-state conversation] (open-conversation! app-state conversation false))
  ([app-state {:keys [id] :as conversation} close-sidebar?]
   (when id
     (when close-sidebar?
       (swap! app-state assoc-in [:chat :sidebar-open?] false))
     (swap! app-state assoc-in [:chat :active-conversation-id] id)
     (swap! app-state assoc-in [:chat :active-conversation] conversation)
     (when-let [provider (:provider conversation)]
       (swap! app-state assoc-in [:ai :provider] (keyword provider)))
     (set-messages! app-state [])
     (chat-context/hold-conversation-context! app-state)
     (api/fetch-conversation-messages! app-state id))))
