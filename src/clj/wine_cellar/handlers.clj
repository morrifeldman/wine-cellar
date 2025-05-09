(ns wine-cellar.handlers
  (:require [wine-cellar.db.api :as db-api]
            [wine-cellar.db.setup :as db-setup]
            [wine-cellar.ai.anthropic :as anthropic]
            [ring.util.response :as response]))

(defn- no-content [] {:status 204 :headers {} :body nil})

(defn- server-error
  [e]
  (tap> e)
  {:status 500
   :headers {}
   :body {:error "Internal server error" :details (.getMessage e)}})

;; Wine Classification Handlers
(defn get-classifications
  [_]
  (try (let [classifications (db-api/get-classifications)]
         (response/response classifications))
       (catch Exception e (server-error e))))

(defn get-regions-by-country
  [{{:keys [country]} :path-params}]
  (try (let [regions (db-api/get-regions-by-country country)]
         (response/response regions))
       (catch Exception e (server-error e))))

(defn get-aocs-by-region
  [{{:keys [country region]} :path-params}]
  (try (let [aocs (db-api/get-aocs-by-region country region)]
         (response/response aocs))
       (catch Exception e (server-error e))))

(defn get-all-wines-with-ratings
  [_]
  (try (let [wines (db-api/get-all-wines-with-ratings)]
         (response/response wines))
       (catch Exception e (server-error e))))

(defn get-wine
  [{{:keys [id]} :path-params}]
  (try (if-let [wine (db-api/get-wine (parse-long id))]
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
             (db-api/create-or-update-classification classification)))
         (let [created-wine (db-api/create-wine wine)]
           {:status 201 :body created-wine})
         (catch Exception e (server-error e)))))

