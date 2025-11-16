(ns wine-cellar.handlers
  (:require [clojure.string :as str]
            [wine-cellar.db.api :as db-api]
            [wine-cellar.ai.core :as ai]
            [wine-cellar.db.setup :as db-setup]
            [wine-cellar.admin.bulk-operations]
            [ring.util.response :as response]
            [wine-cellar.summary :as summary]
            [wine-cellar.logging :as logging]))

(defn- no-content [] {:status 204 :headers {} :body nil})

(defn- server-error
  [e]
  (tap> e)
  {:status 500
   :headers {}
   :body {:error "Internal server error" :details (.getMessage e)}})

;; Wine Classification Handlers

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

(defn get-classification
  [{{{:keys [id]} :path} :parameters}]
  (try (if-let [classification (db-api/get-classification id)]
         (response/response classification)
         (response/not-found {:error "Classification not found"}))
       (catch Exception e (server-error e))))

(defn get-classifications
  [_]
  (try (let [classifications (db-api/get-classifications)]
         (response/response classifications))
       (catch Exception e (server-error e))))

(defn update-classification
  [{{{:keys [id]} :path body :body} :parameters}]
  (try (if-let [updated (db-api/update-classification! id body)]
         (response/response updated)
         (response/not-found {:error "Classification not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid classification data"
                 :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-classification
  [{{{:keys [id]} :path} :parameters}]
  (try (if (db-api/delete-classification! id)
         (no-content)
         (response/not-found {:error "Classification not found"}))
       (catch Exception e (server-error e))))

(defn get-regions-by-country
  [{{{:keys [country]} :path} :parameters}]
  (try (let [regions (db-api/get-regions-by-country country)]
         (response/response regions))
       (catch Exception e (server-error e))))

(defn get-aocs-by-region
  [{{{:keys [country region]} :path} :parameters}]
  (try (let [aocs (db-api/get-aocs-by-region country region)]
         (response/response aocs))
       (catch Exception e (server-error e))))

(defn get-wines-for-list
  [_]
  (try (let [wines (db-api/get-wines-for-list)] (response/response wines))
       (catch Exception e (server-error e))))

(defn get-wine
  [{{{:keys [id]} :path {:keys [include_images]} :query} :parameters}]
  (try (if-let [wine (db-api/get-wine id include_images)]
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
  [{{{:keys [id]} :path body :body} :parameters}]
  (try
    (if (db-api/wine-exists? id)
      (let [updated (db-api/update-wine! id body)] (response/response updated))
      (response/not-found {:error "Wine not found"}))
    (catch org.postgresql.util.PSQLException e
      {:status 400 :body {:error "Invalid wine data" :details (.getMessage e)}})
    (catch Exception e (server-error e))))

(defn delete-wine
  [{{{:keys [id]} :path} :parameters}]
  (try (if (db-api/wine-exists? id)
         (do (db-api/delete-wine! id) (no-content))
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

(defn adjust-quantity
  [{{{:keys [id]} :path {:keys [adjustment]} :body} :parameters}]
  (try (if (db-api/wine-exists? id)
         (let [updated (db-api/adjust-quantity id adjustment)]
           (response/response updated))
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

;; Tasting Notes Handlers
(defn get-tasting-notes-by-wine
  [{{{:keys [id]} :path} :parameters}]
  (try (let [tasting-notes (db-api/get-tasting-notes-by-wine id)]
         (response/response tasting-notes))
       (catch Exception e (server-error e))))

(defn get-tasting-note
  [{{{:keys [note-id]} :path} :parameters}]
  (try (if-let [note (db-api/get-tasting-note note-id)]
         (response/response note)
         (response/not-found {:error "Tasting note not found"}))
       (catch Exception e (server-error e))))

(defn create-tasting-note
  [{{{:keys [id]} :path body :body} :parameters}]
  (try (if (db-api/wine-exists? id)
         (let [note-with-wine-id (assoc body :wine_id id)
               created-note (db-api/create-tasting-note note-with-wine-id)]
           {:status 201 :body created-note})
         (response/not-found {:error "Wine not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid tasting note data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn update-tasting-note
  [{{{:keys [note-id]} :path body :body} :parameters}]
  (try (if (db-api/get-tasting-note note-id)
         (let [updated (db-api/update-tasting-note! note-id body)]
           (response/response updated))
         (response/not-found {:error "Tasting note not found"}))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid tasting note data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-tasting-note
  [{{{:keys [note-id]} :path} :parameters}]
  (try (if (db-api/get-tasting-note note-id)
         (do (db-api/delete-tasting-note! note-id) (no-content))
         (response/not-found {:error "Tasting note not found"}))
       (catch Exception e (server-error e))))

(defn get-tasting-note-sources
  [_]
  (try (let [sources (db-api/get-tasting-note-sources)]
         (response/response sources))
       (catch Exception e (server-error e))))

;; Grape Varieties Handlers
(defn get-grape-varieties
  [_]
  (try (let [varieties (db-api/get-all-grape-varieties)]
         {:status 200 :body varieties})
       (catch Exception e (server-error e))))

(defn create-grape-variety
  [{{{:keys [variety_name]} :body} :parameters}]
  (try (let [variety (db-api/create-grape-variety variety_name)]
         {:status 201 :body variety})
       (catch Exception e (server-error e))))

(defn get-grape-variety
  [{{{:keys [id]} :path} :parameters}]
  (try (let [variety (db-api/get-grape-variety id)]
         (if variety
           {:status 200 :body variety}
           {:status 404 :body {:error "Grape variety not found"}}))
       (catch Exception e (server-error e))))

(defn update-grape-variety
  [{{{:keys [id]} :path {:keys [variety_name]} :body} :parameters}]
  (try (let [variety (db-api/update-grape-variety! id variety_name)]
         (if variety
           {:status 200 :body variety}
           {:status 404 :body {:error "Grape variety not found"}}))
       (catch Exception e (server-error e))))

(defn delete-grape-variety
  [{{{:keys [id]} :path} :parameters}]
  (try (db-api/delete-grape-variety! id)
       (no-content)
       (catch Exception e (server-error e))))

;; Wine Varieties Handlers
(defn get-wine-varieties
  [{{{:keys [id]} :path} :parameters}]
  (try (if (db-api/wine-exists? id)
         (let [varieties (db-api/get-wine-grape-varieties id)]
           {:status 200 :body varieties})
         {:status 404 :body {:error "Wine not found"}})
       (catch Exception e (server-error e))))

(defn add-variety-to-wine
  [{{{:keys [id]} :path {:keys [variety_id percentage]} :body} :parameters}]
  (tap> ["add-variety-to-wine"
         {:wine-id id :variety-id variety_id :percentage percentage}])
  (try (if (db-api/wine-exists? id)
         (let [variety (db-api/get-grape-variety variety_id)]
           (if variety
             (let [result (db-api/associate-grape-variety-with-wine id
                                                                    variety_id
                                                                    percentage)]
               {:status 201 :body result})
             {:status 404 :body {:error "Grape variety not found"}}))
         {:status 404 :body {:error "Wine not found"}})
       (catch Exception e (server-error e))))

(defn update-wine-variety-percentage
  [{{{:keys [id variety-id]} :path {:keys [percentage]} :body} :parameters}]
  (try (if (db-api/wine-exists? id)
         (let [variety (db-api/get-grape-variety variety-id)]
           (if variety
             (let [result (db-api/associate-grape-variety-with-wine id
                                                                    variety-id
                                                                    percentage)]
               {:status 200 :body result})
             {:status 404 :body {:error "Grape variety not found"}}))
         {:status 404 :body {:error "Wine not found"}})
       (catch Exception e (server-error e))))

(defn remove-variety-from-wine
  [{{{:keys [id variety-id]} :path} :parameters}]
  (try (if (db-api/wine-exists? id)
         (let [variety (db-api/get-grape-variety variety-id)]
           (if variety
             (do (db-api/remove-grape-variety-from-wine id variety-id)
                 (no-content))
             {:status 404 :body {:error "Grape variety not found"}}))
         {:status 404 :body {:error "Wine not found"}})
       (catch Exception e (server-error e))))

;; AI Analysis Handlers
(defn analyze-wine-label
  [{{{:keys [label_image back_label_image provider]} :body} :parameters}]
  (try (if (nil? label_image)
         {:status 400 :body {:error "Label image is required"}}
         (let [result
               (ai/analyze-wine-label provider label_image back_label_image)]
           (response/response result)))
       (catch clojure.lang.ExceptionInfo e
         (let [data (ex-data e)]
           {:status 500
            :body {:error "AI analysis failed"
                   :details (.getMessage e)
                   :response (:response data)}}))
       (catch Exception e (server-error e))))

(defn suggest-drinking-window
  [{{{:keys [wine provider]} :body} :parameters}]
  (try (if (nil? wine)
         {:status 400 :body {:error "Wine details are required"}}
         (let [enriched-wine (if (:id wine)
                               (first (db-api/get-enriched-wines-by-ids
                                       [(:id wine)]))
                               wine)
               result (ai/suggest-drinking-window provider enriched-wine)]
           (response/response result)))
       (catch clojure.lang.ExceptionInfo e
         (let [data (ex-data e)
               status (or (:status data) 500)
               error-message (or (:error data) "AI analysis failed")]
           {:status status
            :body (cond-> {:error error-message}
                    (not= (:error data) (.getMessage e)) (assoc :details
                                                                (.getMessage e))
                    (:response data) (assoc :response (:response data)))}))
       (catch Exception e (server-error e))))

(defn generate-wine-summary
  [{{{:keys [wine provider]} :body} :parameters}]
  (try (if (nil? wine)
         {:status 400 :body {:error "Wine details are required"}}
         (let [enriched-wine (if (:id wine)
                               (first (db-api/get-enriched-wines-by-ids
                                       [(:id wine)]))
                               wine)
               result (ai/generate-wine-summary provider enriched-wine)]
           (response/response result)))
       (catch clojure.lang.ExceptionInfo e
         (let [data (ex-data e)
               status (or (:status data) 500)
               error-message (or (:error data) "AI summary generation failed")]
           {:status status
            :body (cond-> {:error error-message}
                    (not= (:error data) (.getMessage e)) (assoc :details
                                                                (.getMessage e))
                    (:response data) (assoc :response (:response data)))}))
       (catch Exception e (server-error e))))

(defn health-check
  [_]
  (try
    ;; Try to connect to the database
    (db-api/ping-db)
    (response/response {:status "healthy"
                        :database "connected"
                        :timestamp (str (java.time.Instant/now))})
    (catch Exception e (server-error e))))

;; Chat Handlers

(defn chat-with-ai
  [request]
  (try
    (let [body (-> request
                   :parameters
                   :body)
          {:keys [wine-ids conversation-history image provider
                  include-visible-wines?]}
          body
          include? (if (contains? body :include-visible-wines?)
                     (boolean include-visible-wines?)
                     false)
          selected-ids (if include? (vec (remove nil? wine-ids)) [])]
      (if (and (empty? conversation-history) (empty? image))
        {:status 400 :body {:error "Conversation history or image is required"}}
        (let [enriched-wines (if (seq selected-ids)
                               (db-api/get-enriched-wines-by-ids selected-ids)
                               [])
              cellar-wines (or (db-api/get-wines-for-list) [])
              condensed (summary/condensed-summary cellar-wines)
              context {:summary condensed
                       :selected-wines (if include? (vec enriched-wines) [])}
              response
              (ai/chat-about-wines provider context conversation-history image)]
          (response/response response))))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)
            status (or (:status data) 500)
            error-message (or (:error data) "AI chat failed")]
        {:status status
         :body (cond-> {:error error-message}
                 (not= (:error data) (.getMessage e)) (assoc :details
                                                             (.getMessage e))
                 (:response data) (assoc :response (:response data)))}))
    (catch Exception e (server-error e))))

;; Conversation persistence handlers

(defn- ensure-user-email
  [request]
  (if-let [email (get-in request [:user :email])]
    email
    (throw (ex-info "Authenticated user email required" {:status 401}))))

(defn list-conversations
  [request]
  (try (let [email (ensure-user-email request)]
         (response/response (db-api/list-conversations-for-user email)))
       (catch Exception e
         (if-let [status (:status (ex-data e))]
           {:status status :body {:error (.getMessage e)}}
           (server-error e)))))

(defn create-conversation
  [request]
  (try (let [email (ensure-user-email request)
             {:keys [title wine_ids wine_search_state auto_tags pinned
                     provider]}
             (get-in request [:parameters :body])
             payload (cond-> {:user_email email
                              :title title
                              :wine_ids wine_ids
                              :wine_search_state wine_search_state
                              :auto_tags auto_tags
                              :provider provider}
                       (some? pinned) (assoc :pinned pinned))
             conversation (db-api/create-conversation! payload)]
         (-> (response/response conversation)
             (response/status 201)))
       (catch Exception e
         (if-let [status (:status (ex-data e))]
           {:status status :body {:error (.getMessage e)}}
           (server-error e)))))

(defn list-conversation-messages
  [request]
  (try (let [email (ensure-user-email request)
             conversation-id (get-in request [:parameters :path :id])
             conversation (db-api/get-conversation conversation-id)]
         (cond (nil? conversation) (response/not-found
                                    {:error "Conversation not found"})
               (not= email (:user_email conversation))
               {:status 403 :body {:error "Forbidden"}}
               :else (response/response (db-api/list-messages-for-conversation
                                         conversation-id))))
       (catch Exception e
         (if-let [status (:status (ex-data e))]
           {:status status :body {:error (.getMessage e)}}
           (server-error e)))))

(defn append-conversation-message
  [request]
  (try
    (let [email (ensure-user-email request)
          conversation-id (get-in request [:parameters :path :id])
          conversation (db-api/get-conversation conversation-id)]
      (cond (nil? conversation) (response/not-found {:error
                                                     "Conversation not found"})
            (not= email (:user_email conversation)) {:status 403
                                                     :body {:error "Forbidden"}}
            :else (let [{:keys [is_user content image_data tokens_used]} ; body
                                                                         ; map
                                                                         ; used
                                                                         ; individual
                                                                         ; keys
                                                                         ; only
                        (get-in request [:parameters :body])
                        message {:conversation_id conversation-id
                                 :is_user (boolean is_user)
                                 :content content
                                 :image_data image_data
                                 :tokens_used tokens_used}
                        inserted (db-api/append-conversation-message! message)
                        title-needed? (and (:is_user inserted)
                                           (str/blank? (:title conversation))
                                           (not (str/blank? content)))
                        updated-conversation
                        (when title-needed?
                          (when-let [title (ai/generate-conversation-title
                                            (:provider conversation)
                                            content)]
                            (db-api/update-conversation! conversation-id
                                                         {:title title})))]
                    (-> (response/response (cond-> {:message inserted}
                                             updated-conversation
                                             (assoc :conversation
                                                    updated-conversation)))
                        (response/status 201)))))
    (catch Exception e
      (if-let [status (:status (ex-data e))]
        {:status status :body {:error (.getMessage e)}}
        (server-error e)))))

(defn update-conversation-message
  [request]
  (try (let [email (ensure-user-email request)
             conversation-id (get-in request [:parameters :path :id])
             message-id (get-in request [:parameters :path :message-id])
             conversation (db-api/get-conversation conversation-id)]
         (cond (nil? conversation) (response/not-found
                                    {:error "Conversation not found"})
               (not= email (:user_email conversation))
               {:status 403 :body {:error "Forbidden"}}
               :else
               (let [body (get-in request [:parameters :body])
                     {:keys [content image truncate_after? tokens_used]} body
                     payload (cond-> {:conversation_id conversation-id
                                      :message_id message-id
                                      :content content}
                               (contains? body :image) (assoc :image_data image)
                               (contains? body :tokens_used) (assoc :tokens_used
                                                                    tokens_used)
                               (true? truncate_after?) (assoc :truncate_after?
                                                              true))]
                 (-> (response/response (db-api/update-conversation-message!
                                         payload))
                     (response/status 200)))))
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [status]} (ex-data e)]
           (if status
             {:status status :body {:error (.getMessage e)}}
             (server-error e))))
       (catch Exception e
         (if-let [status (:status (ex-data e))]
           {:status status :body {:error (.getMessage e)}}
           (server-error e)))))

(defn delete-conversation
  [request]
  (try (let [email (ensure-user-email request)
             conversation-id (get-in request [:parameters :path :id])
             conversation (db-api/get-conversation conversation-id)]
         (cond (nil? conversation) (response/not-found
                                    {:error "Conversation not found"})
               (not= email (:user_email conversation))
               {:status 403 :body {:error "Forbidden"}}
               :else (do (db-api/delete-conversation! conversation-id)
                         (no-content))))
       (catch Exception e
         (if-let [status (:status (ex-data e))]
           {:status status :body {:error (.getMessage e)}}
           (server-error e)))))

(defn update-conversation
  [request]
  (try (let [email (ensure-user-email request)
             conversation-id (get-in request [:parameters :path :id])
             conversation (db-api/get-conversation conversation-id)]
         (cond (nil? conversation) (response/not-found
                                    {:error "Conversation not found"})
               (not= email (:user_email conversation))
               {:status 403 :body {:error "Forbidden"}}
               :else (let [updates (get-in request [:parameters :body])]
                       (if (seq updates)
                         (response/response
                          (db-api/update-conversation! conversation-id updates))
                         {:status 400 :body {:error "No updates provided"}}))))
       (catch Exception e
         (if-let [status (:status (ex-data e))]
           {:status status :body {:error (.getMessage e)}}
           (server-error e)))))

;; Admin Handlers

(defn get-model-info
  "Get current AI model configuration for all providers"
  [_request]
  (response/response (wine-cellar.ai.core/get-model-info)))

(defn reset-database
  "Admin function to drop and recreate all database tables"
  [_request]
  (try (println "🔥 ADMIN: Dropping all database tables...")
       (db-setup/drop-tables)
       (println "🛠️  ADMIN: Recreating database schema...")
       (db-setup/initialize-db false) ; Skip classification seeding for
                                      ; imports
       (println "✅ ADMIN: Database reset complete!")
       {:status 200
        :body {:message "Database reset successfully"
               :tables-dropped true
               :schema-recreated true
               :classifications-seeded false}}
       (catch Exception e
         (println "❌ ADMIN: Database reset failed:" (.getMessage e))
         (server-error e))))

(defn mark-all-wines-unverified
  "Admin function to mark all wines as unverified for inventory verification"
  [_]
  (try (println "🔄 ADMIN: Marking all wines as unverified...")
       (let [updated-count (db-api/mark-all-wines-unverified)]
         (println "✅ ADMIN: Marked" updated-count "wines as unverified")
         {:status 200
          :body {:message "All wines marked as unverified"
                 :wines-updated updated-count}})
       (catch Exception e
         (println "❌ ADMIN: Failed to mark wines as unverified:"
                  (.getMessage e))
         (server-error e))))

(defn get-verbose-logging-state
  [_]
  (response/response (logging/verbose-logging-status)))

(defn set-verbose-logging-state
  [{{{:keys [enabled?]} :body} :parameters}]
  (if (nil? enabled?)
    {:status 400 :body {:error "enabled? flag is required"}}
    (do (logging/set-verbose-logging! enabled?)
        (response/response (logging/verbose-logging-status)))))

(defn start-drinking-window-job
  "Admin function to start async drinking window regeneration job"
  [request]
  (try (let [body (get-in request [:parameters :body])
             wine-ids (:wine-ids body)
             provider (:provider body)
             wine-count (count wine-ids)]
         (println "🔄 ADMIN: Starting drinking window job for"
                  wine-count
                  "wines...")
         (if (empty? wine-ids)
           {:status 400 :body {:error "No wine IDs provided"}}
           (let [job-id
                 (wine-cellar.admin.bulk-operations/start-drinking-window-job
                  {:wine-ids wine-ids :provider provider})]
             (println "✅ ADMIN: Started drinking window job" job-id)
             {:status 200
              :body {:job-id job-id
                     :message "Drinking window regeneration job started"
                     :total-wines wine-count}})))
       (catch Exception e
         (println "❌ ADMIN: Failed to start drinking window job:"
                  (.getMessage e))
         (server-error e))))

(defn start-wine-summary-job
  "Admin function to start async wine summary regeneration job"
  [request]
  (try
    (let [body (get-in request [:parameters :body])
          wine-ids (:wine-ids body)
          provider (:provider body)
          wine-count (count wine-ids)]
      (println "🔄 ADMIN: Starting wine summary job for" wine-count "wines...")
      (if (empty? wine-ids)
        {:status 400 :body {:error "No wine IDs provided"}}
        (let [job-id (wine-cellar.admin.bulk-operations/start-wine-summary-job
                      {:wine-ids wine-ids :provider provider})]
          (println "✅ ADMIN: Started wine summary job" job-id)
          {:status 200
           :body {:job-id job-id
                  :message "Wine summary regeneration job started"
                  :total-wines wine-count}})))
    (catch Exception e
      (println "❌ ADMIN: Failed to start wine summary job:" (.getMessage e))
      (server-error e))))

(defn get-job-status
  "Get status of an async job"
  [request]
  (try (let [job-id (get-in request [:parameters :path :job-id])
             status (wine-cellar.admin.bulk-operations/get-job-status job-id)]
         (if status
           {:status 200 :body status}
           {:status 404 :body {:error "Job not found"}}))
       (catch Exception e
         (println "❌ ADMIN: Failed to get job status:" (.getMessage e))
         (server-error e))))
