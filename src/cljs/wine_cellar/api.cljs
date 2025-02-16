(ns wine-cellar.api
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! go]]))

(def api-base-url "http://localhost:3000")

(def default-opts
  {:with-credentials? false})

(defn fetch-wines [app-state]
  (swap! app-state assoc :loading? true)
  (go
    (let [response (<! (http/get (str api-base-url "/api/wines")
                                default-opts))]
      (if (:success response)
        (swap! app-state assoc 
               :wines (:body response)  ;; No need for unqualify-keys
               :loading? false
               :error nil)
        (swap! app-state assoc
               :error "Failed to fetch wines"
               :loading? false)))))

(defn create-wine [app-state wine]
  (go
    (let [response (<! (http/post (str api-base-url "/api/wines")
                                 (merge default-opts
                                        {:json-params wine})))]
      (if (:success response)
        (fetch-wines app-state)
        (swap! app-state assoc :error "Failed to create wine")))))

(defn delete-wine [app-state id]
  (go
    (let [response (<! (http/delete (str api-base-url "/api/wines/" id)
                                   default-opts))]
      (if (:success response)
        (swap! app-state update :wines #(remove (fn [wine] (= (:id wine) id)) %))
        (swap! app-state assoc :error "Failed to delete wine")))))
