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

;; Wine Classification Handlers
(defn get-classifications [_]
  (try
    (let [classifications (db/get-classifications)]
      (response/response classifications))
    (catch Exception e
      (server-error e))))

(defn get-regions-by-country [{{:keys [country]} :path-params}]
  (try
    (let [regions (db/get-regions-by-country country)]
      (response/response regions))
    (catch Exception e
      (server-error e))))

(defn get-aocs-by-region [{{:keys [country region]} :path-params}]
  (try
    (let [aocs (db/get-aocs-by-region country region)]
      (response/response aocs))
    (catch Exception e
      (server-error e))))

;; Wine Handlers
(defn get-all-wines [_]
  (try
    (let [wines (db/get-all-wines)]
      (response/response wines))
    (catch Exception e
      (server-error e))))

(defn get-all-wines-with-ratings [_]
  (try
    (let [wines (db/get-all-wines-with-ratings)]
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
        wine-with-types (update wine :styles vec)  ;; Ensure styles is a vector
        prepared-wine (update wine-with-types :price bigdec)]
    (try
      (let [created-wine (db/create-wine prepared-wine)]
        {:status 201
         :body created-wine})
      (catch org.postgresql.util.PSQLException e
        {:status 400
         :body {:error "Invalid wine data"
                :details (.getMessage e)}})
      (catch Exception e
        {:status 500
         :body {:error (ex-message e)}}))))

(defn update-wine [{{:keys [id]} :path-params
                    {:keys [body]} :parameters}]
  (try
    (if (db/get-wine (parse-long id))
      (let [wine-with-types (update body :styles vec)
            prepared-wine (update wine-with-types :price bigdec)
            updated (db/update-wine! (parse-long id) prepared-wine)]
        (response/response updated))
      (response/not-found {:error "Wine not found"}))
    (catch org.postgresql.util.PSQLException e
      {:status 400
       :body {:error "Invalid wine data"
              :details (.getMessage e)}})
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

;; Tasting Notes Handlers
(defn get-tasting-notes-by-wine [{{:keys [id]} :path-params}]
  (try
    (let [tasting-notes (db/get-tasting-notes-by-wine (parse-long id))]
      (response/response tasting-notes))
    (catch Exception e
      (server-error e))))

(defn get-tasting-note [{{:keys [id note-id]} :path-params}]
  (try
    (if-let [note (db/get-tasting-note (parse-long note-id))]
      (response/response note)
      (response/not-found {:error "Tasting note not found"}))
    (catch Exception e
      (server-error e))))

(defn create-tasting-note [{{:keys [id]} :path-params {:keys [body]} :parameters}]
  (try
    (if (db/get-wine (parse-long id))
      (let [note-with-wine-id (assoc body :wine_id (parse-long id))
            created-note (db/create-tasting-note note-with-wine-id)]
        {:status 201
         :body created-note})
      (response/not-found {:error "Wine not found"}))
    (catch org.postgresql.util.PSQLException e
      {:status 400
       :body {:error "Invalid tasting note data"
              :details (.getMessage e)}})
    (catch Exception e
      (server-error e))))

(defn update-tasting-note [{{:keys [note-id]} :path-params {:keys [body]} :parameters}]
  (try
    (if (db/get-tasting-note (parse-long note-id))
      (let [updated (db/update-tasting-note! (parse-long note-id) body)]
        (response/response updated))
      (response/not-found {:error "Tasting note not found"}))
    (catch org.postgresql.util.PSQLException e
      {:status 400
       :body {:error "Invalid tasting note data"
              :details (.getMessage e)}})
    (catch Exception e
      (server-error e))))

(defn delete-tasting-note [{{:keys [note-id]} :path-params}]
  (try
    (if (db/get-tasting-note (parse-long note-id))
      (do
        (db/delete-tasting-note! (parse-long note-id))
        (no-content))
      (response/not-found {:error "Tasting note not found"}))
    (catch Exception e
      (server-error e))))

(defn create-classification [request]
  (let [classification (-> request :parameters :body)
        classification-with-levels (update classification :levels vec)]  ;; Ensure levels is a vector
    (try
      (let [created-classification
            (db/create-or-update-classification classification-with-levels)]
        {:status 201
         :body created-classification})
      (catch org.postgresql.util.PSQLException e
        {:status 400
         :body {:error "Invalid classification data"
                :details (.getMessage e)}})
      (catch Exception e
        (server-error e)))))

