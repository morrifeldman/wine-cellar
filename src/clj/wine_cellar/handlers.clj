(ns wine-cellar.handlers
  (:require [wine-cellar.db.api :as api]
            [wine-cellar.db.setup :as db-setup]
            [wine-cellar.ai.anthropic :as anthropic]
            [ring.util.response :as response]))

(defn- no-content [] {:status 204 :headers {} :body nil})

(defn- server-error
  [e]
  {:status 500
   :headers {}
   :body {:error "Internal server error" :details (.getMessage e)}})

;; Wine Classification Handlers
(defn get-classifications
  [_]
  (try (let [classifications (api/get-classifications)]
         (response/response classifications))
       (catch Exception e (server-error e))))

(defn get-regions-by-country
  [{{:keys [country]} :path-params}]
  (try (let [regions (api/get-regions-by-country country)]
         (response/response regions))
       (catch Exception e (server-error e))))

(defn get-aocs-by-region
  [{{:keys [country region]} :path-params}]
  (try (let [aocs (api/get-aocs-by-region country region)]
         (response/response aocs))
       (catch Exception e (server-error e))))

(defn get-all-wines-with-ratings
  [_]
  (try (let [wines (api/get-all-wines-with-ratings)] (response/response wines))
       (catch Exception e (server-error e))))

(defn get-wine
  [{{:keys [id]} :path-params}]
  (try (if-let [wine (api/get-wine (parse-long id))]
         (response/response wine)
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

(defn create-wine
  [request]
  (let [wine (-> request
                 :parameters
                 :body)]
    (try (let [classification {:country (:country wine)
                               :region (:region wine)
                               :aoc (:aoc wine)
                               :classification (:classification wine)
                               :levels (when (:level wine) [(:level wine)])}]
           ;; Only create if all required fields are present
           (when (and (:country classification) (:region classification))
             (tap> ["Creating classification" classification])
             (api/create-or-update-classification classification)))
         (tap> ["Now Creating wine" wine])
         ;; Now create the wine
         (let [created-wine (api/create-wine wine)]
           {:status 201 :body created-wine})
         (catch Exception e (server-error e)))))

(defn update-wine
  [{{:keys [id]} :path-params {:keys [body]} :parameters}]
  (try (if (api/get-wine (parse-long id))
         (let [updated (api/update-wine! (parse-long id) body)]
           (response/response updated))
         (response/not-found {:error "Wine not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid wine data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-wine
  [{{:keys [id]} :path-params}]
  (try (if (api/get-wine (parse-long id))
         (do (api/delete-wine! (parse-long id)) (no-content))
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

(defn adjust-quantity
  [{{:keys [id]} :path-params {:keys [adjustment]} :body-params}]
  (try (if (api/get-wine (parse-long id))
         (let [updated (api/adjust-quantity (parse-long id) adjustment)]
           (response/response updated))
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

;; Tasting Notes Handlers
(defn get-tasting-notes-by-wine
  [{{:keys [id]} :path-params}]
  (try (let [tasting-notes (api/get-tasting-notes-by-wine (parse-long id))]
         (response/response tasting-notes))
       (catch Exception e (server-error e))))

(defn get-tasting-note
  [{{:keys [note-id]} :path-params}]
  (try (if-let [note (api/get-tasting-note (parse-long note-id))]
         (response/response note)
         (response/not-found {:error "Tasting note not found"}))
       (catch Exception e (server-error e))))

(defn create-tasting-note
  [{{:keys [id]} :path-params {:keys [body]} :parameters}]
  (try (if (api/get-wine (parse-long id))
         (let [note-with-wine-id (assoc body :wine_id (parse-long id))
               created-note (api/create-tasting-note note-with-wine-id)]
           {:status 201 :body created-note})
         (response/not-found {:error "Wine not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid tasting note data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn update-tasting-note
  [{{:keys [note-id]} :path-params {:keys [body]} :parameters}]
  (try (if (api/get-tasting-note (parse-long note-id))
         (let [updated (api/update-tasting-note! (parse-long note-id) body)]
           (response/response updated))
         (response/not-found {:error "Tasting note not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid tasting note data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-tasting-note
  [{{:keys [note-id]} :path-params}]
  (try (if (api/get-tasting-note (parse-long note-id))
         (do (api/delete-tasting-note! (parse-long note-id)) (no-content))
         (response/not-found {:error "Tasting note not found"}))
       (catch Exception e (server-error e))))

(defn create-classification
  [request]
  (let [classification (-> request
                           :parameters
                           :body)]
    (try (let [created-classification (api/create-or-update-classification
                                       classification)]
           {:status 201 :body created-classification})
         (catch org.postgresql.util.PSQLException e
           {:status 400
            :body {:error "Invalid classification data"
                   :details (.getMessage e)}})
         (catch Exception e (server-error e)))))

(defn health-check
  [_]
  (try
    ;; Try to connect to the database
    (api/ping-db)
    (response/response {:status "healthy"
                        :database "connected"
                        :timestamp (str (java.time.Instant/now))})
    (catch Exception e
      {:status 503
       :body {:status "unhealthy"
              :database "disconnected"
              :error (.getMessage e)
              :timestamp (str (java.time.Instant/now))}})))

;; Admin Handlers
(defn reset-schema
  [_]
  (try (db-setup/reset-schema!)
       (response/response {:status "success"
                           :message "Database schema reset successfully"
                           :timestamp (str (java.time.Instant/now))})
       (catch Exception e (server-error e))))

;; AI Analysis Handlers
(defn analyze-wine-label
  [{{:keys [label_image back_label_image]} :body-params}]
  (try (if (nil? label_image)
         {:status 400 :body {:error "Label image is required"}}
         (let [result (anthropic/analyze-wine-label label_image
                                                    back_label_image)]
           (response/response result)))
       (catch clojure.lang.ExceptionInfo e
         (let [data (ex-data e)]
           {:status 500
            :body {:error "AI analysis failed"
                   :details (.getMessage e)
                   :response (:response data)}}))
       (catch Exception e (server-error e))))
