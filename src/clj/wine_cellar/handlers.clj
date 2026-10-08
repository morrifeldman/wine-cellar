(ns wine-cellar.handlers
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]
            [wine-cellar.db.api :as db-api]
            [wine-cellar.ai.core :as ai]
            [wine-cellar.db.setup :as db-setup]
            [wine-cellar.admin.bulk-operations]
            [wine-cellar.devices :as devices]
            [wine-cellar.reports.core :as reports]
            [wine-cellar.http :as http]
            [wine-cellar.summary :as summary]
            [wine-cellar.logging :as logging]
            [wine-cellar.utils.web-fetch :as web-fetch])
  (:import [java.time Instant]))

(defn- if-wine
  "If the wine identified by `id` exists, run `f` and return its ring response;
  otherwise return a 404."
  [id f]
  (if (db-api/wine-exists? id) (f) (http/not-found "Wine")))

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
  (let [exp-seconds (get-in request [:user :exp])
        exp (when exp-seconds (Instant/ofEpochSecond exp-seconds))]
    (when device-id (db-api/touch-device! device-id {:token_expires_at exp}))))

;; Wine Classification Handlers

(defn create-classification
  [{{classification :body} :parameters}]
  (http/created (db-api/create-or-update-classification classification)))

(defn get-classification
  [{{{:keys [id]} :path} :parameters}]
  (http/found-or-404 "Classification" (db-api/get-classification id)))

(defn get-classifications [_] (http/ok (db-api/get-classifications)))

(defn update-classification
  [{{{:keys [id]} :path body :body} :parameters}]
  (http/found-or-404 "Classification" (db-api/update-classification! id body)))

(defn delete-classification
  [{{{:keys [id]} :path} :parameters}]
  (http/deleted-or-404 "Classification" (db-api/delete-classification! id)))

(defn get-regions-by-country
  [{{{:keys [country]} :path} :parameters}]
  (http/ok (db-api/get-regions-by-country country)))

(defn get-appellations-by-region
  [{{{:keys [country region]} :path} :parameters}]
  (http/ok (db-api/get-appellations-by-region country region)))

(defn get-wines-for-list [_] (http/ok (db-api/get-wines-for-list)))

(defn get-technical-data-keys [_] (http/ok (db-api/get-all-metadata-keys)))

(defn get-wine
  [{{{:keys [id]} :path {:keys [include_images]} :query} :parameters}]
  (http/found-or-404 "Wine" (db-api/get-wine id include_images)))

(defn create-wine
  [{{wine :body} :parameters}]
  (http/created (db-api/create-wine-with-classification! wine)))

(defn update-wine
  [{{{:keys [id]} :path body :body} :parameters}]
  (http/found-or-404 "Wine" (db-api/update-wine! id body)))

(defn delete-wine
  [{{{:keys [id]} :path} :parameters}]
  (http/deleted-or-404 "Wine" (db-api/delete-wine! id)))

(defn adjust-quantity
  [{{{:keys [id]} :path {:keys [adjustment reason notes occurred_at]} :body}
    :parameters}]
  (if-wine id
           #(http/ok (db-api/adjust-quantity
                      id
                      adjustment
                      {:reason reason :notes notes :occurred_at occurred_at}))))

