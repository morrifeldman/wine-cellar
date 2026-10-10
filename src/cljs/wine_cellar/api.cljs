(ns wine-cellar.api
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! go chan put!]]
            [wine-cellar.common :as common]
            [wine-cellar.config :as config]
            [wine-cellar.nav :as nav]
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
            {:url
             (str "/api/sensor-readings/series"
                  (encode-query-params
                   {:device_id device-id
                    :bucket bucket
                    :from from
                    :to to
                    ;; Buckets follow this browser's days and hours.
                    :tz (.. js/Intl DateTimeFormat resolvedOptions -timeZone)}))
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

(defn- apply-conversation-update!
  "Puts a conversation from the server into the list, and when it is the open
   one makes it active and adopts its provider."
  ([state conversation]
   (apply-conversation-update! state
                               conversation
                               (= (get-in state [:chat :active-conversation-id])
                                  (:id conversation))))
  ([state conversation make-active?]
   (cond-> (update-in state
                      [:chat :conversations]
                      #(upsert-conversation (or % []) conversation))
     make-active? (-> (update :chat assoc
                              :active-conversation conversation
                              :active-conversation-id (:id conversation))
                      (cond-> (:provider conversation)
                              (assoc-in [:ai :provider]
                               (keyword (:provider conversation))))))))

(defn- chat-request!
  "A conversation request; failures show in the chat dialog."
  [app-state opts]
  (request! app-state (assoc opts :error-path [:chat :error])))

(defn- conversation-url [id & more] (apply str "/api/conversations/" id more))

(defn- busy-with!
  "Marks [:chat busy-key] with the conversation id while `promise` runs, so
   the sidebar can show a spinner on that one row."
  [app-state busy-key conversation-id promise]
  (swap! app-state assoc-in [:chat busy-key] conversation-id)
  (-> promise
      (.finally #(swap! app-state assoc-in [:chat busy-key] nil))
      (.catch (fn [_])))
  promise)

(defn load-conversations!
  "Fetch conversations for the authenticated user and store them in app state."
  ([app-state] (load-conversations! app-state {}))
  ([app-state {:keys [force? search-text]}]
   (let [{:keys [conversation-loading? conversations-loaded?]} (:chat
                                                                @app-state)
         chat-type (if (= :bar (:view @app-state)) "bar" "wine")]
     (when (and (not conversation-loading?)
                (or force? (not conversations-loaded?) search-text))
       (swap! app-state assoc-in [:chat :conversations-loaded?] false)
       (-> (chat-request!
            app-state
            {:url (str "/api/conversations"
                       (encode-query-params {:chat_type chat-type
                                             :search-text search-text}))
             :error-msg "Failed to load conversations"
             :loading [:chat :conversation-loading?]
             :on-success
             (fn [state conversations]
               (let [sorted (sort-conversations conversations)
                     active-id (get-in state [:chat :active-conversation-id])
                     active (some #(when (= (:id %) active-id) %) sorted)]
                 (cond-> (update state
                                 :chat assoc
                                 :conversations sorted
                                 :active-conversation active)
                   (:provider active) (assoc-in [:ai :provider]
                                       (keyword (:provider active))))))})
           (.finally
            #(swap! app-state assoc-in [:chat :conversations-loaded?] true))
           (.catch (fn [_])))))))

(defn create-conversation!
  "Creates a conversation and makes it the open one. Promise of the
   conversation."
  [app-state payload]
  (chat-request! app-state
                 {:method :post
                  :url "/api/conversations"
                  :body payload
                  :error-msg "Failed to create conversation"
                  :on-success #(-> %1
                                   (apply-conversation-update! %2 true)
                                   (assoc-in [:chat :conversations-loaded?]
                                             true))}))

(defn- update-conversation!
  [app-state conversation-id changes busy-key]
  (when conversation-id
    (cond->> (chat-request! app-state
                            {:method :put
                             :url (conversation-url conversation-id)
                             :body changes
                             :error-msg "Failed to update conversation"
                             :on-success apply-conversation-update!})
      busy-key (busy-with! app-state busy-key conversation-id))))

(defn rename-conversation!
  [app-state conversation-id title]
  (update-conversation! app-state
                        conversation-id
                        {:title title}
                        :renaming-conversation-id))

(defn set-conversation-pinned!
  [app-state conversation-id pinned?]
  (some-> (update-conversation! app-state
                                conversation-id
                                {:pinned pinned?}
                                :pinning-conversation-id)
          ;; pinning reorders the list
          (.then #(load-conversations! app-state {:force? true}) (fn [_]))))

(defn update-conversation-provider!
  [app-state conversation-id provider]
  (when provider
    (update-conversation! app-state conversation-id {:provider provider} nil)))

(defn- remove-conversation
  "State without the conversation; closes it if it was open."
  [state conversation-id]
  (let [open? (= conversation-id
                 (get-in state [:chat :active-conversation-id]))]
    (cond->
      (update-in state [:chat :conversations] remove-by-id conversation-id)
      open? (update :chat
                    #(-> %
                         (assoc :active-conversation-id nil
                                :active-conversation nil
                                :messages []
                                :messages-loading? false)
                         (dissoc :held-list-ids))))))

(defn delete-conversation!
  [app-state conversation-id]
  (when conversation-id
    (busy-with! app-state
                :deleting-conversation-id
                conversation-id
                (chat-request!
                 app-state
                 {:method :delete
                  :url (conversation-url conversation-id)
                  :error-msg "Failed to delete conversation"
                  :on-success (fn [state _]
                                (remove-conversation state conversation-id))
                  :after #(load-conversations! app-state {:force? true})}))))

(defn fork-conversation!
  "Copy the first `message-count` messages of a conversation into a new one and
   make it the active conversation. Promise of the new messages."
  [app-state conversation-id message-count]
  (-> (chat-request! app-state
                     {:method :post
                      :url (conversation-url conversation-id "/fork")
                      :body {:message_count message-count}
                      :error-msg "Failed to fork conversation"
                      :on-success
                      #(apply-conversation-update! %1 (:conversation %2) true)})
      (.then #(:messages %))))

(defn append-conversation-message!
  "Saves a message. Promise of the saved message."
  [app-state conversation-id message]
  (-> (chat-request! app-state
                     {:method :post
                      :url (conversation-url conversation-id "/messages")
                      :body (into {} (remove (comp nil? val)) message)
                      :error-msg "Failed to save conversation message"
                      :on-success (fn [state {:keys [conversation]}]
                                    (cond-> state
                                      conversation (apply-conversation-update!
                                                    conversation)))})
      (.then #(:message %))))

(defn update-conversation-message!
  "Edits a saved message; with :truncate_after? true, also drops every message
   after it. Promise of the server's {:message :conversation
   :deleted-message-ids}."
  [app-state conversation-id message-id message]
  (chat-request!
   app-state
   {:method :put
    :url (conversation-url conversation-id "/messages/" message-id)
    :body (cond-> {:content (:content message)}
            (contains? message :image_data) (assoc :image (:image_data message))
            (contains? message :tokens_used) (assoc :tokens_used
                                                    (:tokens_used message))
            (contains? message :context_note) (assoc :context_note
                                                     (:context_note message))
            (true? (:truncate_after? message)) (assoc :truncate_after? true))
    :error-msg "Failed to update conversation message"
    :on-success (fn [state {:keys [conversation]}]
                  (cond-> state
                    conversation (apply-conversation-update! conversation)))}))

(defn fetch-conversation-messages!
  [app-state conversation-id]
  (chat-request! app-state
                 {:url (conversation-url conversation-id "/messages")
                  :error-msg "Failed to load conversation messages"
                  :loading [:chat :messages-loading?]
                  :on-success (fn [state messages]
                                (assoc-in state
                                 [:chat :messages]
                                 (mapv (fn [m]
                                         {:id (:id m)
                                          :text (:content m)
                                          :is-user (:is_user m)
                                          :context-note (:context_note m)
                                          :timestamp (some-> (:created_at m)
                                                             js/Date.parse
                                                             js/Date.)})
                                       messages)))}))

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
               (and effort (common/provider-supports? provider :effort))
               (assoc :effort effort)
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
  (request! app-state
            {:method :post
             :url "/api/admin/mark-all-unverified"
             :body {}
             :error-msg "Failed to mark wines as unverified"
             :on-success #(assoc %1
                                 :success
                                 (str "Successfully marked "
                                      (:wines-updated %2)
                                      " wines as unverified"))
             :after #(fetch-wines app-state)}))

(defn- verbose-logging-request!
  [app-state opts]
  (request! app-state
            (assoc opts
                   :on-success
                   #(assoc-in %1
                     [:verbose-logging :enabled?]
                     (boolean (:verbose? %2))))))

(defn fetch-verbose-logging-state
  [app-state]
  (verbose-logging-request! app-state
                            {:url "/api/admin/verbose-logging"
                             :error-msg "Failed to get verbose logging state"
                             :loading [:verbose-logging :loading?]}))

(defn set-verbose-logging-state
  [app-state enabled?]
  (verbose-logging-request! app-state
                            {:method :post
                             :url "/api/admin/verbose-logging"
                             :body {:enabled? enabled?}
                             :error-msg "Failed to update verbose logging state"
                             :loading [:verbose-logging :updating?]}))

(def ^:private job-types
  {:drinking-window {:start-url "/api/admin/start-drinking-window-job"
                     :flag :regenerating-drinking-windows?
                     :label "drinking windows"}
   :wine-summary {:start-url "/api/admin/start-wine-summary-job"
                  :flag :regenerating-wine-summaries?
                  :label "wine summaries"}})

(def ^:private poll-delay-ms 2000)
(def ^:private max-poll-delay-ms 20000)
(def ^:private max-poll-failures 5)

(defn- job-outcome
  "What one poll of a bulk job means: [:poll], or [:done banner-key message]
   once it has finished."
  [{:keys [label]} {:keys [status total failed-wines error]}]
  (case status
    "running" [:poll]
    "completed"
    (if (seq failed-wines)
      [:done :error
       (str "Regenerated " (- total (count failed-wines))
            "/" total
            " wines successfully. " (count failed-wines)
            " failed: "
            (string/join ", " (map #(str "ID " (:wine-id %)) failed-wines)))]
      [:done :success
       (str "Successfully regenerated " label " for " total " wines")])
    "failed" [:done :error
              (str "Job failed while regenerating " label ": " error)]
    [:done nil nil]))

(defn- poll-job!
  "Polls a bulk job until it finishes, keeping :job-progress current. A
   failed poll is retried with backoff before giving up."
  [app-state job-id job-type failures]
  (let [{:keys [flag] :as job} (job-types job-type)
        finish! (fn [banner message]
                  (swap! app-state #(cond-> (dissoc % flag :job-progress)
                                      banner (assoc banner message))))
        again! (fn [failures delay-ms]
                 (js/setTimeout #(poll-job! app-state job-id job-type failures)
                                delay-ms))]
    (go
     (let [{:keys [success data error]}
           (<! (api-request http/get
                            (str "/api/admin/job-status/" job-id)
                            nil
                            "Failed to get job status"))]
       (if success
         (let [[step banner message] (job-outcome job data)]
           (swap! app-state assoc
             :job-progress
             {:progress (or (:progress data) 0)
              :total (or (:total data) 0)
              :status (:status data)
              :job-type job-type})
           (if (= step :poll)
             (again! 0 poll-delay-ms)
             (do (fetch-wines app-state) (finish! banner message))))
         (let [failures (inc failures)
               delay-ms (min max-poll-delay-ms
                             (* poll-delay-ms (js/Math.pow 2 failures)))]
           (if (< failures max-poll-failures)
             (do (swap! app-state update
                   :job-progress assoc
                   :job-type job-type
                   :status "retrying"
                   :retry-attempt failures
                   :retry-max max-poll-failures
                   :retry-delay delay-ms)
                 (again! failures delay-ms))
             (finish! :error
                      (str "Failed to check job status after " max-poll-failures
                           " attempts: " error)))))))))

(defn- start-job-for-filtered-wines!
  "Starts a bulk AI job over the wines the list currently shows."
  [app-state job-type]
  (let [{:keys [start-url flag]} (job-types job-type)
        wine-ids (mapv :id (filters/filtered-sorted-wines app-state))]
    (when (seq wine-ids)
      (request! app-state
                {:method :post
                 :url start-url
                 :body {:wine-ids wine-ids
                        :provider (get-in @app-state [:ai :provider])}
                 :error-msg "Failed to start job"
                 :loading [flag]
                 :after #(do (swap! app-state assoc flag true)
                             (poll-job! app-state (:job-id %) job-type 0))}))))

(defn regenerate-filtered-drinking-windows
  [app-state]
  (start-job-for-filtered-wines! app-state :drinking-window))

(defn regenerate-filtered-wine-summaries
  [app-state]
  (start-job-for-filtered-wines! app-state :wine-summary))

(defn reset-database
  "Admin function to reset the database"
  [app-state]
  (request! app-state
            {:method :post
             :url "/api/admin/reset-database"
             :body {}
             :error-msg "Failed to reset database"
             :loading [:resetting-database?]
             :on-success (fn [_ _]
                           (assoc initial-app-state
                                  :success
                                  "Database reset successfully!"))}))

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
  (request! app-state
            {:method :post
             :url "/api/admin/reextract-recipe-timers"
             :body {}
             :error-msg "Failed to re-read recipe timers"
             :loading [:reextracting-recipe-timers?]
             :on-success (fn [state {:keys [recipes-updated recipes-failed]}]
                           (assoc state
                                  :success
                                  (str "Re-read timers for " recipes-updated
                                       " recipes"
                                       (when (pos? recipes-failed)
                                         (str "; " recipes-failed " failed")))))
             :after #(fetch-bar-data app-state)}))

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

(defn- extracted-recipes
  "The extract endpoint answers {:recipes [...]}, or a single recipe."
  [data]
  (vec (or (:recipes data) (when (:name data) [data]))))

(defn extract-recipe-from-message!
  [app-state message-id message-text]
  (swap! app-state assoc-in
    [:chat :save-recipe]
    {:extracting? true :message-id message-id :recipe nil :open? false})
  (request! app-state
            {:method :post
             :url "/api/cocktail-recipe-extract"
             :body {:message-text message-text}
             :error-msg "Failed to extract recipe"
             :error-path [:chat :error]
             :loading [:chat :save-recipe :extracting?]
             :on-success #(update-in %1
                                     [:chat :save-recipe]
                                     assoc
                                     :recipes (extracted-recipes %2)
                                     :open? true)}))

(defn extract-recipe-from-image!
  [app-state image-data]
  (swap! app-state assoc-in
    [:chat :save-recipe]
    {:extracting? false :recipe nil :open? false :origin :photo})
  (request! app-state
            {:method :post
             :url "/api/cocktail-recipe-extract"
             :body {:image image-data}
             :error-msg "Failed to extract recipe"
             :error-path [:bar :photo-import :error]
             :loading [:bar :photo-import :extracting?]
             :on-success #(-> %1
                              (assoc-in [:bar :photo-import :open?] false)
                              (update-in [:chat :save-recipe]
                                         assoc
                                         :recipes (extracted-recipes %2)
                                         :open? true))}))