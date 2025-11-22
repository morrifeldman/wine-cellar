(ns wine-cellar.api
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! go chan put!]]
            [wine-cellar.config :as config]
            [wine-cellar.state :refer [initial-app-state]]
            [wine-cellar.utils.filters :as filters]))


(def headless-mode? (r/atom false))
(def job-status-failure-test (r/atom nil))

(defn enable-headless-mode!
  []
  (reset! headless-mode? true)
  (js/console.log "Headless mode enabled - API calls will be intercepted"))

(defn disable-headless-mode!
  []
  (reset! headless-mode? false)
  (js/console.log
   "Headless mode disabled - API calls will be processed normally"))

(defn enable-job-status-failure-test!
  ([attempts]
   (enable-job-status-failure-test! attempts
                                    "Simulated job status failure (test)"))
  ([attempts error-msg]
   (reset! job-status-failure-test {:remaining (max 0 (or attempts 0))
                                    :error error-msg})
   (js/console.log "Job status failure test enabled" @job-status-failure-test)))

(defn disable-job-status-failure-test!
  []
  (reset! job-status-failure-test nil)
  (js/console.log "Job status failure test disabled"))

#_(enable-job-status-failure-test! 3 "Simulated network failure")

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

(defn- encode-query-params
  [params]
  (let [pairs (for [[k v] params
                    :when (some? v)]
                (str (name k) "=" (js/encodeURIComponent (str v))))]
    (when (seq pairs) (str "?" (string/join "&" pairs)))))

(defn logout
  []
  (js/console.log "Logging out...")
  (set! (.-href (.-location js/window)) (str api-base-url "/auth/logout")))

(defn fetch-model-info
  [app-state]
  (go (let [result (<! (GET "/api/admin/model-info"
                            "Failed to fetch model info"))]
        (when (:success result)
          (let [data (:data result)
                default-provider (keyword (:default-provider data))]
            (swap! app-state assoc-in [:ai :models] (:models data))
            ;; Set default provider if none is currently set
            (when-not (get-in @app-state [:ai :provider])
              (swap! app-state assoc-in [:ai :provider] default-provider)))))))

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

;; Cellar condition endpoints

(defn fetch-latest-cellar-conditions
  [app-state {:keys [device-id]}]
  (swap! app-state assoc-in [:cellar-conditions :loading-latest?] true)
  (go (let [query (encode-query-params {:device_id device-id})
            result (<! (GET (str "/api/cellar-conditions/latest" query)
                            "Failed to fetch latest cellar readings"))]
        (if (:success result)
          (swap! app-state update
            :cellar-conditions
            (fn [state]
              (-> state
                  (assoc :latest (:data result))
                  (assoc :loading-latest? false)
                  (dissoc :error))))
          (swap! app-state update
            :cellar-conditions
            (fn [state]
              (assoc state :loading-latest? false :error (:error result))))))))

(defn fetch-cellar-series
  [app-state {:keys [device-id bucket from to]}]
  (swap! app-state assoc-in [:cellar-conditions :loading-series?] true)
  (go (let [query (encode-query-params
                   {:device_id device-id :bucket bucket :from from :to to})
            result (<! (GET (str "/api/cellar-conditions/series" query)
                            "Failed to fetch cellar series"))]
        (if (:success result)
          (swap! app-state update
            :cellar-conditions
            (fn [state]
              (-> state
                  (assoc :series (:data result))
                  (assoc :loading-series? false)
                  (dissoc :error))))
          (swap! app-state update
            :cellar-conditions
            (fn [state]
              (assoc state :loading-series? false :error (:error result))))))))

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
  (let [provider (get-in @app-state [:ai :provider])
        payload (assoc image-data :provider provider)]
    (js/Promise. (fn [resolve reject]
                   (go (let [result (<! (POST "/api/wines/analyze-label"
                                              payload
                                              "Failed to analyze wine label"))]
                         (swap! app-state assoc :analyzing-label? false)
                         (if (:success result)
                           (resolve (:data result))
                           (do (swap! app-state assoc :error (:error result))
                               (reject (:error result))))))))))

