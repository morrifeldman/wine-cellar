(ns wine-cellar.api
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! go chan put!]]
            [wine-cellar.config :as config]
            [wine-cellar.nav :as nav]
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
        ;; Replace, so the page that just failed doesn't sit in history
        ;; behind the login and come back on Back.
        (js/setTimeout #(.replace (.-location js/window)
                                  (str api-base-url "/auth/google"))
                       100)
        {:success false :error "Authentication required"})
    ;; Otherwise prefer the server's message. A network failure or a
    ;; proxy's
    ;; HTML error page has none, so fall back to the caller's.
    :else (let [body (:body response)]
            {:success false
             :error (or (when (map? body) (:error body)) error-msg)})))

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

(def ^:private http-methods
  {:get http/get :post http/post :put http/put :delete http/delete})

(defn request!
  "Runs one API call against app-state and returns a Promise of the response
   data, rejected with the error message. Options:
   - :method (default :get), :url, :body
   - :error-msg   shown when the server gives no message of its own
   - :loading     app-state path that is true while the call runs
   - :on-success  (fn [state data] new-state), applied with swap!
   - :after       (fn [data]) for follow-up effects, like a refetch
   - :error-path  where a failure's message goes. The default, [:error], is
                  the app-wide banner. Any other path belongs to one feature
                  and is cleared when a call succeeds.
   Callers needn't handle the promise's rejection: the failure is shown."
  [app-state
   {:keys [method url body error-msg loading on-success after error-path]
    :or {method :get error-path [:error]}}]
  (when loading (swap! app-state assoc-in loading true))
  (let [promise
        (js/Promise.
         (fn [resolve reject]
           (go
            (let [{:keys [success data error]}
                  (<! (api-request (http-methods method) url body error-msg))]
              (swap! app-state (fn [state]
                                 (cond-> state
                                   loading (assoc-in loading false)
                                   (and success on-success) (on-success data)
                                   (and success (not= error-path [:error]))
                                   (assoc-in error-path nil)
                                   (not success) (assoc-in error-path error))))
              (if success
                (do (when after (after data)) (resolve data))
                (reject error))))))]
    (.catch promise (fn [_]))
    promise))

