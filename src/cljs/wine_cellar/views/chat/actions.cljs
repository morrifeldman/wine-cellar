(ns wine-cellar.views.chat.actions
  (:require [clojure.string :as string]
            [wine-cellar.api :as api]
            [wine-cellar.state :as state-core]
            [wine-cellar.views.chat.context :as chat-context]
            [wine-cellar.views.chat.utils :as chat-utils]))

(declare send-chat-message)

(defn- build-wine-search-state
  [app-state context-mode]
  (chat-context/build-wine-search-state app-state context-mode))

(defn ensure-conversation!
  "Ensure an active conversation exists before persisting messages."
  [app-state wines callback]
  (let [conversation-id (get-in @app-state [:chat :active-conversation-id])
        creating? (get-in @app-state [:chat :creating-conversation?])]
    (cond conversation-id (do (swap! app-state assoc-in
                                [:chat :active-conversation-id]
                                conversation-id)
                              (callback conversation-id))
          creating?
          (js/setTimeout #(ensure-conversation! app-state wines callback) 100)
          :else
          (let [state @app-state
                context-mode (state-core/context-mode state)
                wine-ids (->> wines
                              (map :id)
                              (remove nil?)
                              vec)
                payload (cond-> {:wine_search_state (build-wine-search-state
                                                     app-state
                                                     context-mode)
                                 :provider (get-in @app-state [:ai :provider])}
                          (seq wine-ids) (assoc :wine_ids wine-ids))]
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
  "Persist a chat message (user or AI) to the backend conversation store."
  ([app-state wines message-map]
   (persist-conversation-message! app-state wines message-map nil))
  ([app-state wines message-map after-save]
   (ensure-conversation! app-state
                         wines
                         (fn [conversation-id]
                           (api/append-conversation-message!
                            app-state
                            conversation-id
                            message-map
                            (fn [{:keys [success error]}]
                              (if success
                                (when after-save (after-save conversation-id))
                                (tap> ["conversation-message-persist-failed"
                                       error]))))))))

(defn send-chat-message
  "Send a message to the AI chat endpoint with conversation history and optional image.
   Provider is read from app-state by api.cljs.
   Returns a cancel function."
  ([app-state message wines include? conversation-history callback]
   (send-chat-message app-state
                      message
                      wines
                      include?
                      conversation-history
                      nil
                      callback))
  ([app-state message wines include? conversation-history image callback]
   (tap> ["send-chat-message" message conversation-history
          {:include? include?}])
   (api/send-chat-message app-state
                          message
                          wines
                          include?
                          conversation-history
                          image
                          callback)))

(defn handle-send-message
  "Handle sending a message to the AI assistant with optional image"
  [app-state message-text messages is-sending? cancel-fn-atom auto-scroll? &
   [image]]
  (when (and (not @is-sending?) (or (seq message-text) image))
    (reset! is-sending? true)
    (let [state @app-state
          user-message {:id (random-uuid)
                        :text (or message-text "")
                        :is-user true
                        :timestamp (.getTime (js/Date.))}
          include? (state-core/include-wines? state)
          wines (chat-context/context-wines app-state)]
      (swap! messages conj user-message)
      (swap! app-state assoc-in [:chat :messages] @messages)
      (when auto-scroll? (reset! auto-scroll? true))
      (persist-conversation-message!
       app-state
       wines
       (cond-> {:is_user true :content (or message-text "")}
         image (assoc :image_data image))
       (fn [conversation-id]
         (chat-context/sync-conversation-context! app-state
                                                  wines
                                                  conversation-id)))
      (let [cancel-fn
            (send-chat-message
             app-state
             message-text
             wines
             include?
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
                 (when auto-scroll? (reset! auto-scroll? true))
                 (persist-conversation-message!
                  app-state
                  wines
                  {:is_user false :content response}
                  (fn [conversation-id]
                    (when conversation-id
                      (api/update-conversation-provider!
                       app-state
                       conversation-id
                       (get-in @app-state [:ai :provider])))
                    (api/load-conversations! app-state {:force? true})))
                 (reset! is-sending? false))))]
        (reset! cancel-fn-atom cancel-fn)))))

(defn commit-local-edit!
  [app-state messages editing-message-id message-ref auto-scroll?
   on-edit-complete is-sending? new-history]
  (reset! messages new-history)
  (swap! app-state assoc-in [:chat :messages] new-history)
  (reset! editing-message-id nil)
  (when @message-ref (set! (.-value @message-ref) ""))
  (swap! app-state update :chat dissoc :draft-message)
  (when auto-scroll? (reset! auto-scroll? true))
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

(defn sync-context-if-needed!
  [app-state wines conversation-id]
  (when (and (integer? conversation-id) wines)
    (chat-context/sync-conversation-context! app-state wines conversation-id)))

(defn enqueue-ai-followup!
  [app-state messages message-text wines include? auto-scroll? is-sending?
   cancel-fn-atom]
  (let [history @messages
        cancel-fn (send-chat-message
                   app-state
                   message-text
                   wines
                   include?
                   history
                   (fn [response]
                     (reset! cancel-fn-atom nil)
                     (let [ai-message {:id (random-uuid)
                                       :text response
                                       :is-user false
                                       :timestamp (.getTime (js/Date.))}]
                       (swap! messages conj ai-message)
                       (swap! app-state assoc-in [:chat :messages] @messages)
                       (when auto-scroll? (reset! auto-scroll? true))
                       (persist-conversation-message!
                        app-state
                        wines
                        {:is_user false :content response}
                        (fn [conversation-id]
                          (when conversation-id
                            (api/update-conversation-provider!
                             app-state
                             conversation-id
                             (get-in @app-state [:ai :provider])))
                          (api/load-conversations! app-state {:force? true})))
                       (reset! is-sending? false))))]
    (reset! cancel-fn-atom cancel-fn)))

(defn handle-edit-send
  [app-state editing-message-id message-ref messages is-sending? cancel-fn-atom
   auto-scroll? on-edit-complete]
  (when @message-ref
    (let [message-text (.-value @message-ref)]
      (if-let [message-idx (chat-utils/find-message-index @messages
                                                          @editing-message-id)]
        (let [current @messages
              original-message (nth current message-idx)
              updated-local (assoc original-message :text message-text)
              new-history (conj (vec (take message-idx current)) updated-local)
              state @app-state
              include? (state-core/include-wines? state)
              wines (chat-context/context-wines app-state)
              conversation-id (get-in @app-state
                                      [:chat :active-conversation-id])
              message-db-id (:id original-message)]
          (commit-local-edit! app-state
                              messages
                              editing-message-id
                              message-ref
                              auto-scroll?
                              on-edit-complete
                              is-sending?
                              new-history)
          (let [follow-up #(enqueue-ai-followup! app-state
                                                 messages
                                                 message-text
                                                 wines
                                                 include?
                                                 auto-scroll?
                                                 is-sending?
                                                 cancel-fn-atom)
                handle-update-success
                (fn [data]
                  (apply-server-edit! app-state messages message-idx data)
                  (sync-context-if-needed! app-state wines conversation-id)
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
               {:content message-text :truncate_after? true}
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

(defn clear-chat!
  ([app-state messages] (clear-chat! app-state messages nil nil))
  ([app-state messages message-ref pending-image]
   (reset! messages [])
   (when (and message-ref @message-ref) (set! (.-value @message-ref) ""))
   (when pending-image (reset! pending-image nil))
   (state-core/set-context-mode! app-state :selection+filters)
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
  (swap! app-state assoc-in [:chat :open?] false))

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
     (when-let [search-state (:wine_search_state conversation)]
       (chat-context/apply-wine-search-state! app-state search-state))
     (api/fetch-conversation-messages! app-state id))))