(defn suggest-drinking-window
  [app-state wine]
  (swap! app-state assoc :suggesting-drinking-window? true)
  (js/Promise. (fn [resolve reject]
                 (go
                  (let [provider (get-in @app-state [:ai :provider])
                        payload {:wine wine :provider provider}
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
  (js/Promise.
   (fn [resolve reject]
     (go (let [result (<! (POST "/api/wines/generate-summary"
                                (let [provider (some-> (get-in @app-state
                                                               [:ai :provider])
                                                       name)]
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
   (- (or (some-> last_message_at
                  (js/Date.)
                  .getTime)
          (some-> created_at
                  (js/Date.)
                  .getTime)
          0))
   (- (or (some-> created_at
                  (js/Date.)
                  .getTime)
          0))])

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
      (-> base
          (assoc-in [:chat :active-conversation] conversation)
          (cond-> (:provider conversation) (assoc-in [:ai :provider]
                                            (keyword (:provider
                                                      conversation)))))
      base)))

(defn load-conversations!
  "Fetch conversations for the authenticated user and store them in app state."
  ([app-state] (load-conversations! app-state {}))
  ([app-state opts]
   (let [{:keys [force?] :or {force? false}} opts
         chat-state (:chat @app-state)
         loading? (:conversation-loading? chat-state)
         loaded? (:conversations-loaded? chat-state)]
     (when (and (not loading?) (or force? (not loaded?)))
       (swap! app-state (fn [state]
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
                        (cond-> (:provider active) (assoc-in [:ai :provider]
                                                    (keyword (:provider
                                                              active))))
                        (assoc-in [:chat :error] nil))))))
            (do (tap> ["conversations-load-error" (:error result)])
                (swap! app-state
                  (fn [state]
                    (-> state
                        (assoc-in [:chat :conversation-loading?] false)
                        (assoc-in [:chat :conversations-loaded?] true)
                        (assoc-in [:chat :error] (:error result)))))))))))))

(defn create-conversation!
  ([app-state payload] (create-conversation! app-state payload nil))
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
                            (upsert-conversation
                             (or (get-in state [:chat :conversations]) [])
                             conversation))
                  (assoc-in [:chat :active-conversation] conversation)
                  (assoc-in [:chat :active-conversation-id] (:id conversation))
                  (cond-> (:provider conversation) (assoc-in [:ai :provider]
                                                    (keyword (:provider
                                                              conversation))))
                  (assoc-in [:chat :conversations-loaded?] true)
                  (assoc-in [:chat :error] nil))))
          (when callback (callback {:success true :conversation conversation})))
        (do (tap> ["conversation-create-error" (:error result)])
            (swap! app-state assoc-in [:chat :error] (:error result))
            (when callback
              (callback {:success false :error (:error result)}))))))))

