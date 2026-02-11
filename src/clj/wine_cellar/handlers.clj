(ns wine-cellar.handlers
  (:require [clojure.string :as str]
            [wine-cellar.db.api :as db-api]
            [wine-cellar.ai.core :as ai]
            [wine-cellar.db.setup :as db-setup]
            [wine-cellar.admin.bulk-operations]
            [wine-cellar.devices :as devices]
            [wine-cellar.reports.core :as reports]
            [ring.util.response :as response]
            [wine-cellar.summary :as summary]
            [wine-cellar.logging :as logging])
  (:import [java.time Instant]))

(defn- no-content [] {:status 204 :headers {} :body nil})

(defn- server-error
  [e]
  (tap> e)
  {:status 500
   :headers {}
   :body {:error "Internal server error" :details (.getMessage e)}})

(def cellar-measurement-keys
  [:temperatures :humidity_pct :pressure_hpa :illuminance_lux :co2_ppm
   :battery_mv :leak_detected])

(defn- measurement-present?
  [payload]
  (some #(contains? payload %) cellar-measurement-keys))

(def retry-after-seconds 30)

(defn- device-token? [user] (= "device" (:type user)))

(defn- device-id-from-token
  [request]
  (let [user (:user request)] (when (device-token? user) (:device_id user))))

(defn- touch-device!
  "Mark device as seen and optionally update token expiry from JWT exp."
  [device-id request]
  (let [exp-ms (get-in request [:user :exp])
        exp (when exp-ms (Instant/ofEpochMilli exp-ms))]
    (when device-id (db-api/touch-device! device-id {:token_expires_at exp}))))

(defn- handle-ai-error
  [e]
  (let [data (ex-data e)
        status (or (:status data) 500)
        error-message (or (:error data) (.getMessage e) "AI analysis failed")]
    {:status status
     :body (cond-> {:error error-message}
             (and (not= (:error data) (.getMessage e))
                  (not= error-message (.getMessage e)))
             (assoc :details (.getMessage e))
             (:response data) (assoc :response (:response data)))}))

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

(defn get-appellations-by-region
  [{{{:keys [country region]} :path} :parameters}]
  (try (let [appellations (db-api/get-appellations-by-region country region)]
         (response/response appellations))
       (catch Exception e (server-error e))))

(defn get-wines-for-list
  [_]
  (try (let [wines (db-api/get-wines-for-list)] (response/response wines))
       (catch Exception e (server-error e))))

(defn get-technical-data-keys
  [_]
  (try (let [keys (db-api/get-all-metadata-keys)] (response/response keys))
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
                               :appellation (:appellation wine)
                               :appellation_tier (:appellation_tier wine)
                               :classification (:classification wine)
                               :designations (when (:designation wine)
                                               [(:designation wine)])}]
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
  [{{{:keys [id]} :path {:keys [adjustment reason notes]} :body} :parameters}]
  (try (if (db-api/wine-exists? id)
         (let [updated (db-api/adjust-quantity id
                                               adjustment
                                               {:reason reason :notes notes})]
           (response/response updated))
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

(defn get-inventory-history
  [{{{:keys [id]} :path} :parameters}]
  (try (if (db-api/wine-exists? id)
         (let [history (db-api/get-inventory-history id)]
           (response/response history))
         (response/not-found {:error "Wine not found"}))
       (catch Exception e (server-error e))))

(defn update-inventory-history
  [{{{:keys [history-id]} :path body :body} :parameters}]
  (try (if-let [updated (db-api/update-inventory-history! history-id body)]
         (response/response updated)
         (response/not-found {:error "History record not found"}))
       (catch Exception e (server-error e))))

(defn delete-inventory-history
  [{{{:keys [history-id]} :path} :parameters}]
  (try (if (db-api/delete-inventory-history! history-id)
         (no-content)
         (response/not-found {:error "History record not found"}))
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

;; Blind Tasting Handlers
(defn get-blind-tastings
  [_]
  (try (let [blind-tastings (db-api/get-blind-tastings)]
         (response/response blind-tastings))
       (catch Exception e (server-error e))))

(defn create-blind-tasting
  [{{{:keys [notes rating tasting_date wset_data]} :body} :parameters}]
  (try (let [note {:notes notes
                   :rating rating
                   :tasting_date tasting_date
                   :wset_data wset_data}
             created (db-api/create-blind-tasting note)]
         {:status 201 :body created})
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid blind tasting data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn link-blind-tasting
  [{{{:keys [id]} :path {:keys [wine_id]} :body} :parameters}]
  (try (cond (nil? wine_id) {:status 400 :body {:error "wine_id is required"}}
             (not (db-api/wine-exists? wine_id))
             {:status 404 :body {:error "Wine not found"}}
             :else (if-let [updated (db-api/link-blind-tasting id wine_id)]
                     (response/response updated)
                     {:status 404
                      :body {:error
                             "Blind tasting not found or already linked"}}))
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
         (let [classifications (db-api/get-classifications)
               result (ai/analyze-wine-label provider
                                             label_image
                                             back_label_image
                                             classifications)]
           (response/response result)))
       (catch clojure.lang.ExceptionInfo e (handle-ai-error e))
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
       (catch clojure.lang.ExceptionInfo e (handle-ai-error e))
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
       (catch clojure.lang.ExceptionInfo e (handle-ai-error e))
       (catch Exception e (server-error e))))

(defn get-latest-report
  [request]
  (let [force? (get-in request [:query-params "force"])
        provider (some-> (get-in request [:query-params "provider"])
                         keyword)]
    (try (let [report (reports/generate-report! {:force? (boolean force?)
                                                 :provider provider})]
           (response/response report))
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
    (catch clojure.lang.ExceptionInfo e (handle-ai-error e))
    (catch Exception e (server-error e))))

;; Conversation persistence handlers

(defn- ensure-user-email
  [request]
  (if-let [email (get-in request [:user :email])]
    email
    (throw (ex-info "Authenticated user email required" {:status 401}))))

(defn list-conversations
  [request]
  (try (let [email (ensure-user-email request)
             search-text (get-in request [:parameters :query :search-text])]
         (response/response (db-api/list-conversations-for-user email
                                                                search-text)))
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

(defn get-db-schema
  "Admin function to get database schema"
  [_]
  (try (let [schema (db-api/get-db-schema)] {:status 200 :body schema})
       (catch Exception e
         (tap> ["❌ ADMIN: Failed to get schema:" (.getMessage e)])
         (server-error e))))

(defn execute-sql-query
  "Admin function to execute raw SQL queries"
  [{{{:keys [query]} :body} :parameters}]
  (try (tap> ["⚡ ADMIN: Executing raw SQL query:" query])
       (let [result (db-api/execute-sql-query query)]
         {:status 200 :body result})
       (catch Exception e
         (tap> ["❌ ADMIN: SQL execution failed:" (.getMessage e)])
         {:status 400
          :body {:error "SQL execution failed" :details (.getMessage e)}})))

(defn reset-database
  "Admin function to drop and recreate all database tables"
  [_request]
  (try (tap> "🔥 ADMIN: Dropping all database tables...")
       (db-setup/drop-tables)
       (tap> "🛠️  ADMIN: Recreating database schema...")
       (db-setup/initialize-db false) ; Skip classification seeding for
                                      ; imports
       (tap> "✅ ADMIN: Database reset complete!")
       {:status 200
        :body {:message "Database reset successfully"
               :tables-dropped true
               :schema-recreated true
               :classifications-seeded false}}
       (catch Exception e
         (tap> ["❌ ADMIN: Database reset failed:" (.getMessage e)])
         (server-error e))))

(defn mark-all-wines-unverified
  "Admin function to mark all wines as unverified for inventory verification"
  [_]
  (try (tap> "🔄 ADMIN: Marking all wines as unverified...")
       (let [updated-count (db-api/mark-all-wines-unverified)]
         (tap> ["✅ ADMIN: Marked" updated-count "wines as unverified"])
         {:status 200
          :body {:message "All wines marked as unverified"
                 :wines-updated updated-count}})
       (catch Exception e
         (tap> ["❌ ADMIN: Failed to mark wines as unverified:" (.getMessage e)])
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

;; Device provisioning handlers
(defn claim-device
  [request]
  (let [{:keys [device_id claim_code firmware_version capabilities]}
        (get-in request [:parameters :body])]
    (try (let [device (devices/claim! {:device_id device_id
                                       :claim_code claim_code
                                       :firmware_version firmware_version
                                       :capabilities capabilities})]
           (if (= "blocked" (:status device))
             {:status 403 :body {:error "Device is blocked"}}
             {:status 202
              :body {:status (:status device)
                     :device_id device_id
                     :retry_after_seconds retry-after-seconds}}))
         (catch Exception e (server-error e)))))

(defn poll-device-claim
  [request]
  (let [{:keys [device_id claim_code]} (get-in request [:parameters :body])
        device (db-api/get-device device_id)
        claim-hash (devices/hash-string claim_code)]
    (cond (nil? device) (response/not-found {:error "Device not found"})
          (not= claim-hash (:claim_code_hash device))
          {:status 401 :body {:error "Invalid claim code"}}
          (= "blocked" (:status device)) {:status 403
                                          :body {:error "Device is blocked"}}
          (= "pending" (:status device))
          {:status 200
           :body {:status "pending" :retry_after_seconds retry-after-seconds}}
          :else (try (let [{:keys [tokens]} (devices/issue-and-store-token-pair!
                                             device_id)]
                       {:status 200
                        :body (merge {:status "approved" :device_id device_id}
                                     tokens)})
                     (catch Exception e (server-error e))))))

(defn refresh-device-token
  [request]
  (let [{:keys [device_id refresh_token]} (get-in request [:parameters :body])
        device (db-api/get-device device_id)]
    (cond (nil? device) (response/not-found {:error "Device not found"})
          (not= "active" (:status device))
          {:status 403 :body {:error "Device is not active"}}
          :else (try (if-let [{:keys [tokens]} (devices/refresh-with-token!
                                                device
                                                refresh_token)]
                       {:status 200 :body (merge {:device_id device_id} tokens)}
                       {:status 401 :body {:error "Invalid refresh token"}})
                     (catch Exception e (server-error e))))))

(defn list-devices-admin
  [_]
  (try (->> (db-api/list-devices)
            (map devices/public-device-view)
            vec
            response/response)
       (catch Exception e (server-error e))))

(defn approve-device
  [{{{:keys [device_id]} :path {:keys [claim_code]} :body} :parameters}]
  (try (if-let [device (db-api/get-device device_id)]
         (let [matches? (= (:claim_code_hash device)
                           (devices/hash-string claim_code))]
           (cond (not matches?) {:status 422
                                 :body {:error "Invalid claim code"}}
                 (= "blocked" (:status device))
                 {:status 403 :body {:error "Device is blocked"}}
                 :else
                 (let [updated (db-api/update-device! device_id
                                                      {:status "active"
                                                       :refresh_token_hash nil
                                                       :token_expires_at nil})]
                   {:status 200 :body (devices/public-device-view updated)})))
         (response/not-found {:error "Device not found"}))
       (catch Exception e (server-error e))))

(defn block-device
  [{{{:keys [device_id]} :path} :parameters}]
  (try (if-let [updated (db-api/block-device! device_id)]
         {:status 200 :body (devices/public-device-view updated)}
         (response/not-found {:error "Device not found"}))
       (catch Exception e (server-error e))))

(defn unblock-device
  [{{{:keys [device_id]} :path} :parameters}]
  (try (if-let [updated (db-api/unblock-device! device_id)]
         {:status 200 :body (devices/public-device-view updated)}
         (response/not-found {:error "Device not found"}))
       (catch Exception e (server-error e))))

(defn delete-device
  [{{{:keys [device_id]} :path} :parameters}]
  (try (let [deleted (db-api/delete-device! device_id)]
         (if (pos? (:next.jdbc/update-count deleted 0))
           {:status 204 :body nil}
           (response/not-found {:error "Device not found"})))
       (catch Exception e (server-error e))))

(defn update-device-sensor-config
  [{{{:keys [device_id]} :path body :body} :parameters}]
  (try (if-let [updated (db-api/update-device! device_id {:sensor_config body})]
         (response/response (devices/public-device-view updated))
         (response/not-found {:error "Device not found"}))
       (catch Exception e (server-error e))))

(defn- merge-sensor-config!
  "Auto-populate sensor_config on the device with any new sensor addresses
  discovered in the temperatures payload. Existing labels are preserved."
  [device-id temperatures]
  (when (and device-id (map? temperatures) (seq temperatures))
    (try (let [device (db-api/get-device device-id)
               existing (or (:sensor_config device) {})
               new-keys (remove #(contains? existing (keyword %))
                                (keys temperatures))
               merged (reduce (fn [cfg k] (assoc cfg (keyword k) {}))
                              existing
                              new-keys)]
           (when (seq new-keys)
             (db-api/update-device! device-id {:sensor_config merged})))
         (catch Exception _ nil))))

(defn ingest-cellar-condition
  [request]
  (let [payload (get-in request [:parameters :body])
        token-device-id (device-id-from-token request)
        payload-device-id (:device_id payload)]
    (cond (not (measurement-present? payload))
          {:status 400
           :body {:error "At least one measurement value is required"}}
          (and token-device-id (not= token-device-id payload-device-id))
          {:status 403
           :body {:error "device_id does not match the authenticated device"}}
          :else
          (try
            (let [device-status
                  (when token-device-id
                    (let [device (db-api/get-device token-device-id)]
                      (cond (nil? device) {:status 404
                                           :body {:error
                                                  "Device is not registered"}}
                            (not= "active" (:status device))
                            {:status 403 :body {:error "Device is not active"}}
                            :else (do (touch-device! token-device-id request)
                                      nil))))
                  recorded-by (or token-device-id
                                  (get-in request [:user :email])
                                  (get-in request [:user :sub]))]
              (if device-status
                device-status
                (let [record (db-api/create-cellar-condition!
                              (cond-> payload
                                recorded-by (assoc :recorded_by recorded-by)))]
                  (merge-sensor-config! (:device_id payload)
                                        (:temperatures payload))
                  {:status 201 :body record})))
            (catch Exception e (server-error e))))))

(defn list-cellar-conditions
  [request]
  (let [{:keys [device_id limit]} (or (get-in request [:parameters :query]) {})
        limit (or limit 100)]
    (try (-> {:device_id device_id :limit limit}
             (db-api/list-cellar-conditions)
             response/response)
         (catch Exception e (server-error e)))))

(defn latest-cellar-conditions
  [request]
  (let [{:keys [device_id]} (or (get-in request [:parameters :query]) {})]
    (try (-> (db-api/latest-cellar-conditions device_id)
             response/response)
         (catch Exception e (server-error e)))))

(defn cellar-condition-series
  [request]
  (let [{:keys [device_id bucket from to]}
        (or (get-in request [:parameters :query]) {})]
    (try (-> {:device_id device_id :bucket bucket :from from :to to}
             (db-api/cellar-condition-series)
             response/response)
         (catch Exception e (server-error e)))))

(defn start-drinking-window-job
  "Admin function to start async drinking window regeneration job"
  [request]
  (try (let [body (get-in request [:parameters :body])
             wine-ids (:wine-ids body)
             provider (:provider body)
             wine-count (count wine-ids)]
         (tap> ["🔄 ADMIN: Starting drinking window job for" wine-count
                "wines..."])
         (if (empty? wine-ids)
           {:status 400 :body {:error "No wine IDs provided"}}
           (let [job-id
                 (wine-cellar.admin.bulk-operations/start-drinking-window-job
                  {:wine-ids wine-ids :provider provider})]
             (tap> ["✅ ADMIN: Started drinking window job" job-id])
             {:status 200
              :body {:job-id job-id
                     :message "Drinking window regeneration job started"
                     :total-wines wine-count}})))
       (catch Exception e
         (tap> ["❌ ADMIN: Failed to start drinking window job:"
                (.getMessage e)])
         (server-error e))))

(defn start-wine-summary-job
  "Admin function to start async wine summary regeneration job"
  [request]
  (try
    (let [body (get-in request [:parameters :body])
          wine-ids (:wine-ids body)
          provider (:provider body)
          wine-count (count wine-ids)]
      (tap> ["🔄 ADMIN: Starting wine summary job for" wine-count "wines..."])
      (if (empty? wine-ids)
        {:status 400 :body {:error "No wine IDs provided"}}
        (let [job-id (wine-cellar.admin.bulk-operations/start-wine-summary-job
                      {:wine-ids wine-ids :provider provider})]
          (tap> ["✅ ADMIN: Started wine summary job" job-id])
          {:status 200
           :body {:job-id job-id
                  :message "Wine summary regeneration job started"
                  :total-wines wine-count}})))
    (catch Exception e
      (tap> ["❌ ADMIN: Failed to start wine summary job:" (.getMessage e)])
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
         (tap> ["❌ ADMIN: Failed to get job status:" (.getMessage e)])
         (server-error e))))