(defn update-wine
  [{{:keys [id]} :path-params {:keys [body]} :parameters}]
  (try (if (db-api/get-wine (parse-long id))
         (let [updated (db-api/update-wine! (parse-long id) body)]
           (response/response updated))
         (response/not-found {:error "Wine not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid wine data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-wine
  [{{:keys [id]} :path-params}]
  (try (if (db-api/get-wine (parse-long id))
         (do (db-api/delete-wine! (parse-long id)) (no-content))
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

(defn adjust-quantity
  [{{:keys [id]} :path-params {:keys [adjustment]} :body-params}]
  (try (if (db-api/get-wine (parse-long id))
         (let [updated (db-api/adjust-quantity (parse-long id) adjustment)]
           (response/response updated))
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

;; Tasting Notes Handlers
(defn get-tasting-notes-by-wine
  [{{:keys [id]} :path-params}]
  (try (let [tasting-notes (db-api/get-tasting-notes-by-wine (parse-long id))]
         (response/response tasting-notes))
       (catch Exception e (server-error e))))

(defn get-tasting-note
  [{{:keys [note-id]} :path-params}]
  (try (if-let [note (db-api/get-tasting-note (parse-long note-id))]
         (response/response note)
         (response/not-found {:error "Tasting note not found"}))
       (catch Exception e (server-error e))))

(defn create-tasting-note
  [{{:keys [id]} :path-params {:keys [body]} :parameters}]
  (try (if (db-api/get-wine (parse-long id))
         (let [note-with-wine-id (assoc body :wine_id (parse-long id))
               created-note (db-api/create-tasting-note note-with-wine-id)]
           {:status 201 :body created-note})
         (response/not-found {:error "Wine not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid tasting note data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn update-tasting-note
  [{{:keys [note-id]} :path-params {:keys [body]} :parameters}]
  (try (if (db-api/get-tasting-note (parse-long note-id))
         (let [updated (db-api/update-tasting-note! (parse-long note-id) body)]
           (response/response updated))
         (response/not-found {:error "Tasting note not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid tasting note data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-tasting-note
  [{{:keys [note-id]} :path-params}]
  (try (if (db-api/get-tasting-note (parse-long note-id))
         (do (db-api/delete-tasting-note! (parse-long note-id)) (no-content))
         (response/not-found {:error "Tasting note not found"}))
       (catch Exception e (server-error e))))

;; Grape Varieties Handlers
(defn get-grape-varieties
  [_]
  (try (let [varieties (db-api/get-all-grape-varieties)]
         {:status 200 :body varieties})
       (catch Exception e (server-error e))))

(defn create-grape-variety
  [{:keys [body-params]}]
  (try (let [variety (db-api/create-grape-variety (:variety_name body-params))]
         {:status 201 :body variety})
       (catch Exception e (server-error e))))

(defn get-grape-variety
  [{:keys [path-params]}]
  (try (let [id (Integer/parseInt (:id path-params))
             variety (db-api/get-grape-variety id)]
         (if variety
           {:status 200 :body variety}
           {:status 404 :body {:error "Grape variety not found"}}))
       (catch Exception e (server-error e))))

(defn update-grape-variety
  [{:keys [path-params body-params]}]
  (try (let [id (Integer/parseInt (:id path-params))
             variety (db-api/update-grape-variety! id
                                                   (:variety_name body-params))]
         (if variety
           {:status 200 :body variety}
           {:status 404 :body {:error "Grape variety not found"}}))
       (catch Exception e (server-error e))))

(defn delete-grape-variety
  [{:keys [path-params]}]
  (try (let [id (Integer/parseInt (:id path-params))]
         (db-api/delete-grape-variety! id)
         {:status 204})
       (catch Exception e (server-error e))))

;; Wine Varieties Handlers
(defn get-wine-varieties
  [{:keys [path-params]}]
  (try (let [wine-id (Integer/parseInt (:id path-params))
             wine (db-api/get-wine wine-id)]
         (if wine
           (let [varieties (db-api/get-wine-grape-varieties wine-id)]
             {:status 200 :body varieties})
           {:status 404 :body {:error "Wine not found"}}))
       (catch Exception e (server-error e))))

(defn add-variety-to-wine
  [{:keys [path-params body-params]}]
  (try (let [wine-id (Integer/parseInt (:id path-params))
             variety-id (:variety_id body-params)
             percentage (:percentage body-params)
             wine (db-api/get-wine wine-id)]
         (if wine
           (let [variety (db-api/get-grape-variety variety-id)]
             (if variety
               (let [result (db-api/associate-grape-variety-with-wine
                             wine-id
                             variety-id
                             percentage)]
                 {:status 201 :body result})
               {:status 404 :body {:error "Grape variety not found"}}))
           {:status 404 :body {:error "Wine not found"}}))
       (catch Exception e (server-error e))))

(defn update-wine-variety-percentage
  [{:keys [path-params body-params]}]
  (try (let [wine-id (Integer/parseInt (:id path-params))
             variety-id (Integer/parseInt (:variety-id path-params))
             percentage (:percentage body-params)
             wine (db-api/get-wine wine-id)]
         (if wine
           (let [variety (db-api/get-grape-variety variety-id)]
             (if variety
               (let [result (db-api/associate-grape-variety-with-wine
                             wine-id
                             variety-id
                             percentage)]
                 {:status 200 :body result})
               {:status 404 :body {:error "Grape variety not found"}}))
           {:status 404 :body {:error "Wine not found"}}))
       (catch Exception e (server-error e))))

(defn remove-variety-from-wine
  [{:keys [path-params]}]
  (try (let [wine-id (Integer/parseInt (:id path-params))
             variety-id (Integer/parseInt (:variety-id path-params))
             wine (db-api/get-wine wine-id)]
         (if wine
           (let [variety (db-api/get-grape-variety variety-id)]
             (if variety
               (do (db-api/remove-grape-variety-from-wine wine-id variety-id)
                   {:status 204})
               {:status 404 :body {:error "Grape variety not found"}}))
           {:status 404 :body {:error "Wine not found"}}))
       (catch Exception e (server-error e))))

(defn create-classification
  [request]
  (let [classification (-> request
                           :parameters
                           :body)]
    (try (let [created-classification (db-api/create-or-update-classification
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
    (db-api/ping-db)
    (response/response {:status "healthy"
                        :database "connected"
                        :timestamp (str (java.time.Instant/now))})
    (catch Exception e (server-error e))))

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

(defn suggest-drinking-window
  [{{:keys [wine]} :body-params}]
  (try (if (nil? wine)
         {:status 400 :body {:error "Wine details are required"}}
         (let [result (anthropic/suggest-drinking-window wine)]
           (response/response result)))
       (catch clojure.lang.ExceptionInfo e
         (let [data (ex-data e)]
           {:status 500
            :body {:error "AI analysis failed"
                   :details (.getMessage e)
                   :response (:response data)}}))
       (catch Exception e (server-error e))))
