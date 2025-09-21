(ns wine-cellar.api
  (:require [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! go chan put!]]
            [wine-cellar.config :as config]
            [wine-cellar.state :refer [initial-app-state]]
            [wine-cellar.utils.filters :as filters]))

(def headless-mode? (r/atom false))

(defn enable-headless-mode!
  []
  (reset! headless-mode? true)
  (js/console.log "Headless mode enabled - API calls will be intercepted"))

(defn disable-headless-mode!
  []
  (reset! headless-mode? false)
  (js/console.log
   "Headless mode disabled - API calls will be processed normally"))

(def api-base-url (config/get-api-base-url))

(def default-opts {:with-credentials? true})

(defn handle-api-response
  [response error-msg]
  (cond
    ;; If the response is successful, return the body
    (:success response) {:success true :data (:body response)}
    ;; If we got a 401 Unauthorized, redirect to login
    (= 401 (:status response))
    (do (js/console.log "Authentication required, redirecting to login")
        ;; Don't try to parse the response body if it's not valid JSON
        (js/setTimeout #(set! (.-location js/window)
                              (str api-base-url "/auth/google"))
                       100)
        {:success false :error "Authentication required"})
    ;; Otherwise, handle the error
    :else (let [error-message (try
                                ;; Try to get the error from the response
                                ;; body
                                (get-in response [:body :error])
                                ;; If that fails, use the default error
                                ;; message
                                (catch js/Error _ error-msg))]
            {:success false :error error-message})))

(defn api-request
  [method url params error-msg]
  (let [result-chan (chan)]
    (if @headless-mode?
      ;; In headless mode, just log the call and return success without
      ;; doing anything
      (do (js/console.log "API CALL INTERCEPTED (headless mode):"
                          (name method)
                          url
                          (when params (clj->js params)))
          (go (put! result-chan {:success true :data nil})))
      ;; Normal mode - make the actual API call
      (go (let [request-opts (merge default-opts
                                    (when params {:json-params params}))
                response (<! (method (str api-base-url url) request-opts))
                result (handle-api-response response error-msg)]
            (put! result-chan result))))
    result-chan))

;; Helper functions for common HTTP methods
(defn GET [url error-msg] (api-request http/get url nil error-msg))

(defn POST [url params error-msg] (api-request http/post url params error-msg))

(defn PUT [url params error-msg] (api-request http/put url params error-msg))

(defn DELETE [url error-msg] (api-request http/delete url nil error-msg))

(defn logout
  []
  (js/console.log "Logging out...")
  (set! (.-href (.-location js/window)) (str api-base-url "/auth/logout")))

;; Classification endpoints

(defn fetch-classifications
  [app-state]
  (go (let [result (<! (GET "/api/classifications"
                            "Failed to fetch classifications"))]
        (if (:success result)
          (swap! app-state assoc :classifications (:data result))
          (swap! app-state assoc :error (:error result))))))

(defn create-classification
  [app-state classification]
  (js/console.log "Sending classification data:" (clj->js classification))
  (go (let [result (<! (POST "/api/classifications"
                             classification
                             "Failed to create classification"))]
        (if (:success result)
          (do (swap! app-state assoc
                :creating-classification? false
                :new-classification nil)
              (fetch-classifications app-state))
          (swap! app-state assoc :error (:error result))))))

(defn update-classification
  [app-state id updates]
  (go (let [result (<! (PUT (str "/api/classifications/" id)
                            updates
                            "Failed to update classification"))]
        (if (:success result)
          (do (fetch-classifications app-state)
              (swap! app-state assoc :editing-classification nil))
          (swap! app-state assoc :error (:error result))))))

(defn delete-classification
  [app-state id]
  (go (let [result (<! (DELETE (str "/api/classifications/" id)
                               "Failed to delete classification"))]
        (tap> ["delete-classification" id result])
        (if (:success result)
          (do (fetch-classifications app-state)
              (swap! app-state dissoc :deleting-classification))
          (swap! app-state assoc :error (:error result))))))

;; Wine endpoints
(defn fetch-wines
  [app-state]
  (swap! app-state assoc :loading? true)
  (js/console.log "Fetching wines...")
  (go (let [result (<! (GET "/api/wines/list" "Failed to fetch wines"))]
        (if (:success result)
          (do (js/console.log "Success! Wines count:" (count (:data result)))
              (swap! app-state assoc
                :wines (:data result)
                :loading? false
                :error nil))
          (do
            (js/console.log "Error fetching wines:" (:error result))
            (swap! app-state assoc :error (:error result) :loading? false))))))

