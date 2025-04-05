(ns wine-cellar.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! go]]
            [wine-cellar.config :as config]))

(def api-base-url (config/get-api-base-url))

(def default-opts
  {:with-credentials? false})

;; Classification endpoints
(defn fetch-classifications [app-state]
  (go
    (let [response (<! (http/get (str api-base-url "/api/classifications")
                                 default-opts))]
      (if (:success response)
        (swap! app-state assoc :classifications (:body response))
        (swap! app-state assoc :error "Failed to fetch classifications")))))

(defn fetch-regions [app-state country]
  (go
    (let [response (<! (http/get (str api-base-url "/api/classifications/regions/" country)
                                 default-opts))]
      (if (:success response)
        (swap! app-state assoc :regions (:body response))
        (swap! app-state assoc :error "Failed to fetch regions")))))

;; Wine endpoints
(defn fetch-wines [app-state]
  (swap! app-state assoc :loading? true)
  (js/console.log "Fetching wines...")
  (go
    (let [response (<! (http/get (str api-base-url "/api/wines")
                                 default-opts))]
      (js/console.log "Wine API response:" (clj->js response))
      (if (:success response)
        (do
          (js/console.log "Success! Wines count:" (count (:body response)))
          (swap! app-state assoc
                 :wines (:body response)
                 :loading? false
                 :error nil))
        (do
          (js/console.log "Error fetching wines:" (clj->js (:error-text response)))
          (swap! app-state assoc
                 :error (or (get-in response [:body :error]) "Failed to fetch wines")
                 :loading? false))))))

(defn create-wine [app-state wine]
  (js/console.log "Sending wine data:" (clj->js wine))
  (go
    (let [response (<! (http/post (str api-base-url "/api/wines")
                                  (merge default-opts
                                         {:json-params wine})))]
      (if (:success response)
        (do
          (fetch-wines app-state)
          (fetch-classifications app-state)  ;; Refresh classifications after adding a wine
          (swap! app-state assoc :new-wine {}))
        ;; Just use the error message directly from the response body
        (swap! app-state assoc :error (:body response))))))

(defn delete-wine [app-state id]
  (go
    (let [response (<! (http/delete (str api-base-url "/api/wines/" id)
                                    default-opts))]
      (if (:success response)
        (swap! app-state update :wines #(remove (fn [wine] (= (:id wine) id)) %))
        (swap! app-state assoc
               :error (or (get-in response [:body :error]) "Failed to delete wine"))))))

;; Tasting Notes endpoints
(defn fetch-tasting-notes [app-state wine-id]
  (go
    (let [response (<! (http/get (str api-base-url "/api/wines/" wine-id "/tasting-notes")
                                 default-opts))]
      (if (:success response)
        (swap! app-state assoc :tasting-notes (:body response))
        (swap! app-state assoc :error "Failed to fetch tasting notes")))))

(defn create-tasting-note [app-state wine-id note]
  (go
    (let [response (<! (http/post (str api-base-url "/api/wines/" wine-id "/tasting-notes")
                                  (merge default-opts
                                         {:json-params note})))]
      (if (:success response)
        (do
          (swap! app-state update :tasting-notes conj (:body response))
          (swap! app-state assoc :new-tasting-note {}))
        (swap! app-state assoc :error "Failed to create tasting note")))))

(defn update-tasting-note [app-state wine-id note-id note]
  (go
    (let [response (<! (http/put (str api-base-url "/api/wines/" wine-id "/tasting-notes/" note-id)
                                 (merge default-opts
                                        {:json-params note})))]
      (if (:success response)
        (swap! app-state update :tasting-notes
               (fn [notes]
                 (map #(if (= (:id %) note-id) (:body response) %) notes)))
        (swap! app-state assoc :error "Failed to update tasting note")))))

(defn delete-tasting-note [app-state wine-id note-id]
  (go
    (let [response (<! (http/delete (str api-base-url "/api/wines/" wine-id "/tasting-notes/" note-id)
                                    default-opts))]
      (if (:success response)
        (swap! app-state update :tasting-notes
               #(remove (fn [note] (= (:id note) note-id)) %))
        (swap! app-state assoc :error "Failed to delete tasting note")))))

(defn adjust-wine-quantity [app-state wine-id adjustment]
  (go
    (let [response (<! (http/post (str api-base-url "/api/wines/" wine-id "/adjust-quantity")
                                  (merge default-opts
                                         {:json-params {:adjustment adjustment}})))]
      (if (:success response)
        (swap! app-state update :wines
               (fn [wines]
                 (map #(if (= (:id %) wine-id)
                         (update % :quantity + adjustment)
                         %)
                      wines)))
        (swap! app-state assoc :error "Failed to update wine quantity")))))

(defn create-classification [app-state classification]
  (js/console.log "Sending classification data:" (clj->js classification))
  (go
    (let [response (<! (http/post (str api-base-url "/api/classifications")
                                  (merge default-opts
                                         {:json-params classification})))]
      (when-not (:success response)
        (js/console.log "Error response:" (clj->js (:body response))))
      (if (:success response)
        (do
          (swap! app-state assoc
                 :creating-classification? false
                 :new-classification nil)
          (fetch-classifications app-state))
        (swap! app-state assoc :error "Failed to create classification")))))

(defn update-wine [app-state id updates]
  (go
    (let [response (<! (http/put (str api-base-url "/api/wines/" id)
                                 (merge default-opts
                                        {:json-params updates})))]
      (if (:success response)
        (let [updated-wine (:body response)]
          ;; Update the wine in the list
          (swap! app-state update :wines
                 (fn [wines]
                   (map #(if (= (:id %) id) updated-wine %) wines))))
        (swap! app-state assoc :error "Failed to update wine")))))
