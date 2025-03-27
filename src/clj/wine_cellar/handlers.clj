(ns wine-cellar.handlers
  (:require [wine-cellar.db.api :as api]
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
    (let [classifications (api/get-classifications)]
      (response/response classifications))
    (catch Exception e
      (server-error e))))

(defn get-regions-by-country [{{:keys [country]} :path-params}]
  (try
    (let [regions (api/get-regions-by-country country)]
      (response/response regions))
    (catch Exception e
      (server-error e))))

(defn get-aocs-by-region [{{:keys [country region]} :path-params}]
  (try
    (let [aocs (api/get-aocs-by-region country region)]
      (response/response aocs))
    (catch Exception e
      (server-error e))))

;; Wine Handlers
(defn get-all-wines [_]
  (try
    (let [wines (api/get-all-wines)]
      (response/response wines))
    (catch Exception e
      (server-error e))))

(defn get-all-wines-with-ratings [_]
  (try
    (let [wines (api/get-all-wines-with-ratings)]
      (response/response wines))
    (catch Exception e
      (server-error e))))

(defn get-wine [{{:keys [id]} :path-params}]
  (try
    (if-let [wine (api/get-wine (parse-long id))]
      (response/response wine)
      (response/not-found {:error "Wine not found"}))
    (catch Exception e
      (server-error e))))

(defn create-wine [request]
  (tap> request)
  (let [wine (-> request :parameters :body)
        create-classification? (:create-classification-if-needed wine)
        wine-without-flag (dissoc wine :create-classification-if-needed)]
    (try
      ;; Check if we need to create a new classification
      (when create-classification?
        (let [classification {:country (:country wine)
                              :region (:region wine)
                              :aoc (:aoc wine)
                              :classification (:classification wine)
                              :levels (when (:level wine) [(:level wine)])}]
          ;; Only create if all required fields are present
          (when (and (:country classification) (:region classification))
            (api/create-or-update-classification classification))))

      ;; Now create the wine
      (let [created-wine (api/create-wine wine-without-flag)]
        {:status 201
         :body created-wine})
      (catch Exception e
        (server-error e)))))

(defn update-wine [{{:keys [id]} :path-params
                    {:keys [body]} :parameters}]
  (try
    (if (api/get-wine (parse-long id))
      (let [updated (api/update-wine! (parse-long id) body)]
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
    (if (api/get-wine (parse-long id))
      (do
        (api/delete-wine! (parse-long id))
        (no-content))
      (response/not-found {:error "Wine not found"}))
    (catch Exception e
      (server-error e))))

(defn adjust-quantity [{{:keys [id]} :path-params
                        {:keys [adjustment]} :body-params}]
  (try
    (if (api/get-wine (parse-long id))
      (let [updated (api/adjust-quantity (parse-long id) adjustment)]
        (response/response updated))
      (response/not-found {:error "Wine not found"}))
    (catch Exception e
      (server-error e))))

(defn update-tasting-window [{{:keys [id]} :path-params
                              {:keys [drink_from_year drink_until_year]} :body-params}]
  (try
    (if (api/get-wine (parse-long id))
      (let [updated (api/update-wine-tasting-window (parse-long id)
                                                    drink_from_year
                                                    drink_until_year)]
        (response/response updated))
      (response/not-found {:error "Wine not found"}))
    (catch Exception e
      (server-error e))))

;; Tasting Notes Handlers
(defn get-tasting-notes-by-wine [{{:keys [id]} :path-params}]
  (try
    (let [tasting-notes (api/get-tasting-notes-by-wine (parse-long id))]
      (response/response tasting-notes))
    (catch Exception e
      (server-error e))))

(defn get-tasting-note [{{:keys [id note-id]} :path-params}]
  (try
    (if-let [note (api/get-tasting-note (parse-long note-id))]
      (response/response note)
      (response/not-found {:error "Tasting note not found"}))
    (catch Exception e
      (server-error e))))

(defn create-tasting-note [{{:keys [id]} :path-params {:keys [body]} :parameters}]
  (try
    (if (api/get-wine (parse-long id))
      (let [note-with-wine-id (assoc body :wine_id (parse-long id))
            created-note (api/create-tasting-note note-with-wine-id)]
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
    (if (api/get-tasting-note (parse-long note-id))
      (let [updated (api/update-tasting-note! (parse-long note-id) body)]
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
    (if (api/get-tasting-note (parse-long note-id))
      (do
        (api/delete-tasting-note! (parse-long note-id))
        (no-content))
      (response/not-found {:error "Tasting note not found"}))
    (catch Exception e
      (server-error e))))

(defn create-classification [request]
  (let [classification (-> request :parameters :body)]
    (try
      (let [created-classification
            (api/create-or-update-classification classification)]
        {:status 201
         :body created-classification})
      (catch org.postgresql.util.PSQLException e
        {:status 400
         :body {:error "Invalid classification data"
                :details (.getMessage e)}})
      (catch Exception e
        (server-error e)))))