(defn coravin-pour
  [{{{:keys [id]} :path {:keys [oz notes]} :body} :parameters}]
  (if-wine id #(http/ok (db-api/coravin-pour id oz {:notes notes}))))

(defn finish-open-bottle
  [{{{:keys [id]} :path {:keys [notes]} :body} :parameters}]
  (if-wine id
           #(if-let [wine (db-api/finish-open-bottle id {:notes notes})]
              (http/ok wine)
              (http/bad-request "No open bottle to finish"))))

(defn get-inventory-history
  [{{{:keys [id]} :path} :parameters}]
  (if-wine id #(http/ok (db-api/get-inventory-history id))))

(defn update-inventory-history
  [{{{:keys [history-id]} :path body :body} :parameters}]
  (http/found-or-404 "History record"
                     (db-api/update-inventory-history! history-id body)))

(defn delete-inventory-history
  [{{{:keys [history-id]} :path} :parameters}]
  (http/deleted-or-404 "History record"
                       (db-api/delete-inventory-history! history-id)))

;; Tasting Notes Handlers
(defn get-tasting-notes-by-wine
  [{{{:keys [id]} :path} :parameters}]
  (http/ok (db-api/get-tasting-notes-by-wine id)))

(defn get-tasting-note
  [{{{:keys [note-id]} :path} :parameters}]
  (http/found-or-404 "Tasting note" (db-api/get-tasting-note note-id)))

(defn create-tasting-note
  [{{{:keys [id]} :path body :body} :parameters}]
  (if (db-api/wine-exists? id)
    (http/created (db-api/create-tasting-note (assoc body :wine_id id)))
    (http/not-found "Wine")))

(defn update-tasting-note
  [{{{:keys [note-id]} :path body :body} :parameters}]
  (http/found-or-404 "Tasting note" (db-api/update-tasting-note! note-id body)))

(defn delete-tasting-note
  [{{{:keys [note-id]} :path} :parameters}]
  (http/deleted-or-404 "Tasting note" (db-api/delete-tasting-note! note-id)))

(defn get-tasting-note-sources [_] (http/ok (db-api/get-tasting-note-sources)))

;; Blind Tasting Handlers
(defn get-blind-tastings [_] (http/ok (db-api/get-blind-tastings)))

(defn create-blind-tasting
  [{{{:keys [notes rating tasting_date wset_data]} :body} :parameters}]
  (http/created (db-api/create-blind-tasting {:notes notes
                                              :rating rating
                                              :tasting_date tasting_date
                                              :wset_data wset_data})))

(defn link-blind-tasting
  [{{{:keys [id]} :path {:keys [wine_id]} :body} :parameters}]
  (cond (nil? wine_id) (http/bad-request "wine_id is required")
        (not (db-api/wine-exists? wine_id)) (http/not-found "Wine")
        :else (if-let [updated (db-api/link-blind-tasting id wine_id)]
                (http/ok updated)
                (http/not-found "Blind tasting (or already linked)"))))

;; Grape Varieties Handlers
(defn get-grape-varieties [_] (http/ok (db-api/get-all-grape-varieties)))

(defn create-grape-variety
  [{{{:keys [variety_name]} :body} :parameters}]
  (http/created (db-api/create-grape-variety variety_name)))

(defn get-grape-variety
  [{{{:keys [id]} :path} :parameters}]
  (http/found-or-404 "Grape variety" (db-api/get-grape-variety id)))

(defn update-grape-variety
  [{{{:keys [id]} :path {:keys [variety_name]} :body} :parameters}]
  (http/found-or-404 "Grape variety"
                     (db-api/update-grape-variety! id variety_name)))

(defn delete-grape-variety
  [{{{:keys [id]} :path} :parameters}]
  (http/deleted-or-404 "Grape variety" (db-api/delete-grape-variety! id)))

;; Wine Varieties Handlers
(defn- if-wine-and-variety
  [wine-id variety-id f]
  (if-wine wine-id
           #(if (db-api/get-grape-variety variety-id)
              (f)
              (http/not-found "Grape variety"))))

(defn get-wine-varieties
  [{{{:keys [id]} :path} :parameters}]
  (if-wine id #(http/ok (db-api/get-wine-grape-varieties id))))

(defn add-variety-to-wine
  [{{{:keys [id]} :path {:keys [variety_id percentage]} :body} :parameters}]
  (if-wine-and-variety
   id
   variety_id
   #(http/created
     (db-api/associate-grape-variety-with-wine id variety_id percentage))))

(defn update-wine-variety-percentage
  [{{{:keys [id variety-id]} :path {:keys [percentage]} :body} :parameters}]
  (if-wine-and-variety
   id
   variety-id
   #(http/ok
     (db-api/associate-grape-variety-with-wine id variety-id percentage))))

(defn remove-variety-from-wine
  [{{{:keys [id variety-id]} :path} :parameters}]
  (if-wine-and-variety id
                       variety-id
                       #(do (db-api/remove-grape-variety-from-wine id
                                                                   variety-id)
                            (http/no-content))))

