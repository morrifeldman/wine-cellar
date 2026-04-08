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
            [wine-cellar.logging :as logging]
            [wine-cellar.utils.web-fetch :as web-fetch])
  (:import [java.time Instant]))

(defn- no-content [] {:status 204 :headers {} :body nil})

(defn- server-error
  [e]
  (tap> e)
  {:status 500
   :headers {}
   :body {:error "Internal server error" :details (.getMessage e)}})

(defmacro ^:private with-server-error
  "Run `body` and translate any thrown Exception to a 500 ring response."
  [& body]
  `(try ~@body (catch Exception e# (server-error e#))))

(declare handle-ai-error)

(defmacro ^:private with-ai-error
  "Like `with-server-error` but routes ExceptionInfo through the AI handler."
  [& body]
  `(try ~@body
        (catch clojure.lang.ExceptionInfo e# (handle-ai-error e#))
        (catch Exception e# (server-error e#))))

(defn- not-found
  [resource]
  (response/not-found {:error (str resource " not found")}))

(defn- if-wine
  "If the wine identified by `id` exists, run `f` and return its ring response;
  otherwise return a 404. Wraps the body in `with-server-error`."
  [id f]
  (with-server-error (if (db-api/wine-exists? id) (f) (not-found "Wine"))))

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
  (with-server-error (if-let [classification (db-api/get-classification id)]
                       (response/response classification)
                       (not-found "Classification"))))

(defn get-classifications
  [_]
  (with-server-error (response/response (db-api/get-classifications))))

(defn update-classification
  [{{{:keys [id]} :path body :body} :parameters}]
  (try (if-let [updated (db-api/update-classification! id body)]
         (response/response updated)
         (not-found "Classification"))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid classification data"
                 :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-classification
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (if (db-api/delete-classification! id)
                       (no-content)
                       (not-found "Classification"))))

(defn get-regions-by-country
  [{{{:keys [country]} :path} :parameters}]
  (with-server-error (response/response (db-api/get-regions-by-country
                                         country))))

(defn get-appellations-by-region
  [{{{:keys [country region]} :path} :parameters}]
  (with-server-error (response/response
                      (db-api/get-appellations-by-region country region))))

(defn get-wines-for-list
  [_]
  (with-server-error (response/response (db-api/get-wines-for-list))))

(defn get-technical-data-keys
  [_]
  (with-server-error (response/response (db-api/get-all-metadata-keys))))

(defn get-wine
  [{{{:keys [id]} :path {:keys [include_images]} :query} :parameters}]
  (with-server-error (if-let [wine (db-api/get-wine id include_images)]
                       (response/response wine)
                       (not-found "Wine"))))

(defn create-wine
  [request]
  (let [wine (-> request
                 :parameters
                 :body)]
    (try (let [classification {:country (:country wine)
                               :region (:region wine)
                               :appellation (:appellation wine)
                               :appellation_tier (:appellation_tier wine)
                               :classification (:classification wine)}]
           ;; Only create if all required fields are present
           (when (and (:country classification) (:region classification))
             (db-api/create-or-update-classification classification)))
         (let [created-wine (db-api/create-wine wine)]
           {:status 201 :body created-wine})
         (catch Exception e (server-error e)))))

(defn update-wine
  [{{{:keys [id]} :path body :body} :parameters}]
  (try (if (db-api/wine-exists? id)
         (response/response (db-api/update-wine! id body))
         (not-found "Wine"))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid wine data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-wine
  [{{{:keys [id]} :path} :parameters}]
  (if-wine id #(do (db-api/delete-wine! id) (no-content))))

(defn adjust-quantity
  [{{{:keys [id]} :path {:keys [adjustment reason notes]} :body} :parameters}]
  (if-wine
   id
   #(response/response
     (db-api/adjust-quantity id adjustment {:reason reason :notes notes}))))

(defn get-inventory-history
  [{{{:keys [id]} :path} :parameters}]
  (if-wine id #(response/response (db-api/get-inventory-history id))))

(defn update-inventory-history
  [{{{:keys [history-id]} :path body :body} :parameters}]
  (with-server-error
   (if-let [updated (db-api/update-inventory-history! history-id body)]
     (response/response updated)
     (not-found "History record"))))

(defn delete-inventory-history
  [{{{:keys [history-id]} :path} :parameters}]
  (with-server-error (if (db-api/delete-inventory-history! history-id)
                       (no-content)
                       (not-found "History record"))))

;; Tasting Notes Handlers
(defn get-tasting-notes-by-wine
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (response/response (db-api/get-tasting-notes-by-wine id))))

(defn get-tasting-note
  [{{{:keys [note-id]} :path} :parameters}]
  (with-server-error (if-let [note (db-api/get-tasting-note note-id)]
                       (response/response note)
                       (not-found "Tasting note"))))

(defn create-tasting-note
  [{{{:keys [id]} :path body :body} :parameters}]
  (try (if (db-api/wine-exists? id)
         {:status 201
          :body (db-api/create-tasting-note (assoc body :wine_id id))}
         (not-found "Wine"))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid tasting note data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn update-tasting-note
  [{{{:keys [note-id]} :path body :body} :parameters}]
  (try (if (db-api/get-tasting-note note-id)
         (response/response (db-api/update-tasting-note! note-id body))
         (not-found "Tasting note"))
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid tasting note data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn delete-tasting-note
  [{{{:keys [note-id]} :path} :parameters}]
  (with-server-error (if (db-api/get-tasting-note note-id)
                       (do (db-api/delete-tasting-note! note-id) (no-content))
                       (not-found "Tasting note"))))

(defn get-tasting-note-sources
  [_]
  (with-server-error (response/response (db-api/get-tasting-note-sources))))

;; Blind Tasting Handlers
(defn get-blind-tastings
  [_]
  (with-server-error (response/response (db-api/get-blind-tastings))))

(defn create-blind-tasting
  [{{{:keys [notes rating tasting_date wset_data]} :body} :parameters}]
  (try {:status 201
        :body (db-api/create-blind-tasting {:notes notes
                                            :rating rating
                                            :tasting_date tasting_date
                                            :wset_data wset_data})}
       (catch org.postgresql.util.PSQLException e
         {:status 400
          :body {:error "Invalid blind tasting data" :details (.getMessage e)}})
       (catch Exception e (server-error e))))

(defn link-blind-tasting
  [{{{:keys [id]} :path {:keys [wine_id]} :body} :parameters}]
  (with-server-error
   (cond (nil? wine_id) {:status 400 :body {:error "wine_id is required"}}
         (not (db-api/wine-exists? wine_id)) (not-found "Wine")
         :else (if-let [updated (db-api/link-blind-tasting id wine_id)]
                 (response/response updated)
                 (not-found "Blind tasting (or already linked)")))))

;; Grape Varieties Handlers
(defn get-grape-varieties
  [_]
  (with-server-error {:status 200 :body (db-api/get-all-grape-varieties)}))

(defn create-grape-variety
  [{{{:keys [variety_name]} :body} :parameters}]
  (with-server-error {:status 201
                      :body (db-api/create-grape-variety variety_name)}))

(defn get-grape-variety
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (if-let [variety (db-api/get-grape-variety id)]
                       {:status 200 :body variety}
                       (not-found "Grape variety"))))

(defn update-grape-variety
  [{{{:keys [id]} :path {:keys [variety_name]} :body} :parameters}]
  (with-server-error (if-let [variety
                              (db-api/update-grape-variety! id variety_name)]
                       {:status 200 :body variety}
                       (not-found "Grape variety"))))

(defn delete-grape-variety
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (db-api/delete-grape-variety! id) (no-content)))

;; Wine Varieties Handlers
(defn- if-wine-and-variety
  [wine-id variety-id f]
  (if-wine
   wine-id
   #(if (db-api/get-grape-variety variety-id) (f) (not-found "Grape variety"))))

(defn get-wine-varieties
  [{{{:keys [id]} :path} :parameters}]
  (if-wine id #(response/response (db-api/get-wine-grape-varieties id))))

(defn add-variety-to-wine
  [{{{:keys [id]} :path {:keys [variety_id percentage]} :body} :parameters}]
  (tap> ["add-variety-to-wine"
         {:wine-id id :variety-id variety_id :percentage percentage}])
  (if-wine-and-variety
   id
   variety_id
   #(hash-map :status 201
              :body (db-api/associate-grape-variety-with-wine id
                                                              variety_id
                                                              percentage))))

(defn update-wine-variety-percentage
  [{{{:keys [id variety-id]} :path {:keys [percentage]} :body} :parameters}]
  (if-wine-and-variety
   id
   variety-id
   #(response/response
     (db-api/associate-grape-variety-with-wine id variety-id percentage))))

(defn remove-variety-from-wine
  [{{{:keys [id variety-id]} :path} :parameters}]
  (if-wine-and-variety
   id
   variety-id
   #(do (db-api/remove-grape-variety-from-wine id variety-id) (no-content))))

;; AI Analysis Handlers
(defn analyze-wine-label
  [{{{:keys [label_image back_label_image provider]} :body} :parameters}]
  (with-ai-error (if (nil? label_image)
                   {:status 400 :body {:error "Label image is required"}}
                   (response/response (ai/analyze-wine-label
                                       provider
                                       label_image
                                       back_label_image
                                       (db-api/get-classifications))))))

(defn analyze-spirit-label
  [{{{:keys [label_image provider]} :body} :parameters}]
  (with-ai-error (if (nil? label_image)
                   {:status 400 :body {:error "Label image is required"}}
                   (response/response (ai/analyze-spirit-label provider
                                                               label_image)))))

(defn- enrich-wine
  [wine]
  (if (:id wine) (first (db-api/get-enriched-wines-by-ids [(:id wine)])) wine))

(defn suggest-drinking-window
  [{{{:keys [wine provider]} :body} :parameters}]
  (with-ai-error (if (nil? wine)
                   {:status 400 :body {:error "Wine details are required"}}
                   (response/response
                    (ai/suggest-drinking-window provider (enrich-wine wine))))))

(defn generate-wine-summary
  [{{{:keys [wine provider]} :body} :parameters}]
  (with-ai-error (if (nil? wine)
                   {:status 400 :body {:error "Wine details are required"}}
                   (response/response
                    (ai/generate-wine-summary provider (enrich-wine wine))))))

(defn list-reports
  [_]
  (with-server-error (response/response (reports/list-reports))))

(defn get-report
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (if-let [report (reports/get-report-by-id id)]
                       (response/response report)
                       (not-found "Report"))))

(defn get-latest-report
  [request]
  (let [force? (get-in request [:query-params "force"])]
    (with-server-error (response/response (reports/generate-report!
                                           {:force? (boolean force?)})))))

(defn health-check
  [_]
  (with-server-error (db-api/ping-db)
                     (response/response {:status "healthy"
                                         :database "connected"
                                         :timestamp
                                         (str (java.time.Instant/now))})))

;; Bar Handlers

(defn get-spirits
  [_]
  (with-server-error (response/response (db-api/get-spirits))))

(defn get-spirit
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (if-let [spirit (db-api/get-spirit id)]
                       (response/response spirit)
                       (not-found "Spirit"))))

(defn create-spirit
  [{{body :body} :parameters}]
  (with-server-error {:status 201 :body (db-api/create-spirit! body)}))

(defn update-spirit
  [{{{:keys [id]} :path body :body} :parameters}]
  (with-server-error (if (db-api/get-spirit id)
                       (response/response (db-api/update-spirit! id body))
                       (not-found "Spirit"))))

(defn delete-spirit
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (if (db-api/get-spirit id)
                       (do (db-api/delete-spirit! id) (no-content))
                       (not-found "Spirit"))))

(defn get-bar-inventory
  [_]
  (with-server-error (response/response (db-api/get-bar-inventory-items))))

(defn update-bar-inventory-item
  [{{{:keys [id]} :path body :body} :parameters}]
  (with-server-error (if-let [updated (db-api/update-bar-inventory-item! id
                                                                         body)]
                       (response/response updated)
                       (not-found "Bar inventory item"))))

(defn create-bar-inventory-item
  [{{body :body} :parameters}]
  (with-server-error {:status 201
                      :body (db-api/create-bar-inventory-item! body)}))

(defn delete-bar-inventory-item
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (if (db-api/delete-bar-inventory-item! id)
                       (no-content)
                       (not-found "Bar inventory item"))))

(defn get-cocktail-recipes
  [_]
  (with-server-error (response/response (db-api/get-cocktail-recipes))))

(defn get-cocktail-recipe
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (if-let [recipe (db-api/get-cocktail-recipe id)]
                       (response/response recipe)
                       (not-found "Recipe"))))

(defn create-cocktail-recipe
  [{{body :body} :parameters}]
  (with-server-error {:status 201 :body (db-api/create-cocktail-recipe! body)}))

(defn update-cocktail-recipe
  [{{{:keys [id]} :path body :body} :parameters}]
  (with-server-error (if (db-api/get-cocktail-recipe id)
                       (response/response (db-api/update-cocktail-recipe! id
                                                                          body))
                       (not-found "Recipe"))))

(defn delete-cocktail-recipe
  [{{{:keys [id]} :path} :parameters}]
  (with-server-error (if (db-api/get-cocktail-recipe id)
                       (do (db-api/delete-cocktail-recipe! id) (no-content))
                       (not-found "Recipe"))))

(defn extract-cocktail-recipe
  [request]
  (with-server-error
   (let [message-text (get-in request [:parameters :body :message-text])]
     (if-let [recipe (ai/extract-cocktail-recipe message-text)]
       (response/response recipe)
       {:status 422 :body {:error "Could not extract recipe from text"}}))))

;; Chat Handlers

(defn chat-with-ai
  [request]
  (try
    (let [body (-> request
                   :parameters
                   :body)
          {:keys [wine-ids conversation-history image provider
                  include-visible-wines? include-bar?]}
          body
          include? (if (contains? body :include-visible-wines?)
                     (boolean include-visible-wines?)
                     false)
          include-bar? (boolean include-bar?)
          selected-ids (if include? (vec (remove nil? wine-ids)) [])
          message (or (some #(when (or (:is-user %) (:is_user %))
                               (or (:content %) (:text %)))
                            (reverse conversation-history))
                      "")]
      (if (and (empty? conversation-history) (empty? image))
        {:status 400 :body {:error "Conversation history or image is required"}}
        (let [enriched-wines (when (and (not include-bar?) (seq selected-ids))
                               (db-api/get-enriched-wines-by-ids selected-ids))
              cellar-wines (when-not include-bar?
                             (or (db-api/get-wines-for-list) []))
              condensed (when-not include-bar?
                          (summary/condensed-summary cellar-wines))
              bar (when include-bar?
                    {:spirits (db-api/get-spirits)
                     :inventory-items (db-api/get-bar-inventory-items)
                     :recipes (db-api/get-cocktail-recipes)})
              urls (vec
                    (take 2 (re-seq #"https?://[^\s<>\"{}|\\^`\[\]]+" message)))
              web-content
              (when (seq urls)
                (into
                 {}
                 (keep
                  (fn [[url result]] (when-let [text (:ok result)] [url text]))
                  (map vector urls (pmap web-fetch/fetch-url-content urls)))))
              context (cond-> {:chat-mode (if include-bar? :bar :wine)
                               :summary condensed
                               :selected-wines (if (and include?
                                                        (not include-bar?))
                                                 (vec enriched-wines)
                                                 [])}
                        include-bar? (assoc :bar bar)
                        (seq web-content) (assoc :web-content web-content))
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

(defn- handle-chat-error
  [e]
  (if-let [status (:status (ex-data e))]
    {:status status :body {:error (.getMessage e)}}
    (server-error e)))

(defn- with-conversation
  "Resolve the conversation from `request`, enforce ownership, and call
  `(f conversation-id conversation)` on success. Returns a 404/403 ring response
  on failure and routes thrown exceptions through `handle-chat-error`."
  [request f]
  (try (let [email (ensure-user-email request)
             conversation-id (get-in request [:parameters :path :id])
             conversation (db-api/get-conversation conversation-id)]
         (cond (nil? conversation) (response/not-found
                                    {:error "Conversation not found"})
               (not= email (:user_email conversation))
               {:status 403 :body {:error "Forbidden"}}
               :else (f conversation-id conversation)))
       (catch Exception e (handle-chat-error e))))

(defn list-conversations
  [request]
  (try (let [email (ensure-user-email request)
             search-text (get-in request [:parameters :query :search-text])
             chat-type (get-in request [:parameters :query :chat_type])]
         (response/response
          (db-api/list-conversations-for-user email search-text chat-type)))
       (catch Exception e (handle-chat-error e))))

(defn create-conversation
  [request]
  (try (let [email (ensure-user-email request)
             {:keys [title wine_ids wine_search_state auto_tags pinned provider
                     chat_type]}
             (get-in request [:parameters :body])
             payload (cond-> {:user_email email
                              :title title
                              :wine_ids wine_ids
                              :wine_search_state wine_search_state
                              :auto_tags auto_tags
                              :provider provider
                              :chat_type (or chat_type "wine")}
                       (some? pinned) (assoc :pinned pinned))
             conversation (db-api/create-conversation! payload)]
         (-> (response/response conversation)
             (response/status 201)))
       (catch Exception e (handle-chat-error e))))

(defn list-conversation-messages
  [request]
  (with-conversation request
                     (fn [conversation-id _]
                       (response/response (db-api/list-messages-for-conversation
                                           conversation-id)))))

(defn append-conversation-message
  [request]
  (with-conversation
   request
   (fn [conversation-id conversation]
     (let [{:keys [is_user content image_data tokens_used]}
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
             (when-let [title (ai/generate-conversation-title (:provider
                                                               conversation)
                                                              content)]
               (db-api/update-conversation! conversation-id {:title title})))]
       (-> (response/response (cond-> {:message inserted}
                                updated-conversation
                                (assoc :conversation updated-conversation)))
           (response/status 201))))))

(defn update-conversation-message
  [request]
  (with-conversation
   request
   (fn [conversation-id _]
     (let [message-id (get-in request [:parameters :path :message-id])
           body (get-in request [:parameters :body])
           {:keys [content image truncate_after? tokens_used]} body
           payload (cond-> {:conversation_id conversation-id
                            :message_id message-id
                            :content content}
                     (contains? body :image) (assoc :image_data image)
                     (contains? body :tokens_used) (assoc :tokens_used
                                                          tokens_used)
                     (true? truncate_after?) (assoc :truncate_after? true))]
       (-> (response/response (db-api/update-conversation-message! payload))
           (response/status 200))))))

(defn delete-conversation
  [request]
  (with-conversation request
                     (fn [conversation-id _]
                       (db-api/delete-conversation! conversation-id)
                       (no-content))))

(defn update-conversation
  [request]
  (with-conversation
   request
   (fn [conversation-id _]
     (let [updates (get-in request [:parameters :body])]
       (if (seq updates)
         (response/response (db-api/update-conversation! conversation-id
                                                         updates))
         {:status 400 :body {:error "No updates provided"}})))))

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
  (with-server-error (tap> "🔄 ADMIN: Marking all wines as unverified...")
                     (let [updated-count (db-api/mark-all-wines-unverified)]
                       (tap> ["✅ ADMIN: Marked" updated-count
                              "wines as unverified"])
                       {:status 200
                        :body {:message "All wines marked as unverified"
                               :wines-updated updated-count}})))

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
(defn- device-blocked-response
  []
  {:status 403 :body {:error "Device is blocked"}})

(defn- if-device
  "Look up the device and call (f device) on success, else 404."
  [device-id f]
  (with-server-error (if-let [device (db-api/get-device device-id)]
                       (f device)
                       (not-found "Device"))))

(defn claim-device
  [request]
  (let [{:keys [device_id claim_code firmware_version capabilities]}
        (get-in request [:parameters :body])]
    (with-server-error
     (let [device (devices/claim! {:device_id device_id
                                   :claim_code claim_code
                                   :firmware_version firmware_version
                                   :capabilities capabilities})]
       (if (= "blocked" (:status device))
         (device-blocked-response)
         {:status 202
          :body {:status (:status device)
                 :device_id device_id
                 :retry_after_seconds retry-after-seconds}})))))

(defn poll-device-claim
  [request]
  (let [{:keys [device_id claim_code]} (get-in request [:parameters :body])]
    (if-device
     device_id
     (fn [device]
       (let [claim-hash (devices/hash-string claim_code)]
         (cond (not= claim-hash (:claim_code_hash device))
               {:status 401 :body {:error "Invalid claim code"}}
               (= "blocked" (:status device)) (device-blocked-response)
               (= "pending" (:status device)) {:status 200
                                               :body {:status "pending"
                                                      :retry_after_seconds
                                                      retry-after-seconds}}
               :else (let [{:keys [tokens]} (devices/issue-and-store-token-pair!
                                             device_id)]
                       {:status 200
                        :body (merge {:status "approved" :device_id device_id}
                                     tokens)})))))))

(defn refresh-device-token
  [request]
  (let [{:keys [device_id refresh_token]} (get-in request [:parameters :body])]
    (if-device device_id
               (fn [device]
                 (if (not= "active" (:status device))
                   {:status 403 :body {:error "Device is not active"}}
                   (if-let [{:keys [tokens]}
                            (devices/refresh-with-token! device refresh_token)]
                     {:status 200 :body (merge {:device_id device_id} tokens)}
                     {:status 401 :body {:error "Invalid refresh token"}}))))))

(defn list-devices-admin
  [_]
  (with-server-error (->> (db-api/list-devices)
                          (map devices/public-device-view)
                          vec
                          response/response)))

(defn approve-device
  [{{{:keys [device_id]} :path {:keys [claim_code]} :body} :parameters}]
  (if-device
   device_id
   (fn [device]
     (cond (not= (:claim_code_hash device) (devices/hash-string claim_code))
           {:status 422 :body {:error "Invalid claim code"}}
           (= "blocked" (:status device)) (device-blocked-response)
           :else (let [updated (db-api/update-device! device_id
                                                      {:status "active"
                                                       :refresh_token_hash nil
                                                       :token_expires_at nil})]
                   {:status 200 :body (devices/public-device-view updated)})))))

(defn- device-status-update
  [updater device-id]
  (with-server-error (if-let [updated (updater device-id)]
                       {:status 200 :body (devices/public-device-view updated)}
                       (not-found "Device"))))

(defn block-device
  [{{{:keys [device_id]} :path} :parameters}]
  (device-status-update db-api/block-device! device_id))

(defn unblock-device
  [{{{:keys [device_id]} :path} :parameters}]
  (device-status-update db-api/unblock-device! device_id))

(defn delete-device
  [{{{:keys [device_id]} :path} :parameters}]
  (with-server-error (let [deleted (db-api/delete-device! device_id)]
                       (if (pos? (:next.jdbc/update-count deleted 0))
                         (no-content)
                         (not-found "Device")))))

(defn update-device-sensor-config
  [{{{:keys [device_id]} :path body :body} :parameters}]
  (with-server-error
   (if-let [updated (db-api/update-device! device_id {:sensor_config body})]
     (response/response (devices/public-device-view updated))
     (not-found "Device"))))

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

(defn ingest-sensor-reading
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
                (let [record (db-api/create-sensor-reading!
                              (cond-> payload
                                recorded-by (assoc :recorded_by recorded-by)))]
                  (merge-sensor-config! (:device_id payload)
                                        (:temperatures payload))
                  {:status 201 :body record})))
            (catch Exception e (server-error e))))))

(defn list-sensor-readings
  [request]
  (let [{:keys [device_id limit]} (or (get-in request [:parameters :query]) {})]
    (with-server-error (response/response (db-api/list-sensor-readings
                                           {:device_id device_id
                                            :limit (or limit 100)})))))

(defn latest-sensor-readings
  [request]
  (let [{:keys [device_id]} (or (get-in request [:parameters :query]) {})]
    (with-server-error (response/response (db-api/latest-sensor-readings
                                           device_id)))))

(defn sensor-reading-series
  [request]
  (let [{:keys [device_id bucket from to]}
        (or (get-in request [:parameters :query]) {})]
    (with-server-error
     (response/response
      (db-api/sensor-reading-series
       {:device_id device_id :bucket bucket :from from :to to})))))

(defn- start-bulk-job
  [request {:keys [job-label start-fn message]}]
  (with-server-error
   (let [{:keys [wine-ids provider]} (get-in request [:parameters :body])
         wine-count (count wine-ids)]
     (tap> [(str "🔄 ADMIN: Starting " job-label " for ") wine-count
            "wines..."])
     (if (empty? wine-ids)
       {:status 400 :body {:error "No wine IDs provided"}}
       (let [job-id (start-fn {:wine-ids wine-ids :provider provider})]
         (tap> [(str "✅ ADMIN: Started " job-label) job-id])
         {:status 200
          :body {:job-id job-id :message message :total-wines wine-count}})))))

(defn start-drinking-window-job
  "Admin function to start async drinking window regeneration job"
  [request]
  (start-bulk-job request
                  {:job-label "drinking window job"
                   :start-fn
                   wine-cellar.admin.bulk-operations/start-drinking-window-job
                   :message "Drinking window regeneration job started"}))

(defn start-wine-summary-job
  "Admin function to start async wine summary regeneration job"
  [request]
  (start-bulk-job request
                  {:job-label "wine summary job"
                   :start-fn
                   wine-cellar.admin.bulk-operations/start-wine-summary-job
                   :message "Wine summary regeneration job started"}))

(defn get-job-status
  "Get status of an async job"
  [request]
  (with-server-error
   (let [job-id (get-in request [:parameters :path :job-id])]
     (if-let [status (wine-cellar.admin.bulk-operations/get-job-status job-id)]
       {:status 200 :body status}
       (not-found "Job")))))
