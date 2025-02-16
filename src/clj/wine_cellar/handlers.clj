(ns wine-cellar.handlers
  (:require [wine-cellar.db :as db]
            [ring.util.response :as response]))

(defn- no-content []
  {:status 204
   :headers {}
   :body nil})

(defn- server-error [e]
  {:status 500
   :headers {}
   :body {:error "Internal server error"
          :details (.getMessage e)}})

(defn get-all-wines [_]
  (try
    (let [wines (db/get-all-wines)]
      (response/response wines))
    (catch Exception e
      (server-error e))))

(defn get-wine [{{:keys [id]} :path-params}]
  (try
    (if-let [wine (db/get-wine (parse-long id))]
      (response/response wine)
      (response/not-found {:error "Wine not found"}))
    (catch Exception e
      (server-error e))))

(defn create-wine [request]
  (let [wine (-> request :parameters :body)
        wine-with-double-price (update wine :price double)]
    (try
      (let [created-wine (db/create-wine wine-with-double-price)]
        {:status 201
         :body created-wine})
      (catch Exception e
        {:status 500
         :body {:error (ex-message e)}}))))

(defn update-wine [{{:keys [id]} :path-params
                    :keys [body-params]}]
  (try
    (if (db/get-wine (parse-long id))
      (let [updated (db/update-wine! (parse-long id) body-params)]
        (response/response updated))
      (response/not-found {:error "Wine not found"}))
    (catch Exception e
      (server-error e))))

(defn delete-wine [{{:keys [id]} :path-params}]
  (try
    (if (db/get-wine (parse-long id))
      (do
        (db/delete-wine! (parse-long id))
        (no-content))
      (response/not-found {:error "Wine not found"}))
    (catch Exception e
      (server-error e))))

(defn adjust-quantity [{{:keys [id]} :path-params
                        {:keys [adjustment]} :body-params}]
  (try
    (if (db/get-wine (parse-long id))
      (let [updated (db/adjust-quantity (parse-long id) adjustment)]
        (response/response updated))
      (response/not-found {:error "Wine not found"}))
    (catch Exception e
      (server-error e))))