;; AI Analysis Handlers
(defn analyze-wine-label
  [{{{:keys [label_image back_label_image provider]} :body} :parameters}]
  (if (nil? label_image)
    (http/bad-request "Label image is required")
    (http/ok (ai/analyze-wine-label provider
                                    label_image
                                    back_label_image
                                    (db-api/get-classifications)))))

(defn analyze-spirit-label
  [{{{:keys [label_image provider]} :body} :parameters}]
  (if (nil? label_image)
    (http/bad-request "Label image is required")
    (http/ok (ai/analyze-spirit-label provider label_image))))

(defn- enrich-wine
  [wine]
  (if (:id wine) (first (db-api/get-enriched-wines-by-ids [(:id wine)])) wine))

(defn suggest-drinking-window
  [{{{:keys [wine provider]} :body} :parameters}]
  (if (nil? wine)
    (http/bad-request "Wine details are required")
    (http/ok (ai/suggest-drinking-window provider (enrich-wine wine)))))

(defn generate-wine-summary
  [{{{:keys [wine provider]} :body} :parameters}]
  (if (nil? wine)
    (http/bad-request "Wine details are required")
    (http/ok (ai/generate-wine-summary provider (enrich-wine wine)))))

(defn list-reports [_] (http/ok (reports/list-reports)))

(defn get-report
  [{{{:keys [id]} :path} :parameters}]
  (if-let [report (reports/get-report-by-id id)]
    (http/ok report)
    (http/not-found "Report")))

(defn get-latest-report
  [request]
  (let [force? (get-in request [:query-params "force"])]
    (http/ok (reports/generate-report! {:force? (boolean force?)}))))

(defn health-check
  [_]
  (db-api/ping-db)
  (http/ok {:status "healthy"
            :database "connected"
            :timestamp (str (java.time.Instant/now))}))

;; Bar Handlers

(defn get-spirits [_] (http/ok (db-api/get-spirits)))

(defn get-spirit
  [{{{:keys [id]} :path} :parameters}]
  (http/found-or-404 "Spirit" (db-api/get-spirit id)))

(defn create-spirit
  [{{body :body} :parameters}]
  (http/created (db-api/create-spirit! body)))

(defn update-spirit
  [{{{:keys [id]} :path body :body} :parameters}]
  (http/found-or-404 "Spirit" (db-api/update-spirit! id body)))

(defn delete-spirit
  [{{{:keys [id]} :path} :parameters}]
  (http/deleted-or-404 "Spirit" (db-api/delete-spirit! id)))

(defn get-bar-inventory [_] (http/ok (db-api/get-bar-inventory-items)))

(defn update-bar-inventory-item
  [{{{:keys [id]} :path body :body} :parameters}]
  (http/found-or-404 "Bar inventory item"
                     (db-api/update-bar-inventory-item! id body)))

(defn create-bar-inventory-item
  [{{body :body} :parameters}]
  (http/created (db-api/create-bar-inventory-item! body)))

(defn delete-bar-inventory-item
  [{{{:keys [id]} :path} :parameters}]
  (http/deleted-or-404 "Bar inventory item"
                       (db-api/delete-bar-inventory-item! id)))

(defn get-cocktail-recipes [_] (http/ok (db-api/get-cocktail-recipes)))

(defn get-cocktail-recipe
  [{{{:keys [id]} :path} :parameters}]
  (http/found-or-404 "Recipe" (db-api/get-cocktail-recipe id)))

(defn- recipe-timers
  [instructions]
  (if (str/blank? instructions) [] (ai/extract-recipe-timers instructions)))