(defn rename-conversation!
  ([app-state conversation-id title]
   (rename-conversation! app-state conversation-id title nil))
  ([app-state conversation-id title callback]
   (when conversation-id
     (swap! app-state assoc-in
       [:chat :renaming-conversation-id]
       conversation-id)
     (go (let [result (<! (PUT (str "/api/conversations/" conversation-id)
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
    (go (let [result (<! (PUT (str "/api/conversations/" conversation-id)
                              {:pinned pinned?}
                              "Failed to update conversation"))]
          (swap! app-state assoc-in [:chat :pinning-conversation-id] nil)
          (if (:success result)
            (let [conversation (:data result)]
              (tap> ["conversation-pinned"
                     {:id (:id conversation) :pinned (:pinned conversation)}])
              (swap! app-state apply-conversation-update! conversation)
              (load-conversations! app-state {:force? true}))
            (do (tap> ["conversation-pin-error" (:error result)])
                (swap! app-state assoc-in [:chat :error] (:error result))))))))

(defn update-conversation-provider!
  [app-state conversation-id provider]
  (when (and conversation-id provider)
    (go (let [result (<! (PUT (str "/api/conversations/" conversation-id)
                              {:provider provider}
                              "Failed to update conversation"))]
          (if (:success result)
            (swap! app-state apply-conversation-update! (:data result))
            (swap! app-state assoc-in [:chat :error] (:error result)))))))

(defn update-conversation-context!
  [app-state conversation-id {:keys [wine-ids wine-search-state]}]
  (when conversation-id
    (go (let [provider (get-in @app-state [:ai :provider])
              payload (cond-> {:provider provider}
                        (some? wine-ids) (assoc :wine_ids (vec wine-ids))
                        (some? wine-search-state) (assoc :wine_search_state
                                                         wine-search-state))]
          (when (seq payload)
            (let [result (<! (PUT (str "/api/conversations/" conversation-id)
                                  payload
                                  "Failed to update conversation"))]
              (if (:success result)
                (swap! app-state apply-conversation-update! (:data result))
                (swap! app-state assoc-in [:chat :error] (:error result)))))))))

(defn delete-conversation!
  [app-state conversation-id]
  (when conversation-id
    (swap! app-state assoc-in [:chat :deleting-conversation-id] conversation-id)
    (go
     (let [result (<! (DELETE (str "/api/conversations/" conversation-id)
                              "Failed to delete conversation"))]
       (swap! app-state assoc-in [:chat :deleting-conversation-id] nil)
       (if (:success result)
         (do (swap! app-state
               (fn [state]
                 (let [chat (:chat state)
                       filtered (->> (:conversations chat)
                                     (remove #(= (:id %) conversation-id)))
                       sorted (sort-conversations filtered)
                       active? (= (:active-conversation-id chat)
                                  conversation-id)
                       base-state (-> state
                                      (assoc-in [:chat :conversations] sorted)
                                      (assoc-in [:chat :conversations-loaded?]
                                                false)
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
      (let [result (<! (POST
                        (str "/api/conversations/" conversation-id "/messages")
                        payload
                        "Failed to save conversation message"))]
        (if (:success result)
          (let [saved (:data result)
                message (:message saved)
                conversation (:conversation saved)
                message (or message saved)]
            (tap> ["conversation-message-saved" (:id message)])
            (when conversation
              (swap! app-state apply-conversation-update! conversation))
            (swap! app-state assoc-in [:chat :error] nil)
            (when callback
              (callback
               {:success true :message message :conversation conversation})))
          (do (tap> ["conversation-message-save-error" (:error result)])
              (swap! app-state assoc-in [:chat :error] (:error result))
              (when callback
                (callback {:success false :error (:error result)})))))))))

(defn update-conversation-message!
  ([app-state conversation-id message-id message]
   (update-conversation-message! app-state
                                 conversation-id
                                 message-id
                                 message
                                 nil))
  ([app-state conversation-id message-id message callback]
   (let [payload
         (cond-> {:content (:content message)}
           (contains? message :image_data) (assoc :image (:image_data message))
           (contains? message :tokens_used) (assoc :tokens_used
                                                   (:tokens_used message))
           (true? (:truncate_after? message)) (assoc :truncate_after? true))]
     (go
      (let [result (<! (PUT (str "/api/conversations/" conversation-id
                                 "/messages/" message-id)
                            payload
                            "Failed to update conversation message"))]
        (if (:success result)
          (let [data (:data result)
                conversation (:conversation data)]
            (tap> ["conversation-message-updated"
                   {:conversation-id conversation-id
                    :message-id message-id
                    :deleted (count (:deleted-message-ids data))}])
            (when conversation
              (swap! app-state apply-conversation-update! conversation))
            (swap! app-state assoc-in [:chat :error] nil)
            (when callback (callback {:success true :data data})))
          (do (tap> ["conversation-message-update-error" (:error result)])
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
         (tap> ["conversation-messages-loaded"
                {:conversation-id conversation-id :count (count messages)}])
         (swap! app-state assoc-in
           [:chat :messages]
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
   Provider is read from app-state. Optionally includes image data."
  ([app-state message wines include? conversation-history callback]
   (send-chat-message app-state
                      message
                      wines
                      include?
                      conversation-history
                      nil
                      callback))
  ([app-state message wines include? conversation-history image callback]
   (go
    (let
      [provider (get-in @app-state [:ai :provider])
       wine-ids (->> wines
                     (map :id)
                     (remove nil?)
                     vec)
       payload (cond-> {:conversation-history conversation-history
                        :include-visible-wines? include?
                        :provider provider}
                 (seq message) (assoc :message message)
                 (and include? (seq wine-ids)) (assoc :wine-ids wine-ids)
                 image (assoc :image image))
       fallback-msg
       "Sorry, I'm having trouble connecting right now. Please try again later."]
      (if-let [result
               (<! (POST "/api/chat" payload "Failed to send chat message"))]
        (if (:success result) (callback (:data result)) (callback fallback-msg))
        (callback fallback-msg))))))

;; Admin endpoints

(defn mark-all-wines-unverified
  "Admin function to mark all wines as unverified"
  [app-state]
  (go (if-let [result (<! (POST "/api/admin/mark-all-unverified"
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
          (swap! app-state assoc :error (:error result)))
        (swap! app-state assoc :error "Failed to mark wines as unverified"))))

(defn fetch-verbose-logging-state
  [app-state]
  (swap! app-state (fn [state]
                     (-> state
                         (assoc-in [:verbose-logging :loading?] true)
                         (assoc-in [:verbose-logging :error] nil))))
  (go
   (let [result (<! (GET "/api/admin/verbose-logging"
                         "Failed to get verbose logging state"))]
     (swap! app-state assoc-in [:verbose-logging :loading?] false)
     (if (:success result)
       (let [verbose? (boolean (get-in result [:data :verbose?]))]
         (swap! app-state assoc-in [:verbose-logging :enabled?] verbose?)
         (swap! app-state assoc-in [:verbose-logging :error] nil))
       (swap! app-state assoc-in [:verbose-logging :error] (:error result))))))

(defn set-verbose-logging-state
  [app-state enabled?]
  (swap! app-state (fn [state]
                     (-> state
                         (assoc-in [:verbose-logging :updating?] true)
                         (assoc-in [:verbose-logging :error] nil))))
  (go
   (let [result (<! (POST "/api/admin/verbose-logging"
                          {:enabled? enabled?}
                          "Failed to update verbose logging state"))]
     (swap! app-state assoc-in [:verbose-logging :updating?] false)
     (if (:success result)
       (let [verbose? (boolean (get-in result [:data :verbose?]))]
         (swap! app-state assoc-in [:verbose-logging :enabled?] verbose?)
         (swap! app-state assoc-in [:verbose-logging :error] nil))
       (swap! app-state assoc-in [:verbose-logging :error] (:error result))))))

(def max-job-status-retries 5)
(def base-job-status-delay-ms 2000)
(def max-job-status-delay-ms 20000)

(defn- job-type-config
  [job-type]
  (case job-type
    :wine-summary {:in-progress-key :regenerating-wine-summaries?
                   :success-label "wine summaries"}
    {:in-progress-key :regenerating-drinking-windows?
     :success-label "drinking windows"}))

(defn- format-success-message
  [job-type total failed-wines]
  (let [{:keys [success-label]} (job-type-config job-type)
        failed-count (count failed-wines)
        success-count (- total failed-count)
        failures-text (when (> failed-count 0)
                        (str " " failed-count
                             " failed: " (string/join ", "
                                                      (map #(str "ID "
                                                                 (:wine-id %))
                                                           failed-wines))))]
    (if (> failed-count 0)
      (str "Regenerated " success-count
           "/" total
           " wines successfully." failures-text)
      (str "Successfully regenerated " success-label " for " total " wines"))))

(defn- format-failure-message
  [job-type status]
  (let [{:keys [success-label]} (job-type-config job-type)]
    (str "Job failed while regenerating " success-label ": " (:error status))))

(defn- retryable-job-status-error?
  [error-message]
  (if-not (string? error-message)
    true
    (not (some #(string/includes? error-message %)
               ["Authentication required" "Job not found"]))))

(defn poll-job-status
  "Poll job status until completion"
  ([app-state job-id]
   (poll-job-status app-state job-id {:job-type :drinking-window}))
  ([app-state job-id {:keys [job-type retry-state]}]
   (let [job-type (or job-type :drinking-window)
         retry-state (merge {:failure-count 0
                             :delay-ms base-job-status-delay-ms}
                            retry-state)
         {:keys [failure-count delay-ms]} retry-state
         {:keys [in-progress-key]} (job-type-config job-type)
         schedule (fn [opts wait-ms]
                    (js/setTimeout (fn []
                                     (poll-job-status app-state job-id opts))
                                   wait-ms))]
     (tap> ["🔍 Polling job status for" job-id "job-type" job-type])
     (go
      (let [test-state @job-status-failure-test
            simulate? (and test-state (> (:remaining test-state) 0))
            result
            (if simulate?
              (let [updated-state (swap! job-status-failure-test
                                    (fn [{:keys [remaining] :as state}]
                                      (let [next (dec (or remaining 0))]
                                        (when (> next 0)
                                          (assoc state :remaining next)))))]
                (when-not updated-state (reset! job-status-failure-test nil))
                (tap> ["🧪 Simulating job status failure" test-state])
                {:success false :error (:error test-state)})
              (<! (GET (str "/api/admin/job-status/" job-id)
                       "Failed to get job status")))]
        (tap> ["📊 Job status result:" result])
        (if (:success result)
          (let [status (:data result)
                derived-job-type (let [value (:job-type status)]
                                   (cond (keyword? value) value
                                         (string? value) (keyword value)
                                         :else nil))
                job-type (or derived-job-type job-type)
                {:keys [in-progress-key]} (job-type-config job-type)
                job-status (:status status)
                progress (:progress status)
                total (:total status)]
            (tap> ["📈 Job status details:"
                   {:job-status job-status
                    :progress progress
                    :total total
                    :job-type job-type}])
            (swap! app-state assoc
              :job-progress
              {:progress (or progress 0)
               :total (or total 0)
               :status job-status
               :job-type job-type})
            (tap> ["🔄 Updated app-state with progress"])
            (cond (= job-status "completed")
                  (do (swap! app-state dissoc in-progress-key :job-progress)
                      (fetch-wines app-state)
                      (let [failed-wines (:failed-wines status)
                            message (format-success-message job-type
                                                            (or total 0)
                                                            failed-wines)]
                        (swap! app-state assoc :success message)))
                  (= job-status "failed")
                  (do (swap! app-state dissoc in-progress-key :job-progress)
                      (swap! app-state assoc
                        :error
                        (format-failure-message job-type status)))
                  (= job-status "running")
                  (schedule {:job-type job-type
                             :retry-state {:failure-count 0
                                           :delay-ms base-job-status-delay-ms}}
                            base-job-status-delay-ms)
                  :else (swap! app-state dissoc in-progress-key :job-progress)))
          (let [error-message (:error result)
                next-count (inc failure-count)
                existing-progress (:job-progress @app-state)
                processed (or (:progress existing-progress) 0)
                total (or (:total existing-progress) 0)]
            (tap> ["⚠️ Failed to get job status"
                   {:error error-message :attempt next-count}])
            (if (and (< next-count max-job-status-retries)
                     (retryable-job-status-error? error-message))
              (let [next-delay (-> (* 2 delay-ms)
                                   (max base-job-status-delay-ms)
                                   (min max-job-status-delay-ms))
                    retry-opts {:job-type job-type
                                :retry-state {:failure-count next-count
                                              :delay-ms next-delay}}
                    retry-progress {:job-type job-type
                                    :progress processed
                                    :total total
                                    :status "retrying"
                                    :retry-attempt next-count
                                    :retry-max max-job-status-retries
                                    :retry-delay next-delay}]
                (tap> ["⏳ Retrying job status poll"
                       {:attempt next-count
                        :max max-job-status-retries
                        :delay-ms next-delay
                        :error error-message}])
                (swap! app-state assoc :job-progress retry-progress)
                (schedule retry-opts next-delay))
              (do (swap! app-state dissoc in-progress-key :job-progress)
                  (swap! app-state assoc
                    :error
                    (str "Failed to check job status"
                         (when (> max-job-status-retries 0)
                           (str " after " max-job-status-retries " attempts"))
                         (when error-message
                           (str ": " error-message)))))))))))))

(defn regenerate-filtered-drinking-windows
  "Admin function to regenerate drinking windows for currently filtered wines"
  [app-state]
  (let [filtered-wines (filters/filtered-sorted-wines app-state)
        wine-ids (map :id filtered-wines)
        wine-count (count wine-ids)
        provider (get-in @app-state [:ai :provider])]
    (when (> wine-count 0)
      (swap! app-state assoc :regenerating-drinking-windows? true)
      (go (let [result (<! (POST "/api/admin/start-drinking-window-job"
                                 {:wine-ids wine-ids :provider provider}
                                 "Failed to start drinking window job"))]
            (if (:success result)
              (let [job-id (get-in result [:data :job-id])]
                (tap> ["🚀 Starting polling for job:" job-id])
                ;; Start polling for job status
                (poll-job-status app-state job-id))
              (do (swap! app-state dissoc :regenerating-drinking-windows?)
                  (swap! app-state assoc :error (:error result)))))))))

(defn regenerate-filtered-wine-summaries
  "Admin function to regenerate wine summaries for currently filtered wines"
  [app-state]
  (let [filtered-wines (filters/filtered-sorted-wines app-state)
        wine-ids (map :id filtered-wines)
        wine-count (count wine-ids)
        provider (get-in @app-state [:ai :provider])]
    (when (> wine-count 0)
      (swap! app-state assoc :regenerating-wine-summaries? true)
      (go (let [result (<! (POST "/api/admin/start-wine-summary-job"
                                 {:wine-ids wine-ids :provider provider}
                                 "Failed to start wine summary job"))]
            (if (:success result)
              (let [job-id (get-in result [:data :job-id])]
                (tap> ["🚀 Starting polling for wine summary job:" job-id])
                (poll-job-status app-state job-id {:job-type :wine-summary}))
              (do (swap! app-state dissoc :regenerating-wine-summaries?)
                  (swap! app-state assoc :error (:error result)))))))))

(defn reset-database
  "Admin function to reset the database"
  [app-state]
  (go (swap! app-state assoc :resetting-database? true)
      (if-let [result (<! (POST "/api/admin/reset-database"
                                {}
                                "Failed to reset database"))]
        (do (swap! app-state assoc :resetting-database? false)
            (if (:success result)
              (reset! app-state (assoc initial-app-state
                                       :success
                                       "Database reset successfully!"))
              (swap! app-state assoc :error (:error result))))
        (do (swap! app-state assoc :resetting-database? false)
            (swap! app-state assoc :error "Failed to reset database")))))

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