(defn fetch-wine-details
  [app-state wine-id & {:keys [include-images] :or {include-images true}}]
  (let [result-chan (chan)]
    (go
     (let [url (str "/api/wines/by-id/"
                    wine-id
                    (when include-images "?include_images=true"))
           result (<! (GET url "Failed to fetch wine details"))]
       (if (:success result)
         (let [wine-with-details (:data result)]
           ;; Update the wine in the list with full details including the
           ;; full image if requested
           (swap! app-state update
             :wines
             (fn [wines]
               (map #(if (= (:id %) wine-id)
                       ;; Keep the latest_rating
                       (merge % wine-with-details)
                       %)
                    wines)))
           ;; Set as selected wine
           (swap! app-state assoc :selected-wine-id wine-id)
           (put! result-chan {:success true :data wine-with-details}))
         (do (swap! app-state assoc :error (:error result))
             (put! result-chan {:success false :error (:error result)})))))
    result-chan))

(defn create-wine
  [app-state wine]
  (js/console.log "Sending wine data:" (clj->js wine))
  (go
   (let [result (<! (POST "/api/wines" wine "Failed to create wine"))]
     (if (:success result)
       (do
         ;; Use replaceState BEFORE updating app state to fix back button
         ;; behavior. This replaces the add wine form entry in history
         ;; before navigation
         (.replaceState js/history nil "" "/")
         (fetch-wines app-state)
         (fetch-classifications app-state) ;; Refresh classifications
                                           ;; after adding a wine
         (swap! app-state assoc
           :new-wine {}
           :window-reason nil
           :show-wine-form? false
           :submitting-wine? false))
       (swap! app-state assoc
         :error (:error result)
         :submitting-wine? false)))))

