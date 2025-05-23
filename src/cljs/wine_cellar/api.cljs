(ns wine-cellar.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! go chan put!]]
            [wine-cellar.config :as config]))

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

;; Generic API request function
(defn api-request
  [method url params error-msg]
  (let [result-chan (chan)]
    (go (let [request-opts (merge default-opts
                                  (when params {:json-params params}))
              _ (tap> [api-base-url url request-opts])
              response (<! (method (str api-base-url url) request-opts))
              result (handle-api-response response error-msg)]
          (put! result-chan result)))
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
  (go (let [result (<! (GET "/api/wines" "Failed to fetch wines"))]
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
  [app-state wine-id]
  (go
   (let [result (<! (GET (str "/api/wines/by-id/" wine-id)
                         "Failed to fetch wine details"))]
     (if (:success result)
       (let [wine-with-details (:data result)]
         ;; Update the wine in the list with full details including the
         ;; full image
         (swap! app-state update
           :wines
           (fn [wines]
             (map #(if (= (:id %) wine-id)
                     ;; Keep the latest_rating
                     (merge % wine-with-details)
                     %)
                  wines)))
         ;; Set as selected wine
         (swap! app-state assoc :selected-wine-id wine-id))
       (swap! app-state assoc :error (:error result))))))

(defn create-wine
  [app-state wine]
  (js/console.log "Sending wine data:" (clj->js wine))
  (go (let [result (<! (POST "/api/wines" wine "Failed to create wine"))]
        (if (:success result)
          (do (fetch-wines app-state)
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
  [app-state wine-id note]
  (go
   (let [result (<! (POST (str "/api/wines/by-id/" wine-id "/tasting-notes")
                          note
                          "Failed to create tasting note"))]
     (if (:success result)
       (do (swap! app-state update :tasting-notes conj (:data result))
           (swap! app-state assoc :new-tasting-note {} :submitting-note? false))
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
                  (let [result (<! (POST "/api/wines/suggest-drinking-window"
                                         {:wine wine}
                                         "Failed to suggest drinking window"))]
                    (swap! app-state assoc :suggesting-drinking-window? false)
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
  (js/Promise.
   (fn [resolve reject]
     (go (let [result (<! (POST "/api/grape-varieties"
                                {:variety_name (or (:name variety)
                                                   (:variety_name variety))}
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

;; Function removed - using two-step approach instead

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

;; admin endpoints
(defn reset-schema
  []
  (js/Promise. (fn [resolve reject]
                 (go (let [result (<! (POST "/api/admin/schema"
                                            nil
                                            "Failed to reset database schema"))]
                       (if (:success result)
                         (resolve result)
                         (reject (js/Error. (:error result)))))))))