(defn- with-timers
  "Timers are read from the instructions, so a recipe typed in by hand or
   given new instructions needs them read again. When the AI call fails, the
   recipe keeps whatever timers it had."
  [recipe]
  (if-let [timers (recipe-timers (:instructions recipe))]
    (assoc recipe :timers timers)
    recipe))

(defn create-cocktail-recipe
  [{{body :body} :parameters}]
  (http/created (db-api/create-cocktail-recipe!
                 (cond-> body (not (contains? body :timers)) with-timers))))

(defn update-cocktail-recipe
  [{{{:keys [id]} :path body :body} :parameters}]
  (if-let [existing (db-api/get-cocktail-recipe id)]
    (http/ok (db-api/update-cocktail-recipe!
              id
              (cond-> body
                (and (contains? body :instructions)
                     (not (contains? body :timers))
                     (not= (:instructions body) (:instructions existing)))
                with-timers)))
    (http/not-found "Recipe")))

(defn delete-cocktail-recipe
  [{{{:keys [id]} :path} :parameters}]
  (http/deleted-or-404 "Recipe" (db-api/delete-cocktail-recipe! id)))

(defn- merge-recipe-links
  "Rewrites ingredients from a resolve-recipe-links result, joining by index.
   The fresh links are authoritative — :inventory_item_ids and :spirit are
   rebuilt from the result and cleared when absent. :garnish is sticky-true:
   an ingredient stays a garnish if either the result or the incoming
   ingredient says so (extraction saw the source text, so its flag is
   higher-confidence than a re-link's). A spec in a grab-bag category
   (liqueur/other) with neither subcategory nor spirit_id is discarded — such
   a spec is unsatisfiable by construction (bottles-for-spec never matches
   it), so it could only block makeability; the model sometimes emits one for
   dashed bitters despite the prompt."
  [ingredients {:keys [ingredient_links spirit_links]}]
  (let [link-by-idx (into {} (map (juxt :index identity)) ingredient_links)
        spirit-by-idx
        (into {} (map (juxt :ingredient_index identity)) spirit_links)]
    (vec
     (map-indexed
      (fn [i ing]
        (let [{:keys [inventory_item_ids garnish]} (get link-by-idx i)
              {:keys [spirit_id category subcategory preferred_spirit_ids
                      alternate_spirit_ids]}
              (get spirit-by-idx i)
              garnish? (or (true? garnish) (true? (:garnish ing)))
              spec? (and (seq category)
                         (or spirit_id
                             (seq subcategory)
                             (not (common/grab-bag-spirit-categories
                                   (str/lower-case category)))))]
          (cond-> (dissoc ing :inventory_item_ids :garnish :spirit)
            (seq inventory_item_ids) (assoc :inventory_item_ids
                                            (vec inventory_item_ids))
            garnish? (assoc :garnish true)
            spec? (assoc :spirit
                         (cond-> {:category category}
                           (seq subcategory) (assoc :subcategory subcategory)
                           spirit_id (assoc :spirit_id spirit_id)
                           (seq preferred_spirit_ids)
                           (assoc :preferred_spirit_ids
                                  (vec preferred_spirit_ids))
                           (seq alternate_spirit_ids)
                           (assoc :alternate_spirit_ids
                                  (vec alternate_spirit_ids)))))))
      ingredients))))

(defn extract-cocktail-recipe
  "Two-phase: extracts recipes from text and/or an image without any bar
   context, then links each extracted recipe to the bar via the same
   resolution call refresh uses. A failed resolve degrades to an unlinked
   recipe rather than failing the extraction."
  [request]
  (let [{:keys [message-text image]} (get-in request [:parameters :body])
        existing-tags (db-api/distinct-recipe-tags)
        bar {:spirits (db-api/get-spirits)
             :inventory-items (db-api/get-bar-inventory-items)}]
    (if (and (empty? message-text) (empty? image))
      (http/bad-request "message-text or image is required")
      (if-let [result
               (ai/extract-cocktail-recipe message-text existing-tags image)]
        (http/ok (update
                  result
                  :recipes
                  (partial
                   mapv
                   (fn [recipe]
                     (if-let [links (and (seq (:ingredients recipe))
                                         (ai/resolve-recipe-links recipe bar))]
                       (update recipe :ingredients merge-recipe-links links)
                       recipe)))))
        {:status 422 :body {:error "Could not extract recipe"}}))))

(defn refresh-recipe-links
  "Re-resolves a stored recipe's links against current inventory via an id-keyed
   resolution call (no re-extraction, so ingredient text is never paraphrased).
   The fresh run is authoritative: each ingredient's :inventory_item_ids and
   :spirit spec are rewritten from the result by index — links the AI no longer
   makes are cleared (except :garnish, which is sticky once true). Out-of-stock
   items and spirits are in the prompt, so they can still be re-linked."
  [{{{:keys [id]} :path} :parameters}]
  (if-let [recipe (db-api/get-cocktail-recipe id)]
    (let [bar {:spirits (db-api/get-spirits)
               :inventory-items (db-api/get-bar-inventory-items)}
          result (ai/resolve-recipe-links recipe bar)]
      (if result
        (http/ok (db-api/update-cocktail-recipe!
                  id
                  {:ingredients (merge-recipe-links (:ingredients recipe)
                                                    result)}))
        {:status 422 :body {:error "Could not refresh recipe links"}}))
    (http/not-found "Recipe")))

;; Chat Handlers

(defn- complete-context-note
  "A note is a snapshot of the wines as Claude saw them when they came into
   the conversation, so it carries their rendered details, not just ids."
  [{:keys [label wine_ids text]}]
  (let [wine-ids (vec (remove nil? wine_ids))]
    {:label label
     :wine_ids wine-ids
     :text (or text (ai/wines-context-text wine-ids))}))

(defn- complete-history-notes
  "A note whose message was saved moments ago may not have its text back in
   the browser yet; render it the same way saving it did."
  [conversation-history]
  (mapv (fn [msg]
          (if-let [note (or (:context-note msg) (:context_note msg))]
            (-> msg
                (dissoc :context_note)
                (assoc :context-note (complete-context-note note)))
            msg))
        conversation-history))

(defn chat-with-ai
  [request]
  (let [body (-> request
                 :parameters
                 :body)
        {:keys [image provider effort include-bar?]} body
        conversation-history (complete-history-notes (:conversation-history
                                                      body))
        include-bar? (boolean include-bar?)
        message (or (some #(when (or (:is-user %) (:is_user %))
                             (or (:content %) (:text %)))
                          (reverse conversation-history))
                    "")]
    (if (and (empty? conversation-history) (empty? image))
      (http/bad-request "Conversation history or image is required")
      (let [cellar-wines (when-not include-bar?
                           (or (db-api/get-wines-for-list) []))
            condensed (when-not include-bar?
                        (summary/condensed-summary cellar-wines))
            bar (when include-bar?
                  {:spirits (db-api/get-spirits)
                   :inventory-items (db-api/get-bar-inventory-items)
                   :recipes (db-api/get-cocktail-recipes)})
            ;; Every provider searches the web on its own side, but only
            ;; Anthropic can open a specific URL (web_fetch), so we still
            ;; download pasted links here for the other two.
            urls
            (when-not (= :anthropic provider)
              (vec (take 2 (re-seq #"https?://[^\s<>\"{}|\\^`\[\]]+" message))))
            web-content
            (when (seq urls)
              (into
               {}
               (keep
                (fn [[url result]] (when-let [text (:ok result)] [url text]))
                (map vector urls (pmap web-fetch/fetch-url-content urls)))))
            context (cond-> {:chat-mode (if include-bar? :bar :wine)
                             :summary condensed}
                      include-bar? (assoc :bar bar)
                      (seq web-content) (assoc :web-content web-content)
                      effort (assoc :effort effort))
            response
            (ai/chat-about-wines provider context conversation-history image)]
        (http/ok response)))))

;; Conversation persistence handlers

(defn- ensure-user-email
  [request]
  (if-let [email (get-in request [:user :email])]
    email
    (http/throw-status 401 "Authenticated user email required")))

(defn- with-conversation
  "Resolve the conversation from `request`, enforce ownership, and call
  `(f conversation-id conversation)` on success. Returns a 404/403 ring response
  on failure."
  [request f]
  (let [email (ensure-user-email request)
        conversation-id (get-in request [:parameters :path :id])
        conversation (db-api/get-conversation conversation-id)]
    (cond (nil? conversation) (http/not-found "Conversation")
          (not= email (:user_email conversation)) {:status 403
                                                   :body {:error "Forbidden"}}
          :else (f conversation-id conversation))))

(defn list-conversations
  [request]
  (let [email (ensure-user-email request)
        search-text (get-in request [:parameters :query :search-text])
        chat-type (get-in request [:parameters :query :chat_type])]
    (http/ok (db-api/list-conversations-for-user email search-text chat-type))))

(defn create-conversation
  [request]
  (let [email (ensure-user-email request)
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
    (http/created conversation)))

(defn list-conversation-messages
  [request]
  (with-conversation request
                     (fn [conversation-id _]
                       (http/ok (db-api/list-messages-for-conversation
                                 conversation-id)))))

(defn append-conversation-message
  [request]
  (with-conversation
   request
   (fn [conversation-id conversation]
     (let [{:keys [is_user content image_data tokens_used context_note]}
           (get-in request [:parameters :body])
           message (cond-> {:conversation_id conversation-id
                            :is_user (boolean is_user)
                            :content content
                            :image_data image_data
                            :tokens_used tokens_used}
                     context_note (assoc :context_note
                                         (complete-context-note context_note)))
           inserted (db-api/append-conversation-message! message)
           title-needed? (and (:is_user inserted)
                              (str/blank? (:title conversation))
                              (not (str/blank? content)))
           updated-conversation
           (or (when title-needed?
                 (when-let [title (ai/generate-conversation-title (:provider
                                                                   conversation)
                                                                  content)]
                   (db-api/update-conversation! conversation-id
                                                {:title title})))
               (when context_note (db-api/get-conversation conversation-id)))]
       (http/created (cond-> {:message inserted}
                       updated-conversation (assoc :conversation
                                                   updated-conversation)))))))

(defn update-conversation-message
  [request]
  (with-conversation
   request
   (fn [conversation-id _]
     (let [message-id (get-in request [:parameters :path :message-id])
           body (get-in request [:parameters :body])
           {:keys [content image truncate_after? tokens_used context_note]} body
           payload (cond-> {:conversation_id conversation-id
                            :message_id message-id
                            :content content}
                     (contains? body :image) (assoc :image_data image)
                     (contains? body :context_note)
                     (assoc :context_note
                            (some-> context_note
                                    complete-context-note))
                     (contains? body :tokens_used) (assoc :tokens_used
                                                          tokens_used)
                     (true? truncate_after?) (assoc :truncate_after? true))]
       (http/ok (db-api/update-conversation-message! payload))))))

(defn fork-conversation
  [request]
  (with-conversation
   request
   (fn [conversation-id _]
     (let [message-count (get-in request [:parameters :body :message_count])]
       (http/created (db-api/fork-conversation! conversation-id
                                                message-count))))))

(defn delete-conversation
  [request]
  (with-conversation request
                     (fn [conversation-id _]
                       (db-api/delete-conversation! conversation-id)
                       (http/no-content))))

(defn update-conversation
  [request]
  (with-conversation request
                     (fn [conversation-id _]
                       (let [updates (get-in request [:parameters :body])]
                         (if (seq updates)
                           (http/ok (db-api/update-conversation! conversation-id
                                                                 updates))
                           (http/bad-request "No updates provided"))))))

;; Admin Handlers

(defn get-model-info
  "Get current AI model configuration for all providers"
  [_request]
  (http/ok (wine-cellar.ai.core/get-model-info)))

(defn get-db-schema
  "Admin function to get database schema"
  [_]
  (http/ok (db-api/get-db-schema)))

(defn execute-sql-query
  "Admin function to execute raw SQL queries"
  [{{{:keys [query]} :body} :parameters}]
  (try (tap> ["⚡ ADMIN: Executing raw SQL query:" query])
       (let [result (db-api/execute-sql-query query)] (http/ok result))
       (catch Exception e
         (tap> ["❌ ADMIN: SQL execution failed:" (.getMessage e)])
         ;; The admin's own SQL failing is a bad request, whatever
         ;; SQLSTATE.
         {:status 400
          :body {:error "SQL execution failed" :details (.getMessage e)}})))

(defn reset-database
  "Admin function to drop and recreate all database tables"
  [_request]
  (tap> "🔥 ADMIN: Dropping all database tables...")
  (db-setup/drop-tables)
  (tap> "🛠️  ADMIN: Recreating database schema...")
  (db-setup/initialize-db false) ; Skip classification seeding for imports
  (tap> "✅ ADMIN: Database reset complete!")
  (http/ok {:message "Database reset successfully"
            :tables-dropped true
            :schema-recreated true
            :classifications-seeded false}))

(defn mark-all-wines-unverified
  "Admin function to mark all wines as unverified for inventory verification"
  [_]
  (tap> "🔄 ADMIN: Marking all wines as unverified...")
  (let [updated-count (db-api/mark-all-wines-unverified)]
    (tap> ["✅ ADMIN: Marked" updated-count "wines as unverified"])
    (http/ok {:message "All wines marked as unverified"
              :wines-updated updated-count})))

(defn reextract-recipe-timers
  "Admin: re-reads every recipe's timed steps from its saved instructions,
   overwriting the stored timers, so a change to the timer prompt reaches old
   recipes too."
  [_]
  (let [results
        (->> (db-api/get-cocktail-recipes)
             (pmap (fn [{:keys [id instructions]}]
                     (when-let [timers (recipe-timers instructions)]
                       (db-api/update-cocktail-recipe! id {:timers timers}))))
             doall)
        updated (count (remove nil? results))]
    (http/ok {:message (str "Re-read timers for " updated " recipes")
              :recipes-updated updated
              :recipes-failed (- (count results) updated)})))

(defn get-verbose-logging-state [_] (http/ok (logging/verbose-logging-status)))

(defn set-verbose-logging-state
  [{{{:keys [enabled?]} :body} :parameters}]
  (if (nil? enabled?)
    (http/bad-request "enabled? flag is required")
    (do (logging/set-verbose-logging! enabled?)
        (http/ok (logging/verbose-logging-status)))))

;; Device provisioning handlers
(defn- device-blocked-response
  []
  {:status 403 :body {:error "Device is blocked"}})

(defn- if-device
  "Look up the device and call (f device) on success, else 404."
  [device-id f]
  (if-let [device (db-api/get-device device-id)]
    (f device)
    (http/not-found "Device")))

(defn claim-device
  [request]
  (let [{:keys [device_id] :as claim}
        (select-keys (get-in request [:parameters :body])
                     [:device_id :claim_code :firmware_version :capabilities])
        device (devices/claim! claim)]
    (if (= "blocked" (:status device))
      (device-blocked-response)
      {:status 202
       :body {:status (:status device)
              :device_id device_id
              :retry_after_seconds retry-after-seconds}})))

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
               (= "pending" (:status device)) (http/ok {:status "pending"
                                                        :retry_after_seconds
                                                        retry-after-seconds})
               :else (let [{:keys [tokens]} (devices/issue-and-store-token-pair!
                                             device_id)]
                       (http/ok (merge {:status "approved" :device_id device_id}
                                       tokens)))))))))

