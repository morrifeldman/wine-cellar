(ns wine-cellar.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! go]]))

(def api-base-url "http://localhost:3000")

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
  (go
    (let [response (<! (http/get (str api-base-url "/api/wines")
                                default-opts))]
      (if (:success response)
        (swap! app-state assoc
               :wines (:body response)
               :loading? false
               :error nil)
        (swap! app-state assoc
               :error (or (get-in response [:body :error]) "Failed to fetch wines")
               :loading? false)))))

(defn create-wine [app-state wine]
  (js/console.log "Sending wine data:" (clj->js wine))  ;; Add this line
  (go
    (let [response (<! (http/post (str api-base-url "/api/wines")
                                 (merge default-opts
                                        {:json-params wine})))]
      ;; Add response logging
      (when-not (:success response)
        (js/console.log "Error response:" (clj->js (:body response))))
      (if (:success response)
        (fetch-wines app-state)
        (swap! app-state assoc :error "Failed to create wine")))))

(defn delete-wine [app-state id]
  (go
    (let [response (<! (http/delete (str api-base-url "/api/wines/" id)
                                   default-opts))]
      (if (:success response)
        (swap! app-state update :wines #(remove (fn [wine] (= (:id wine) id)) %))
        (swap! app-state assoc 
               :error (or (get-in response [:body :error]) "Failed to delete wine"))))))