(defn delete-wine
  [app-state id]
  (go
   (let [result (<! (DELETE (str "/api/wines/by-id/" id)
                            "Failed to delete wine"))]
     (if (:success result)
       (swap! app-state update :wines #(remove (fn [wine] (= (:id wine) id)) %))
       (swap! app-state assoc :error (:error result))))))

;; Tasting Notes endpoints
(defn fetch-tasting-notes
  [app-state wine-id]
  (go (let [result (<! (GET (str "/api/wines/by-id/" wine-id "/tasting-notes")
                            "Failed to fetch tasting notes"))]
        (if (:success result)
          (swap! app-state assoc :tasting-notes (:data result))
          (swap! app-state assoc :error (:error result))))))


(defn create-tasting-note
  [app-state wine-id note notes-ref]
  (go (let [result (<! (POST (str "/api/wines/by-id/" wine-id "/tasting-notes")
                             note
                             "Failed to create tasting note"))]
        (if (:success result)
          (do
            (swap! app-state update :tasting-notes conj (:data result))
            (swap! app-state assoc :new-tasting-note {} :submitting-note? false)
            ;; Clear the notes field after successful creation
            (when (and notes-ref @notes-ref) (set! (.-value @notes-ref) "")))
          (swap! app-state assoc
            :error (:error result)
            :submitting-note? false)))))

(defn update-tasting-note
  [app-state wine-id note-id note]
  (go (let [result (<! (PUT (str "/api/wines/by-id/" wine-id
                                 "/tasting-notes/" note-id)
                            note
                            "Failed to update tasting note"))]
        (if (:success result)
          (do (swap! app-state update
                :tasting-notes
                (fn [notes]
                  (map #(if (= (:id %) note-id) (:data result) %) notes)))
              (swap! app-state assoc
                :editing-note-id nil
                :submitting-note? false
                :new-tasting-note {}))
          (swap! app-state assoc
            :error (:error result)
            :submitting-note? false)))))

(defn delete-tasting-note
  [app-state wine-id note-id]
  (go (let [result (<! (DELETE (str "/api/wines/by-id/" wine-id
                                    "/tasting-notes/" note-id)
                               "Failed to delete tasting note"))]
        (if (:success result)
          (swap! app-state update
            :tasting-notes
            #(remove (fn [note] (= (:id note) note-id)) %))
          (swap! app-state assoc :error (:error result))))))

(defn fetch-tasting-note-sources
  [app-state]
  (go (let [result (<! (GET "/api/tasting-note-sources"
                            "Failed to fetch tasting note sources"))]
        (if (:success result)
          (swap! app-state assoc :tasting-note-sources (:data result))
          (js/console.error "Failed to fetch tasting note sources:"
                            (:error result))))))

(defn adjust-wine-quantity
  [app-state wine-id adjustment]
  (go (let [result (<! (POST
                        (str "/api/wines/by-id/" wine-id "/adjust-quantity")
                        {:adjustment adjustment}
                        "Failed to update wine quantity"))]
        (if (:success result)
          (swap! app-state update
            :wines
            (fn [wines]
              (map #(if (= (:id %) wine-id) (update % :quantity + adjustment) %)
                   wines)))
          (swap! app-state assoc :error (:error result))))))



(defn update-wine
  [app-state id updates]
  (let [promise (js/Promise.
                 (fn [resolve reject]
                   (go (let [result (<! (PUT (str "/api/wines/by-id/" id)
                                             updates
                                             "Failed to update wine"))]
                         (if (:success result)
                           (let [updated-wine (:data result)]
                             ;; Update the wine in the list
                             (swap! app-state update
                               :wines
                               (fn [wines]
                                 (map #(if (= (:id %) id) updated-wine %)
                                      wines)))
                             (resolve updated-wine))
                           (do (swap! app-state assoc :error (:error result))
                               (reject (:error result))))))))]
    promise))

(defn update-wine-image
  [app-state wine-id image-data]
  (go (let [result (<! (PUT (str "/api/wines/by-id/" wine-id "/image")
                            image-data
                            "Failed to update wine image"))]
        (if (:success result)
          (let [updated-wine (:data result)]
            ;; Update the wine in the list
            (swap! app-state update
              :wines
              (fn [wines]
                (map #(if (= (:id %) wine-id) updated-wine %) wines))))
          (swap! app-state assoc :error (:error result))))))

(defn analyze-wine-label
  [app-state image-data]
  (swap! app-state assoc :analyzing-label? true)
  (js/Promise. (fn [resolve reject]
                 (go (let [result (<! (POST "/api/wines/analyze-label"
                                            image-data
                                            "Failed to analyze wine label"))]
                       (swap! app-state assoc :analyzing-label? false)
                       (if (:success result)
                         (resolve (:data result))
                         (do (swap! app-state assoc :error (:error result))
                             (reject (:error result)))))))))

(defn suggest-drinking-window
  [app-state wine]
  (swap! app-state assoc :suggesting-drinking-window? true)
  (js/Promise. (fn [resolve reject]
                 (go
                  (let [provider (some-> (get-in @app-state [:chat :provider]) name)
                        payload (cond-> {:wine wine}
                                   provider (assoc :provider provider))
                        result (<! (POST "/api/wines/suggest-drinking-window"
                                         payload
                                         "Failed to suggest drinking window"))]
                    (swap! app-state assoc :suggesting-drinking-window? false)
                    (if (:success result)
                      (resolve (:data result))
                      (do (swap! app-state assoc :error (:error result))
                          (reject (:error result)))))))))

(defn generate-wine-summary
  [app-state wine]
  (js/Promise. (fn [resolve reject]
                 (go (let [result (<! (POST "/api/wines/generate-summary"
                                            (let [provider (some-> (get-in @app-state [:chat :provider]) name)]
                                              (cond-> {:wine wine}
                                                provider (assoc :provider provider)))
                                            "Failed to generate wine summary"))]
                       (if (:success result)
                         (resolve (:data result))
                         (do (swap! app-state assoc :error (:error result))
                             (reject (:error result)))))))))

(defn fetch-grape-varieties
  [app-state]
  (go (let [result (<! (GET "/api/grape-varieties"
                            "Failed to fetch grape varieties"))]
        (if (:success result)
          (swap! app-state assoc :grape-varieties (:data result))
          (swap! app-state assoc :error (:error result))))))

(defn create-grape-variety
  [app-state variety]
  (js/console.log "Creating grape variety:" (clj->js variety))
  (js/Promise. (fn [resolve reject]
                 (go (let [result (<! (POST "/api/grape-varieties"
                                            variety
                                            "Failed to create grape variety"))]
                       (if (:success result)
                         (do (fetch-grape-varieties app-state)
                             (swap! app-state assoc
                               :new-grape-variety {}
                               :submitting-variety? false
                               :show-variety-form? false)
                             (resolve (:data result)))
                         (do (swap! app-state assoc
                               :error (:error result)
                               :submitting-variety? false)
                             (reject (:error result)))))))))

(defn update-grape-variety
  [app-state id updates]
  (go (let [result (<! (PUT (str "/api/grape-varieties/" id)
                            updates
                            "Failed to update grape variety"))]
        (if (:success result)
          (do (fetch-grape-varieties app-state)
              (swap! app-state assoc
                :editing-variety-id nil
                :submitting-variety? false
                :show-variety-form? false))
          (swap! app-state assoc
            :error (:error result)
            :submitting-variety? false)))))

(defn delete-grape-variety
  [app-state id]
  (go (let [result (<! (DELETE (str "/api/grape-varieties/" id)
                               "Failed to delete grape variety"))]
        (if (:success result)
          (swap! app-state update
            :grape-varieties
            #(remove (fn [variety] (= (:id variety) id)) %))
          (swap! app-state assoc :error (:error result))))))

;; Wine Varieties endpoints
(defn fetch-wine-varieties
  [app-state wine-id]
  (go (let [result (<! (GET (str "/api/wines/by-id/" wine-id "/varieties")
                            "Failed to fetch wine varieties"))]
        (if (:success result)
          (swap! app-state assoc :wine-varieties (:data result))
          (swap! app-state assoc :error (:error result))))))

(defn add-variety-to-wine
  [app-state wine-id variety]
  (tap> ["add-variety-to-wine" wine-id variety])
  (go (let [result (<! (POST (str "/api/wines/by-id/" wine-id "/varieties")
                             variety
                             "Failed to add variety to wine"))]
        (if (:success result)
          (do (fetch-wine-varieties app-state wine-id)
              (swap! app-state assoc
                :new-wine-variety {}
                :submitting-wine-variety? false
                :show-wine-variety-form? false))
          (swap! app-state assoc
            :error (:error result)
            :submitting-wine-variety? false)))))

(defn update-wine-variety-percentage
  [app-state wine-id variety-id percentage]
  (go (let [result (<! (PUT (str "/api/wines/by-id/" wine-id
                                 "/varieties/" variety-id)
                            {:percentage percentage}
                            "Failed to update variety percentage"))]
        (if (:success result)
          (do (fetch-wine-varieties app-state wine-id)
              (swap! app-state assoc
                :editing-wine-variety-id nil
                :submitting-wine-variety? false
                :show-wine-variety-form? false))
          (swap! app-state assoc
            :error (:error result)
            :submitting-wine-variety? false)))))

(defn remove-variety-from-wine
  [app-state wine-id variety-id]
  (go (let [result (<! (DELETE (str "/api/wines/by-id/" wine-id
                                    "/varieties/" variety-id)
                               "Failed to remove variety from wine"))]
        (if (:success result)
          (swap! app-state update
            :wine-varieties
            #(remove (fn [v] (= (:variety_id v) variety-id)) %))
          (swap! app-state assoc :error (:error result))))))

;; Chat endpoints

(defn- conversation-sort-key
  [{:keys [pinned last_message_at created_at]}]
  [(if pinned 0 1)
   (- (or (some-> last_message_at (js/Date.) .getTime)
          (some-> created_at (js/Date.) .getTime)
          0))
   (- (or (some-> created_at (js/Date.) .getTime) 0))])

(defn- sort-conversations
  [conversations]
  (->> conversations
       (sort-by conversation-sort-key)
       vec))

(defn- upsert-conversation
  [conversations conversation]
  (let [without (remove #(= (:id %) (:id conversation)) conversations)]
    (sort-conversations (cons conversation without))))

(defn- apply-conversation-update!
  [state conversation]
  (let [chat (:chat state)
        convs (or (:conversations chat) [])
        updated (upsert-conversation convs conversation)
        active? (= (:active-conversation-id chat) (:id conversation))
        base (-> state
                 (assoc-in [:chat :conversations] updated)
                 (assoc-in [:chat :error] nil))]
    (if active?
      (assoc-in base [:chat :active-conversation] conversation)
      base)))

(defn load-conversations!
  "Fetch conversations for the authenticated user and store them in app state."
  ([app-state]
   (load-conversations! app-state {}))
  ([app-state opts]
   (let [{:keys [force?] :or {force? false}} opts
         chat-state (:chat @app-state)
         loading? (:conversation-loading? chat-state)
         loaded? (:conversations-loaded? chat-state)]
     (when (and (not loading?) (or force? (not loaded?)))
       (swap! app-state
              (fn [state]
                (-> state
                    (assoc-in [:chat :conversation-loading?] true)
                    (assoc-in [:chat :conversations-loaded?] false))))
       (go
        (let [result (<! (GET "/api/conversations"
                               "Failed to load conversations"))]
          (if (:success result)
            (let [conversations (vec (:data result))
                  sorted (sort-conversations conversations)]
              (tap> ["conversations-loaded" (count conversations)])
              (swap! app-state
                     (fn [state]
                       (let [active-id (get-in state [:chat :active-conversation-id])
                             active (some #(when (= (:id %) active-id) %) sorted)]
                         (-> state
                             (assoc-in [:chat :conversation-loading?] false)
                             (assoc-in [:chat :conversations-loaded?] true)
                             (assoc-in [:chat :conversations] sorted)
                             (assoc-in [:chat :active-conversation] active)
                             (assoc-in [:chat :error] nil))))))
            (do
              (tap> ["conversations-load-error" (:error result)])
              (swap! app-state
                     (fn [state]
                       (-> state
                           (assoc-in [:chat :conversation-loading?] false)
                           (assoc-in [:chat :conversations-loaded?] true)
                           (assoc-in [:chat :error] (:error result)))))))))))))

(defn create-conversation!
  ([app-state payload]
   (create-conversation! app-state payload nil))
  ([app-state payload callback]
   (go
    (let [result (<! (POST "/api/conversations"
                           payload
                           "Failed to create conversation"))]
      (if (:success result)
        (let [conversation (:data result)]
          (tap> ["conversation-created" (:id conversation)])
          (swap! app-state
                 (fn [state]
                   (-> state
                       (assoc-in [:chat :conversations]
                                 (upsert-conversation (or (get-in state [:chat :conversations]) [])
                                                      conversation))
                       (assoc-in [:chat :active-conversation] conversation)
                       (assoc-in [:chat :active-conversation-id] (:id conversation))
                       (assoc-in [:chat :conversations-loaded?] true)
                       (assoc-in [:chat :error] nil))))
          (when callback
            (callback {:success true :conversation conversation})))
        (do (tap> ["conversation-create-error" (:error result)])
            (swap! app-state assoc-in [:chat :error] (:error result))
             (when callback
               (callback {:success false :error (:error result)}))))))))

(defn rename-conversation!
  ([app-state conversation-id title]
   (rename-conversation! app-state conversation-id title nil))
  ([app-state conversation-id title callback]
   (when conversation-id
     (swap! app-state assoc-in [:chat :renaming-conversation-id] conversation-id)
     (go
      (let [result (<! (PUT (str "/api/conversations/" conversation-id)
                            {:title title}
                            "Failed to update conversation"))]
        (swap! app-state assoc-in [:chat :renaming-conversation-id] nil)
        (if (:success result)
          (let [conversation (:data result)]
            (tap> ["conversation-renamed" (:id conversation)])
            (swap! app-state apply-conversation-update! conversation)
            (when callback
              (callback {:success true :conversation conversation})))
          (do (tap> ["conversation-rename-error" (:error result)])
              (swap! app-state assoc-in [:chat :error] (:error result))
              (when callback
                (callback {:success false :error (:error result)})))))))))

(defn set-conversation-pinned!
  [app-state conversation-id pinned?]
  (when conversation-id
    (swap! app-state assoc-in [:chat :pinning-conversation-id] conversation-id)
    (go
     (let [result (<! (PUT (str "/api/conversations/" conversation-id)
                           {:pinned pinned?}
                           "Failed to update conversation"))]
       (swap! app-state assoc-in [:chat :pinning-conversation-id] nil)
       (if (:success result)
         (let [conversation (:data result)]
           (tap> ["conversation-pinned" {:id (:id conversation)
                                          :pinned (:pinned conversation)}])
           (swap! app-state apply-conversation-update! conversation)
           (load-conversations! app-state {:force? true}))
         (do (tap> ["conversation-pin-error" (:error result)])
             (swap! app-state assoc-in [:chat :error] (:error result))))))))

(defn delete-conversation!
  [app-state conversation-id]
  (when conversation-id
    (swap! app-state assoc-in [:chat :deleting-conversation-id] conversation-id)
    (go
     (let [result (<! (DELETE (str "/api/conversations/" conversation-id)
                              "Failed to delete conversation"))]
       (swap! app-state assoc-in [:chat :deleting-conversation-id] nil)
       (if (:success result)
         (do
           (swap! app-state
                  (fn [state]
                    (let [chat (:chat state)
                          filtered (->> (:conversations chat)
                                        (remove #(= (:id %) conversation-id)))
                          sorted (sort-conversations filtered)
                          active? (= (:active-conversation-id chat) conversation-id)
                          base-state (-> state
                                         (assoc-in [:chat :conversations] sorted)
                                         (assoc-in [:chat :conversations-loaded?] false)
                                         (assoc-in [:chat :error] nil))]
                      (if active?
                        (-> base-state
                            (assoc-in [:chat :active-conversation-id] nil)
                            (assoc-in [:chat :active-conversation] nil)
                            (assoc-in [:chat :messages] [])
                            (assoc-in [:chat :messages-loading?] false))
                        base-state))))
           (tap> ["conversation-deleted" conversation-id])
           (load-conversations! app-state {:force? true}))
         (do (tap> ["conversation-delete-error" (:error result)])
             (swap! app-state assoc-in [:chat :error] (:error result))))))))

(defn append-conversation-message!
  ([app-state conversation-id message]
   (append-conversation-message! app-state conversation-id message nil))
  ([app-state conversation-id message callback]
   (let [payload (cond-> message
                   (nil? (:image_data message)) (dissoc :image_data)
                   (nil? (:tokens_used message)) (dissoc :tokens_used))]
     (go
      (let [result (<! (POST (str "/api/conversations/" conversation-id "/messages")
                              payload
                              "Failed to save conversation message"))]
        (if (:success result)
          (let [saved (:data result)]
            (tap> ["conversation-message-saved" (:id saved)])
            (swap! app-state assoc-in [:chat :error] nil)
            (when callback
              (callback {:success true :message saved})))
          (do (tap> ["conversation-message-save-error" (:error result)])
              (swap! app-state assoc-in [:chat :error] (:error result))
              (when callback
                (callback {:success false :error (:error result)})))))))))

(defn fetch-conversation-messages!
  [app-state conversation-id]
  (swap! app-state assoc-in [:chat :messages-loading?] true)
  (go
   (let [result (<! (GET (str "/api/conversations/" conversation-id "/messages")
                          "Failed to load conversation messages"))]
     (swap! app-state assoc-in [:chat :messages-loading?] false)
     (if (:success result)
       (let [messages (:data result)]
         (tap> ["conversation-messages-loaded" {:conversation-id conversation-id
                                                 :count (count messages)}])
         (swap! app-state assoc-in [:chat :messages]
                (mapv (fn [m]
                        {:id (:id m)
                         :text (:content m)
                         :is-user (:is_user m)
                         :timestamp (some-> (:created_at m)
                                            js/Date.parse
                                            js/Date.)})
                      messages))
         (swap! app-state assoc-in [:chat :error] nil))
       (do (tap> ["conversation-messages-load-error" (:error result)])
           (swap! app-state assoc-in [:chat :error] (:error result)))))))

(defn send-chat-message
  "Send a message to the AI chat endpoint with wine IDs and conversation history.
   Optionally includes provider override and image data."
  ([message wines conversation-history provider callback]
   (send-chat-message message wines conversation-history provider nil callback))
  ([message wines conversation-history provider image callback]
   (go
    (let [wine-ids (mapv :id wines)
          payload (cond-> {:wine-ids wine-ids
                           :conversation-history conversation-history}
                    (seq message) (assoc :message message)
                    provider (assoc :provider (if (keyword? provider)
                                                (name provider)
                                                provider))
                    image (assoc :image image))]
      (let [result (<!
                    (POST "/api/chat" payload "Failed to send chat message"))]
        (if (:success result)
          (callback (:data result))
          (callback
           "Sorry, I'm having trouble connecting right now. Please try again later.")))))))

;; Admin endpoints

(defn mark-all-wines-unverified
  "Admin function to mark all wines as unverified"
  [app-state]
  (go (let [result (<! (POST "/api/admin/mark-all-unverified"
                             {}
                             "Failed to mark wines as unverified"))]
        (if (:success result)
          (do
            ;; Refresh the wines list to show updated verification status
            (fetch-wines app-state)
            (swap! app-state assoc
              :success
              (str "Successfully marked "
                   (get-in result [:data :wines-updated])
                   " wines as unverified")))
          (swap! app-state assoc :error (:error result))))))

(defn poll-job-status
  "Poll job status until completion"
  [app-state job-id]
  (tap> ["🔍 Polling job status for" job-id])
  (go
   (let [result (<! (GET (str "/api/admin/job-status/" job-id)
                         "Failed to get job status"))]
     (tap> ["📊 Job status result:" result])
     (if (:success result)
       (let [status (:data result)
             job-status (:status status)
             progress (:progress status)
             total (:total status)]
         (tap> ["📈 Job status details:" "job-status" job-status "progress"
                progress "total" total])
         (swap! app-state assoc
           :job-progress
           {:progress (or progress 0) :total (or total 0) :status job-status})
         (tap> ["🔄 Updated app-state with progress"])
         (cond (= job-status "completed")
               (do (swap! app-state dissoc
                     :regenerating-drinking-windows?
                     :job-progress)
                   (fetch-wines app-state)
                   (let [failed-wines (:failed-wines status)
                         failed-count (count failed-wines)
                         success-count (- total failed-count)
                         message
                         (if (> failed-count 0)
                           (str "Regenerated " success-count
                                "/" total
                                " wines successfully. " failed-count
                                " failed: " (clojure.string/join
                                             ", "
                                             (map #(str "ID " (:wine-id %))
                                                  failed-wines)))
                           (str "Successfully regenerated drinking windows for "
                                total
                                " wines"))]
                     (swap! app-state assoc :success message))
                   (js/setTimeout #(swap! app-state dissoc :success) 8000))
               (= job-status "failed") (do (swap! app-state dissoc
                                             :regenerating-drinking-windows?
                                             :job-progress)
                                           (swap! app-state assoc
                                             :error
                                             (str "Job failed: "
                                                  (:error status))))
               (= job-status "running")
               ;; Continue polling
               (js/setTimeout #(poll-job-status app-state job-id) 2000)
               :else
               ;; Unknown status, stop polling
               (swap! app-state dissoc
                 :regenerating-drinking-windows?
                 :job-progress)))
       ;; Error getting status
       (do
         (swap! app-state dissoc :regenerating-drinking-windows? :job-progress)
         (swap! app-state assoc :error "Failed to check job status"))))))

(defn regenerate-filtered-drinking-windows
  "Admin function to regenerate drinking windows for currently filtered wines"
  [app-state]
  (let [filtered-wines (filters/filtered-sorted-wines app-state)
        wine-ids (map :id filtered-wines)
        wine-count (count wine-ids)]
    (when (> wine-count 0)
      (swap! app-state assoc :regenerating-drinking-windows? true)
      (go (let [result (<! (POST "/api/admin/start-drinking-window-job"
                                 {:wine-ids wine-ids}
                                 "Failed to start drinking window job"))]
            (if (:success result)
              (let [job-id (get-in result [:data :job-id])]
                (tap> ["🚀 Starting polling for job:" job-id])
                ;; Start polling for job status
                (poll-job-status app-state job-id))
              (do (swap! app-state dissoc :regenerating-drinking-windows?)
                  (swap! app-state assoc :error (:error result)))))))))

(defn reset-database
  "Admin function to reset the database"
  [app-state]
  (go (swap! app-state assoc :resetting-database? true)
      (let [result (<! (POST "/api/admin/reset-database"
                             {}
                             "Failed to reset database"))]
        (swap! app-state assoc :resetting-database? false)
        (if (:success result)
          (do
            ;; Completely reset app state to initial values
            (reset! app-state (assoc initial-app-state
                                     :success
                                     "Database reset successfully!")))
          (swap! app-state assoc :error (:error result))))))

(defn load-wine-detail-page
  "Load all data needed for the wine detail page"
  [app-state wine-id]
  (swap! app-state assoc :selected-wine-id wine-id)
  (swap! app-state assoc :new-tasting-note {})
  (fetch-tasting-notes app-state wine-id)
  (fetch-wine-details app-state wine-id)
  (fetch-wine-varieties app-state wine-id)
  (fetch-tasting-note-sources app-state))

(defn exit-wine-detail-page
  "Clean up state when leaving wine detail page"
  [app-state]
  ;; Clear wine detail specific state
  (swap! app-state dissoc
    :selected-wine-id :tasting-notes
    :editing-note-id :window-suggestion
    :new-tasting-note :wine-varieties)
  ;; Refresh wine list to get updated ratings from database
  (fetch-wines app-state))