;; Collection helpers for lists of maps with :id
(defn- replace-by-id
  [coll item]
  (mapv #(if (= (:id %) (:id item)) item %) coll))

(defn- remove-by-id [coll id] (filterv #(not= (:id %) id) coll))

(defn- prepend [coll item] (into [item] coll))

(defn- encode-query-params
  [params]
  (let [pairs (for [[k v] params
                    :when (some? v)]
                (str (name k) "=" (js/encodeURIComponent (str v))))]
    (when (seq pairs) (str "?" (string/join "&" pairs)))))

(defn fetch-report-list
  [app-state]
  (request! app-state
            {:url "/api/reports"
             :error-msg "Failed to fetch report list"
             :on-success #(assoc-in %1 [:report-nav :list] %2)}))

(defn fetch-report-by-id
  [app-state id]
  (request! app-state
            {:url (str "/api/reports/by-id/" id)
             :error-msg "Failed to fetch report"
             :loading [:loading-report?]
             :on-success #(assoc %1 :report %2)}))

(defn fetch-latest-report
  ([app-state] (fetch-latest-report app-state {}))
  ([app-state {:keys [force? provider]}]
   (request! app-state
             {:url (str "/api/reports/latest"
                        (encode-query-params {:force (when force? "true")
                                              :provider (some-> provider
                                                                name)}))
              :error-msg "Failed to fetch cellar report"
              :loading [:loading-report?]
              :on-success #(assoc %1 :report %2)})))

(defn logout
  []
  (js/console.log "Logging out...")
  (set! (.-href (.-location js/window)) (str api-base-url "/auth/logout")))

(defn fetch-model-info
  [app-state]
  (request! app-state
            {:url "/api/admin/model-info"
             :error-msg "Failed to fetch model info"
             :on-success
             (fn [state {:keys [models default-provider default-effort]}]
               ;; Defaults only fill in what the user hasn't chosen.
               (-> state
                   (assoc-in [:ai :models] models)
                   (update-in [:ai :provider]
                              #(or % (keyword default-provider)))
                   (update-in [:ai :effort] #(or % default-effort))))}))

;; Device admin endpoints

(defn fetch-devices
  [app-state]
  (request! app-state
            {:url "/api/admin/devices"
             :error-msg "Failed to fetch devices"
             :loading [:devices/loading?]
             :error-path [:devices/error]
             :on-success
             #(assoc %1 :devices/list %2 :devices/approve-error nil)}))

(defn- device-action!
  "An admin action on one device; its errors show beside the device list."
  [app-state method path error-msg & [body]]
  (request! app-state
            {:method method
             :url (str "/api/admin/devices/" path)
             :body body
             :error-msg error-msg
             :loading [:devices/action?]
             :error-path [:devices/approve-error]
             :after #(fetch-devices app-state)}))

(defn approve-device
  [app-state device-id claim-code]
  (device-action! app-state
                  :post (str device-id "/approve")
                  "Failed to approve device" {:claim_code claim-code}))

(defn block-device
  [app-state device-id]
  (device-action! app-state
                  :post (str device-id "/block")
                  "Failed to block device" {}))

(defn unblock-device
  [app-state device-id]
  (device-action! app-state
                  :post (str device-id "/unblock")
                  "Failed to unblock device" {}))

(defn delete-device
  [app-state device-id]
  (device-action! app-state
                  :delete
                  (str device-id "/delete")
                  "Failed to delete device"))

(defn update-device-sensor-config
  [app-state device-id sensor-config]
  (device-action! app-state
                  :put (str device-id "/sensor-config")
                  "Failed to update sensor config" sensor-config))

;; Classification endpoints

(defn fetch-classifications
  [app-state]
  (request! app-state
            {:url "/api/classifications"
             :error-msg "Failed to fetch classifications"
             :on-success #(assoc %1 :classifications %2)}))

(defn create-classification
  [app-state classification]
  (request! app-state
            {:method :post
             :url "/api/classifications"
             :body classification
             :error-msg "Failed to create classification"
             :on-success
             #(assoc %1 :creating-classification? false :new-classification nil)
             :after #(fetch-classifications app-state)}))

(defn update-classification
  [app-state id updates]
  (request! app-state
            {:method :put
             :url (str "/api/classifications/" id)
             :body updates
             :error-msg "Failed to update classification"
             :on-success #(assoc %1 :editing-classification nil)
             :after #(fetch-classifications app-state)}))

(defn delete-classification
  [app-state id]
  (request! app-state
            {:method :delete
             :url (str "/api/classifications/" id)
             :error-msg "Failed to delete classification"
             :on-success #(dissoc %1 :deleting-classification)
             :after #(fetch-classifications app-state)}))

;; Sensor reading endpoints

(defn fetch-latest-sensor-readings
  [app-state {:keys [device-id]}]
  (request! app-state
            {:url (str "/api/sensor-readings/latest"
                       (encode-query-params {:device_id device-id}))
             :error-msg "Failed to fetch latest sensor readings"
             :loading [:sensor-readings :loading-latest?]
             :on-success #(assoc-in %1 [:sensor-readings :latest] %2)}))

(defn fetch-sensor-series
  [app-state {:keys [device-id bucket from to]}]
  (request! app-state
            {:url (str
                   "/api/sensor-readings/series"
                   (encode-query-params
                    {:device_id device-id :bucket bucket :from from :to to}))
             :error-msg "Failed to fetch sensor series"
             :loading [:sensor-readings :loading-series?]
             :on-success #(assoc-in %1 [:sensor-readings :series] %2)}))

;; Wine endpoints
(defn fetch-wines
  ([app-state] (fetch-wines app-state {}))
  ([app-state {:keys [background?]}]
   (request!
    app-state
    {:url "/api/wines/list"
     :error-msg "Failed to fetch wines"
     :loading (when-not background? [:loading?])
     :on-success
     (fn [state wines]
       ;; Fresh values win; fields loaded elsewhere (images, detail-only
       ;; data) are kept.
       (let [existing-by-id (into {} (map (juxt :id identity)) (:wines state))]
         (assoc state
                :wines
                (mapv #(merge (get existing-by-id (:id %)) %) wines))))})))

(defn- merge-wine
  "Merges `wine` into its entry in :wines, adding it if absent."
  [state wine]
  (update state
          :wines
          (fn [wines]
            (if (some #(= (:id %) (:id wine)) wines)
              (mapv #(if (= (:id %) (:id wine)) (merge % wine) %) wines)
              (conj (vec wines) wine)))))

(defn fetch-wine-details
  [app-state wine-id & {:keys [include-images] :or {include-images true}}]
  (request! app-state
            {:url (str "/api/wines/by-id/"
                       wine-id
                       (when include-images "?include_images=true"))
             :error-msg "Failed to fetch wine details"
             :on-success #(-> %1
                              (merge-wine %2)
                              (assoc :selected-wine-id wine-id))}))

(defn create-wine
  [app-state wine]
  (request! app-state
            {:method :post
             :url "/api/wines"
             :body wine
             :error-msg "Failed to create wine"
             :loading [:submitting-wine?]
             :on-success #(assoc %1 :new-wine {} :window-reason nil)
             :after (fn [_]
                      (nav/replace-wines!)
                      (fetch-wines app-state)
                      (fetch-classifications app-state))}))

(defn delete-wine
  [app-state id]
  (request! app-state
            {:method :delete
             :url (str "/api/wines/by-id/" id)
             :error-msg "Failed to delete wine"
             :on-success #(update %1 :wines remove-by-id id)}))

;; Tasting Notes endpoints
(defn fetch-tasting-notes
  [app-state wine-id]
  (request! app-state
            {:url (str "/api/wines/by-id/" wine-id "/tasting-notes")
             :error-msg "Failed to fetch tasting notes"
             :on-success #(assoc %1 :tasting-notes %2)}))


(defn create-tasting-note
  [app-state wine-id note notes-ref]
  (request!
   app-state
   {:method :post
    :url (str "/api/wines/by-id/" wine-id "/tasting-notes")
    :body note
    :error-msg "Failed to create tasting note"
    :loading [:submitting-note?]
    ;; The note is saved, so there is no longer any in-progress
    ;; work for a navigation to ask about.
    :on-success #(-> %1
                     (update :tasting-notes (fnil conj []) %2)
                     (assoc :new-tasting-note {} :tasting-note-baseline nil))
    :after
    (fn [_] (when (and notes-ref @notes-ref) (set! (.-value @notes-ref) "")))}))

(defn update-tasting-note
  [app-state wine-id note-id note]
  (request! app-state
            {:method :put
             :url (str "/api/wines/by-id/" wine-id "/tasting-notes/" note-id)
             :body note
             :error-msg "Failed to update tasting note"
             :loading [:submitting-note?]
             :on-success #(-> %1
                              (update :tasting-notes replace-by-id %2)
                              (assoc :editing-note-id nil
                                     :new-tasting-note {}
                                     :tasting-note-baseline nil))}))

(defn delete-tasting-note
  [app-state wine-id note-id]
  (request! app-state
            {:method :delete
             :url (str "/api/wines/by-id/" wine-id "/tasting-notes/" note-id)
             :error-msg "Failed to delete tasting note"
             :on-success #(update %1 :tasting-notes remove-by-id note-id)}))

(defn fetch-tasting-note-sources
  [app-state]
  (request! app-state
            {:url "/api/tasting-note-sources"
             :error-msg "Failed to fetch tasting note sources"
             :on-success #(assoc %1 :tasting-note-sources %2)}))

;; Blind Tasting endpoints
(defn fetch-blind-tastings
  [app-state]
  (request! app-state
            {:url "/api/blind-tastings"
             :error-msg "Failed to fetch blind tastings"
             :loading [:blind-tastings :loading?]
             :on-success #(assoc-in %1 [:blind-tastings :list] %2)}))

(defn create-blind-tasting
  [app-state note]
  (request! app-state
            {:method :post
             :url "/api/blind-tastings"
             :body note
             :error-msg "Failed to create blind tasting"
             :loading [:blind-tastings :submitting?]
             :on-success
             #(update %1 :blind-tastings assoc :form {} :show-form? false)
             :after #(fetch-blind-tastings app-state)}))

(defn link-blind-tasting
  [app-state note-id wine-id]
  (swap! app-state assoc-in [:blind-tastings :linking-note-id] note-id)
  (-> (request! app-state
                {:method :put
                 :url (str "/api/blind-tastings/" note-id "/link")
                 :body {:wine_id wine-id}
                 :error-msg "Failed to link blind tasting"
                 :on-success
                 #(assoc-in %1 [:blind-tastings :show-link-dialog?] false)
                 :after #(fetch-blind-tastings app-state)})
      (.finally
       #(swap! app-state assoc-in [:blind-tastings :linking-note-id] nil))
      ;; The failure is already on the banner.
      (.catch (fn [_]))))

(defn fetch-inventory-history
  [app-state wine-id]
  (request! app-state
            {:url (str "/api/wines/by-id/" wine-id "/history")
             :error-msg "Failed to fetch inventory history"
             :on-success #(assoc-in %1 [:inventory-history wine-id] %2)}))

(defn- refresh-wine-stock!
  "After a stock change, reload the wine's history and its quantities."
  [app-state wine-id]
  (fetch-inventory-history app-state wine-id)
  (fetch-wine-details app-state wine-id :include-images false))

(defn update-inventory-history
  [app-state wine-id history-id updates]
  (request! app-state
            {:method :put
             :url (str "/api/wines/history/" history-id)
             :body updates
             :error-msg "Failed to update history record"
             :after #(refresh-wine-stock! app-state wine-id)}))

(defn delete-inventory-history
  [app-state wine-id history-id]
  (request! app-state
            {:method :delete
             :url (str "/api/wines/history/" history-id)
             :error-msg "Failed to delete history record"
             :after #(refresh-wine-stock! app-state wine-id)}))

(defn- stock-change!
  "Posts a stock change for the wine and merges the stock columns the server
   returns."
  [app-state wine-id path body error-msg]
  (request! app-state
            {:method :post
             :url (str "/api/wines/by-id/" wine-id path)
             :body body
             :error-msg error-msg
             :on-success #(merge-wine %1 %2)
             :after #(fetch-inventory-history app-state wine-id)}))

(defn adjust-wine-quantity
  ([app-state wine-id adjustment]
   (adjust-wine-quantity app-state wine-id adjustment {}))
  ([app-state wine-id adjustment {:keys [reason notes occurred_at]}]
   (stock-change! app-state
                  wine-id
                  "/adjust-quantity"
                  (cond-> {:adjustment adjustment}
                    reason (assoc :reason reason)
                    notes (assoc :notes notes)
                    occurred_at (assoc :occurred_at occurred_at))
                  "Failed to update wine quantity")))

(defn coravin-pour
  ([app-state wine-id oz] (coravin-pour app-state wine-id oz {}))
  ([app-state wine-id oz {:keys [notes]}]
   (stock-change! app-state
                  wine-id
                  "/coravin-pour"
                  (cond-> {:oz oz} notes (assoc :notes notes))
                  "Failed to record Coravin pour")))

(defn finish-open-bottle
  ([app-state wine-id] (finish-open-bottle app-state wine-id {}))
  ([app-state wine-id {:keys [notes]}]
   (stock-change! app-state
                  wine-id
                  "/finish-open-bottle"
                  (cond-> {} notes (assoc :notes notes))
                  "Failed to finish open bottle")))

(defn update-wine
  [app-state id updates]
  (request! app-state
            {:method :put
             :url (str "/api/wines/by-id/" id)
             :body updates
             :error-msg "Failed to update wine"
             :on-success #(merge-wine %1 %2)}))

(defn update-wine-image
  [app-state wine-id image-data]
  (request! app-state
            {:method :put
             :url (str "/api/wines/by-id/" wine-id "/image")
             :body image-data
             :error-msg "Failed to update wine image"
             :on-success #(merge-wine %1 %2)}))

(defn- ai-request!
  "Posts `payload` plus the chosen AI provider; `loading` names the flag the
   page shows while the model thinks."
  [app-state url payload loading error-msg]
  (request! app-state
            {:method :post
             :url url
             :body (assoc payload :provider (get-in @app-state [:ai :provider]))
             :error-msg error-msg
             :loading (when loading [loading])}))

(defn analyze-wine-label
  [app-state image-data]
  (ai-request! app-state
               "/api/wines/analyze-label" image-data
               :analyzing-label? "Failed to analyze wine label"))

(defn analyze-spirit-label
  [app-state label-image]
  (ai-request! app-state
               "/api/spirits/analyze-label" {:label_image label-image}
               :analyzing-spirit-label? "Failed to analyze spirit label"))

(defn suggest-drinking-window
  [app-state wine]
  (ai-request! app-state
               "/api/wines/suggest-drinking-window" {:wine wine}
               :suggesting-drinking-window?
               "Failed to suggest drinking window"))

(defn generate-wine-summary
  [app-state wine]
  (ai-request! app-state
               "/api/wines/generate-summary"
               {:wine wine}
               nil
               "Failed to generate wine summary"))

(defn fetch-grape-varieties
  [app-state]
  (request! app-state
            {:url "/api/grape-varieties"
             :error-msg "Failed to fetch grape varieties"
             :on-success #(assoc %1 :grape-varieties %2)}))

(defn create-grape-variety
  [app-state variety]
  (request! app-state
            {:method :post
             :url "/api/grape-varieties"
             :body variety
             :error-msg "Failed to create grape variety"
             :loading [:submitting-variety?]
             :on-success
             #(assoc %1 :new-grape-variety {} :show-variety-form? false)
             :after #(fetch-grape-varieties app-state)}))

(defn update-grape-variety
  [app-state id updates]
  (request! app-state
            {:method :put
             :url (str "/api/grape-varieties/" id)
             :body updates
             :error-msg "Failed to update grape variety"
             :loading [:submitting-variety?]
             :on-success
             #(assoc %1 :editing-variety-id nil :show-variety-form? false)
             :after #(fetch-grape-varieties app-state)}))

(defn delete-grape-variety
  [app-state id]
  (request! app-state
            {:method :delete
             :url (str "/api/grape-varieties/" id)
             :error-msg "Failed to delete grape variety"
             :on-success #(update %1 :grape-varieties remove-by-id id)}))

;; Wine Varieties endpoints
(defn fetch-wine-varieties
  [app-state wine-id]
  (request! app-state
            {:url (str "/api/wines/by-id/" wine-id "/varieties")
             :error-msg "Failed to fetch wine varieties"
             :on-success #(assoc %1 :wine-varieties %2)}))

(defn- wine-variety-change!
  [app-state wine-id opts]
  (request! app-state
            (merge {:loading [:submitting-wine-variety?]
                    :on-success #(assoc %1
                                        :new-wine-variety {}
                                        :editing-wine-variety-id nil
                                        :show-wine-variety-form? false)
                    :after #(fetch-wine-varieties app-state wine-id)}
                   opts)))

(defn add-variety-to-wine
  [app-state wine-id variety]
  (wine-variety-change! app-state
                        wine-id
                        {:method :post
                         :url (str "/api/wines/by-id/" wine-id "/varieties")
                         :body variety
                         :error-msg "Failed to add variety to wine"}))

(defn update-wine-variety-percentage
  [app-state wine-id variety-id percentage]
  (wine-variety-change! app-state
                        wine-id
                        {:method :put
                         :url (str "/api/wines/by-id/" wine-id
                                   "/varieties/" variety-id)
                         :body {:percentage percentage}
                         :error-msg "Failed to update variety percentage"}))

(defn remove-variety-from-wine
  [app-state wine-id variety-id]
  (request! app-state
            {:method :delete
             :url (str "/api/wines/by-id/" wine-id "/varieties/" variety-id)
             :error-msg "Failed to remove variety from wine"
             :on-success (fn [state _]
                           (update state
                                   :wine-varieties
                                   (fn [vs]
                                     (filterv #(not= (:variety_id %) variety-id)
                                              vs))))}))

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

(defn- chat-error!
  "Handle a failed chat API result: tap, set :chat :error, optional callback."
  ([app-state tap-label result] (chat-error! app-state tap-label result nil))
  ([app-state tap-label result callback]
   (tap> [tap-label (:error result)])
   (swap! app-state assoc-in [:chat :error] (:error result))
   (when callback (callback {:success false :error (:error result)}))))

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
   (let [{:keys [force? search-text] :or {force? false}} opts
         chat-state (:chat @app-state)
         loading? (:conversation-loading? chat-state)
         loaded? (:conversations-loaded? chat-state)
         chat-type (if (= :bar (:view @app-state)) "bar" "wine")
         query (encode-query-params (cond-> {:chat_type chat-type}
                                      search-text (assoc :search-text
                                                         search-text)))]
     (when (and (not loading?) (or force? (not loaded?) search-text))
       (swap! app-state (fn [state]
                          (-> state
                              (assoc-in [:chat :conversation-loading?] true)
                              (assoc-in [:chat :conversations-loaded?] false))))
       (go
        (let [result (<! (GET (str "/api/conversations" query)
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
            (do
              (swap! app-state (fn [state]
                                 (-> state
                                     (assoc-in [:chat :conversation-loading?]
                                               false)
                                     (assoc-in [:chat :conversations-loaded?]
                                               true))))
              (chat-error! app-state "conversations-load-error" result)))))))))

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
        (chat-error! app-state "conversation-create-error" result callback))))))

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
             (chat-error! app-state
                          "conversation-rename-error"
                          result
                          callback)))))))

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
            (chat-error! app-state "conversation-pin-error" result))))))

(defn update-conversation-provider!
  [app-state conversation-id provider]
  (when (and conversation-id provider)
    (go (let [result (<! (PUT (str "/api/conversations/" conversation-id)
                              {:provider provider}
                              "Failed to update conversation"))]
          (if (:success result)
            (swap! app-state apply-conversation-update! (:data result))
            (swap! app-state assoc-in [:chat :error] (:error result)))))))

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
                         (assoc-in [:chat :messages-loading?] false)
                         (update :chat dissoc :held-list-ids))
                     base-state))))
             (tap> ["conversation-deleted" conversation-id])
             (load-conversations! app-state {:force? true}))
         (chat-error! app-state "conversation-delete-error" result))))))

(defn fork-conversation!
  "Copy the first `message-count` messages of a conversation into a new one and
   make it the active conversation. Calls back with the new messages."
  [app-state conversation-id message-count callback]
  (go (let [result (<! (POST (str "/api/conversations/" conversation-id "/fork")
                             {:message_count message-count}
                             "Failed to fork conversation"))]
        (if (:success result)
          (let [{:keys [conversation messages]} (:data result)]
            (swap! app-state
              (fn [state]
                (-> state
                    (update-in [:chat :conversations]
                               #(upsert-conversation (or % []) conversation))
                    (assoc-in [:chat :active-conversation] conversation)
                    (assoc-in [:chat :active-conversation-id]
                              (:id conversation))
                    (assoc-in [:chat :error] nil))))
            (callback messages))
          (chat-error! app-state "conversation-fork-error" result)))))

(defn append-conversation-message!
  ([app-state conversation-id message]
   (append-conversation-message! app-state conversation-id message nil))
  ([app-state conversation-id message callback]
   (let [payload (cond-> message
                   (nil? (:image_data message)) (dissoc :image_data)
                   (nil? (:tokens_used message)) (dissoc :tokens_used)
                   (nil? (:context_note message)) (dissoc :context_note))]
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
          (chat-error! app-state
                       "conversation-message-save-error"
                       result
                       callback)))))))

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
           (contains? message :context_note) (assoc :context_note
                                                    (:context_note message))
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
          (chat-error! app-state
                       "conversation-message-update-error"
                       result
                       callback)))))))

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
                    :context-note (:context_note m)
                    :timestamp (some-> (:created_at m)
                                       js/Date.parse
                                       js/Date.)})
                 messages))
         (swap! app-state assoc-in [:chat :error] nil))
       (chat-error! app-state "conversation-messages-load-error" result)))))

(defn send-chat-message
  "Send the conversation history to the AI chat endpoint. The wines under
   discussion travel as context notes on the history's user messages.
   Provider is read from app-state. Optionally includes image data.
   Returns a zero-arity function that can be called to cancel the request."
  [app-state conversation-history image callback]
  (let
    [provider (get-in @app-state [:ai :provider])
     effort (get-in @app-state [:ai :effort])
     include-bar? (= :bar (get @app-state :view))
     payload (cond-> {:conversation-history conversation-history
                      :include-bar? include-bar?
                      :provider provider}
               (and effort (= :anthropic provider)) (assoc :effort effort)
               image (assoc :image image))
     fallback-msg
     "Sorry, I'm having trouble connecting right now. Please try again later."]
    (if @headless-mode?
      (do (js/console.log "API CALL INTERCEPTED (headless mode): POST /api/chat"
                          (clj->js payload))
          (go (callback "This is a mock response in headless mode."))
          (fn [] (js/console.log "Mock request cancelled")))
      (let [request-opts (merge default-opts {:json-params payload})
            request-ch (http/post (str api-base-url "/api/chat") request-opts)]
        (go (let [response (<! request-ch)]
              (when response ;; If response is nil, the channel was closed
                             ;; (cancelled)
                (let [result (handle-api-response
                              response
                              "Failed to send chat message")]
                  (if (:success result)
                    (callback (:data result))
                    (callback (if-let [error (:error result)]
                                (str "Sorry, that didn't work: " error)
                                fallback-msg)))))))
        ;; Return a cancel function that closes the request channel
        (fn []
          (js/console.log "Cancelling chat request...")
          (cljs.core.async/close! request-ch))))))

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
                        (if (seq failed-wines)
                          (swap! app-state assoc :error message)
                          (swap! app-state assoc :success message))))
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

(defn execute-sql
  "Execute a raw SQL query. Returns a channel."
  [query]
  (POST "/api/admin/sql" {:query query} "Failed to execute SQL query"))

(defn fetch-db-schema
  "Fetch the database schema (tables and views). Returns a channel."
  []
  (GET "/api/admin/schema" "Failed to fetch database schema"))

(defn load-wine-detail-page
  "Load all data needed for the wine detail page"
  [app-state wine-id]
  (when-not (:saved-list-scroll-pos @app-state)
    (swap! app-state assoc :saved-list-scroll-pos (.-scrollY js/window)))
  (.scrollTo js/window 0 0)
  (swap! app-state assoc :selected-wine-id wine-id)
  (swap! app-state assoc :new-tasting-note {})
  (fetch-tasting-notes app-state wine-id)
  (fetch-wine-details app-state wine-id)
  (fetch-wine-varieties app-state wine-id)
  (fetch-tasting-note-sources app-state)
  (fetch-inventory-history app-state wine-id))

(defn exit-wine-detail-page
  [app-state]
  (swap! app-state dissoc
    :selected-wine-id :tasting-notes
    :editing-note-id :window-suggestion
    :new-tasting-note :wine-varieties
    :zoomed-image :inventory-history)
  (fetch-wines app-state {:background? true}))

;; Bar API
(defn fetch-bar-data
  [app-state]
  (doseq [[path url what] [[:spirits "/api/spirits" "spirits"]
                           [:inventory-items "/api/bar-inventory"
                            "bar inventory"]
                           [:recipes "/api/cocktail-recipes" "recipes"]]]
    (request! app-state
              {:url url
               :error-msg (str "Failed to fetch " what)
               :on-success #(assoc-in %1 [:bar path] %2)})))

(defn- bar-change!
  "A request that adds, replaces or removes (`op`) one item in the [:bar coll]
   list."
  [app-state coll op {:keys [id] :as opts}]
  (request! app-state
            (merge {:on-success (fn [state item]
                                  (update-in state
                                             [:bar coll]
                                             (case op
                                               ;; newest first, like the
                                               ;; server's order
                                               :add #(prepend % item)
                                               :replace #(replace-by-id % item)
                                               :remove #(remove-by-id % id))))}
                   (dissoc opts :id))))

(defn create-spirit
  [app-state spirit]
  (bar-change! app-state
               :spirits
               :add
               {:method :post
                :url "/api/spirits"
                :body spirit
                :error-msg "Failed to create spirit"}))

(defn update-spirit
  [app-state id updates]
  (bar-change! app-state
               :spirits
               :replace
               {:method :put
                :url (str "/api/spirits/" id)
                :body updates
                :error-msg "Failed to update spirit"}))

(defn delete-spirit
  [app-state id]
  (bar-change! app-state
               :spirits
               :remove
               {:method :delete
                :id id
                :url (str "/api/spirits/" id)
                :error-msg "Failed to delete spirit"}))

(defn toggle-bar-inventory-item
  [app-state id have-it?]
  (bar-change! app-state
               :inventory-items
               :replace
               {:method :put
                :url (str "/api/bar-inventory/" id)
                :body {:have_it have-it?}
                :error-msg "Failed to update inventory item"}))

(defn create-bar-inventory-item
  [app-state item]
  (request! app-state
            {:method :post
             :url "/api/bar-inventory"
             :body item
             :error-msg "Failed to create inventory item"
             ;; inventory is listed by category, so the new item goes last
             :on-success #(update-in %1 [:bar :inventory-items] conj %2)}))

(defn update-bar-inventory-item
  [app-state id fields]
  (bar-change! app-state
               :inventory-items
               :replace
               {:method :put
                :url (str "/api/bar-inventory/" id)
                :body fields
                :error-msg "Failed to update inventory item"}))

(defn delete-bar-inventory-item
  [app-state id]
  (bar-change! app-state
               :inventory-items
               :remove
               {:method :delete
                :id id
                :url (str "/api/bar-inventory/" id)
                :error-msg "Failed to delete inventory item"}))

(defn create-cocktail-recipe
  ([app-state recipe] (create-cocktail-recipe app-state recipe nil))
  ([app-state recipe {:keys [open?]}]
   (-> (bar-change! app-state
                    :recipes
                    :add
                    {:method :post
                     :url "/api/cocktail-recipes"
                     :body recipe
                     :error-msg "Failed to create recipe"
                     ;; The URL owns which recipe is open, so opening the
                     ;; new one is a navigation; it scrolls itself into
                     ;; view.
                     :after #(when open? (nav/go-bar-recipe! (:id %)))})
       (.then (fn [_]
                (swap! app-state update
                  :bar assoc
                  :show-recipe-form? false
                  :new-recipe {:ingredients []}))
              (fn [_])))))

(defn update-cocktail-recipe
  [app-state id recipe]
  (bar-change! app-state
               :recipes
               :replace
               {:method :put
                :url (str "/api/cocktail-recipes/" id)
                :body recipe
                :error-msg "Failed to update recipe"
                :after
                #(swap! app-state assoc-in [:bar :editing-recipe-id] nil)}))

(defn reextract-recipe-timers
  [app-state]
  (swap! app-state assoc :reextracting-recipe-timers? true)
  (go (let [result (<! (POST "/api/admin/reextract-recipe-timers"
                             {}
                             "Failed to re-read recipe timers"))]
        (swap! app-state dissoc :reextracting-recipe-timers?)
        (if (:success result)
          (let [{:keys [recipes-updated recipes-failed]} (:data result)]
            (fetch-bar-data app-state)
            (swap! app-state assoc
              :success
              (str "Re-read timers for " recipes-updated
                   " recipes" (when (pos? recipes-failed)
                                (str "; " recipes-failed " failed")))))
          (swap! app-state assoc :error (:error result))))))

(defn refresh-recipe-links
  "Re-resolves one recipe's spirit/ingredient links against current inventory.
   Returns a JS Promise resolving to the updated recipe."
  [app-state id]
  (bar-change! app-state
               :recipes
               :replace
               {:method :post
                :url (str "/api/cocktail-recipes/" id "/refresh-links")
                :body {}
                :error-msg "Failed to refresh recipe links"}))

(defn refresh-all-recipe-links
  "Sequentially refreshes every recipe's links, updating
   [:bar :refresh-progress] as it goes (:current holds the name of the recipe
   being resolved). Stops early when :stop? is set; a single recipe's failure
   is skipped so the batch continues."
  [app-state]
  (let [entries (mapv (juxt :id :name) (get-in @app-state [:bar :recipes]))]
    (swap! app-state assoc-in
      [:bar :refresh-progress]
      {:running? true :done 0 :total (count entries) :stop? false})
    (go
     (loop [[[id recipe-name] & more] entries]
       (if (or (nil? id) (get-in @app-state [:bar :refresh-progress :stop?]))
         (swap! app-state update-in
           [:bar :refresh-progress]
           assoc
           :running? false
           :current nil)
         (do (swap! app-state assoc-in
               [:bar :refresh-progress :current]
               recipe-name)
             (let [result
                   (<! (POST (str "/api/cocktail-recipes/" id "/refresh-links")
                             {}
                             "Failed to refresh recipe links"))]
               (when (:success result)
                 (swap! app-state update-in
                   [:bar :recipes]
                   (fn [rs] (mapv #(if (= (:id %) id) (:data result) %) rs))))
               (swap! app-state update-in [:bar :refresh-progress :done] inc)
               (recur more))))))))

(defn delete-cocktail-recipe
  [app-state id]
  (bar-change! app-state
               :recipes
               :remove
               {:method :delete
                :id id
                :url (str "/api/cocktail-recipes/" id)
                :error-msg "Failed to delete recipe"}))

(defn extract-recipe-from-message!
  [app-state message-id message-text]
  (swap! app-state assoc-in
    [:chat :save-recipe]
    {:extracting? true :message-id message-id :recipe nil :open? false})
  (go
   (let [result (<! (POST "/api/cocktail-recipe-extract"
                          {:message-text message-text}
                          "Failed to extract recipe"))]
     (if (:success result)
       (let [data (:data result)
             recipes (or (:recipes data) (when (:name data) [data]))]
         (swap! app-state assoc-in
           [:chat :save-recipe]
           {:extracting? false
            :message-id message-id
            :recipes (vec recipes)
            :open? true}))
       (swap! app-state (fn [s]
                          (-> s
                              (assoc-in [:chat :save-recipe :extracting?] false)
                              (assoc-in [:bar :error] (:error result)))))))))

(defn extract-recipe-from-image!
  [app-state image-data]
  (swap! app-state (fn [s]
                     (-> s
                         (assoc-in [:bar :photo-import :extracting?] true)
                         (assoc-in [:chat :save-recipe]
                                   {:extracting? true
                                    :recipe nil
                                    :open? false
                                    :origin :photo}))))
  (go
   (let [result (<! (POST "/api/cocktail-recipe-extract"
                          {:image image-data}
                          "Failed to extract recipe"))]
     (if (:success result)
       (let [data (:data result)
             recipes (or (:recipes data) (when (:name data) [data]))]
         (swap! app-state (fn [s]
                            (-> s
                                (assoc-in [:bar :photo-import]
                                          {:open? false :extracting? false})
                                (assoc-in [:chat :save-recipe]
                                          {:extracting? false
                                           :recipes (vec recipes)
                                           :open? true
                                           :origin :photo})))))
       (swap! app-state (fn [s]
                          (-> s
                              (assoc-in [:bar :photo-import :extracting?] false)
                              (assoc-in [:bar :error] (:error result)))))))))