(defn refresh-device-token
  [request]
  (let [{:keys [device_id refresh_token]} (get-in request [:parameters :body])]
    (if-device device_id
               (fn [device]
                 (if (not= "active" (:status device))
                   {:status 403 :body {:error "Device is not active"}}
                   (if-let [{:keys [tokens]}
                            (devices/refresh-with-token! device refresh_token)]
                     (http/ok (merge {:device_id device_id} tokens))
                     {:status 401 :body {:error "Invalid refresh token"}}))))))

(defn list-devices-admin
  [_]
  (->> (db-api/list-devices)
       (map devices/public-device-view)
       vec
       http/ok))

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
                   (http/ok (devices/public-device-view updated)))))))

(defn- device-status-update
  [updater device-id]
  (if-let [updated (updater device-id)]
    (http/ok (devices/public-device-view updated))
    (http/not-found "Device")))

(defn block-device
  [{{{:keys [device_id]} :path} :parameters}]
  (device-status-update db-api/block-device! device_id))

(defn unblock-device
  [{{{:keys [device_id]} :path} :parameters}]
  (device-status-update db-api/unblock-device! device_id))

(defn delete-device
  [{{{:keys [device_id]} :path} :parameters}]
  (http/deleted-or-404 "Device" (db-api/delete-device! device_id)))

(defn update-device-sensor-config
  [{{{:keys [device_id]} :path body :body} :parameters}]
  (if-let [updated (db-api/update-device! device_id {:sensor_config body})]
    (http/ok (devices/public-device-view updated))
    (http/not-found "Device")))

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
    (cond
      (not (measurement-present? payload))
      (http/bad-request "At least one measurement value is required")
      (and token-device-id (not= token-device-id payload-device-id))
      {:status 403
       :body {:error "device_id does not match the authenticated device"}}
      :else
      (let [device-status
            (when token-device-id
              (let [device (db-api/get-device token-device-id)]
                (cond (nil? device) {:status 404
                                     :body {:error "Device is not registered"}}
                      (not= "active" (:status device))
                      {:status 403 :body {:error "Device is not active"}}
                      :else (do (touch-device! token-device-id request) nil))))
            recorded-by (or token-device-id
                            (get-in request [:user :email])
                            (get-in request [:user :sub]))]
        (if device-status
          device-status
          (let [record (db-api/create-sensor-reading!
                        (cond-> payload
                          recorded-by (assoc :recorded_by recorded-by)))]
            (merge-sensor-config! (:device_id payload) (:temperatures payload))
            (http/created record)))))))

(defn list-sensor-readings
  [request]
  (let [{:keys [device_id limit]} (or (get-in request [:parameters :query]) {})]
    (http/ok (db-api/list-sensor-readings {:device_id device_id
                                           :limit (or limit 100)}))))

(defn latest-sensor-readings
  [request]
  (let [{:keys [device_id]} (or (get-in request [:parameters :query]) {})]
    (http/ok (db-api/latest-sensor-readings device_id))))

(defn sensor-reading-series
  [request]
  (let [{:keys [device_id bucket from to]}
        (or (get-in request [:parameters :query]) {})]
    (http/ok (db-api/sensor-reading-series
              {:device_id device_id :bucket bucket :from from :to to}))))

(defn- start-bulk-job
  [request {:keys [job-label start-fn message]}]
  (let [{:keys [wine-ids provider]} (get-in request [:parameters :body])
        wine-count (count wine-ids)]
    (tap> [(str "🔄 ADMIN: Starting " job-label " for ") wine-count "wines..."])
    (if (empty? wine-ids)
      (http/bad-request "No wine IDs provided")
      (let [job-id (start-fn {:wine-ids wine-ids :provider provider})]
        (tap> [(str "✅ ADMIN: Started " job-label) job-id])
        (http/ok {:job-id job-id :message message :total-wines wine-count})))))

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
  (let [job-id (get-in request [:parameters :path :job-id])]
    (if-let [status (wine-cellar.admin.bulk-operations/get-job-status job-id)]
      (http/ok status)
      (http/not-found "Job"))))
