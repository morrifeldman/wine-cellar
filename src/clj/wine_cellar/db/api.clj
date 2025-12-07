(ns wine-cellar.db.api
  (:require [clojure.string :as str]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [jsonista.core :as json]
            [wine-cellar.db.connection :refer [db-opts ds]])
  (:import [java.sql Date Timestamp]
           [java.time Instant]
           [java.util Base64]))

;; Helper functions for SQL generation
(defn ->pg-array
  "Convert a Clojure collection into a PostgreSQL array literal string.
  Returns nil when collection itself is nil. Empty collections become '{}'."
  [coll]
  (when coll
    (let [format-item (fn [item]
                        (cond (string? item) (str "\""
                                                  (-> item
                                                      (str/replace "\\" "\\\\")
                                                      (str/replace "\"" "\\\""))
                                                  "\"")
                              (keyword? item) (recur (name item))
                              :else (str item)))
          items (map format-item (remove nil? coll))]
      {:raw (str "'{" (str/join "," items) "}'")})))

(defn sql-cast [sql-type field] [:cast field sql-type])

(defn- ->sql-date
  [^String date-string]
  (some-> date-string
          (subs 0 10)
          (Date/valueOf)))

(defn- ->sql-timestamp
  [^String instant-string]
  (some-> instant-string
          (Instant/parse)
          (Timestamp/from)))

(defn- timestamp->iso-string
  [value]
  (when value
    (-> ^Timestamp value
        .toInstant
        str)))

(defn base64->bytes
  [^String base64-string]
  (when base64-string
    (let [base64-content
          (str/replace base64-string #"^data:image/[^;]+;base64," "")
          decoder (Base64/getDecoder)]
      (.decode decoder (.getBytes base64-content "UTF-8")))))

(defn bytes->base64
  [bytes]
  (when bytes
    (str "data:image/jpeg;base64,"
         (String. (.encode (Base64/getEncoder) bytes) "UTF-8"))))

(defn wine->db-wine
  [{:keys [style level label_image label_thumbnail back_label_image
           purchase_date]
    :as wine}]
  (cond-> wine
    purchase_date (update :purchase_date ->sql-date)
    style (update :style (partial sql-cast :wine_style))
    level (update :level (partial sql-cast :wine_level))
    label_image (update :label_image base64->bytes)
    label_thumbnail (update :label_thumbnail base64->bytes)
    back_label_image (update :back_label_image base64->bytes)))

(defn tasting-note->db-tasting-note
  [{:keys [tasting_date wset_data] :as note}]
  (cond-> note
    tasting_date (update :tasting_date ->sql-date)
    wset_data (update :wset_data
                      #(sql-cast :jsonb (json/write-value-as-string %)))))

(defn conversation->db-conversation
  [{:keys [wine_ids auto_tags wine_search_state] :as conversation}]
  (cond-> conversation
    wine_ids (update :wine_ids #(->pg-array %))
    auto_tags (update :auto_tags #(->pg-array %))
    (contains? conversation :provider)
    (update :provider
            (fn [value]
              (cond (keyword? value) (name value)
                    (string? value) (str/lower-case value)
                    (nil? value) nil
                    :else (str value))))
    wine_search_state (update :wine_search_state
                              #(sql-cast :jsonb
                                         (json/write-value-as-string %)))))

(defn conversation-message->db-message
  [{:keys [image_data] :as message}]
  (cond-> message image_data (update :image_data base64->bytes)))

(defn db-conversation-message->message
  [{:keys [image_data] :as message}]
  (cond-> message image_data (update :image_data bytes->base64)))

(defn db-conversation->conversation
  [conversation]
  (cond-> conversation
    (contains? conversation :provider) (update :provider
                                               (fn [value]
                                                 (when value
                                                   (-> value
                                                       str/lower-case
                                                       keyword))))))

(defn db-wine->wine
  [{:keys [label_image label_thumbnail back_label_image] :as db-wine}]
  (cond-> db-wine
    label_image (update :label_image bytes->base64)
    label_thumbnail (update :label_thumbnail bytes->base64)
    back_label_image (update :back_label_image bytes->base64)))

(defn cellar-condition->db-row
  [{:keys [measured_at] :as condition}]
  (cond-> condition measured_at (update :measured_at ->sql-timestamp)))

(defn db-cellar-condition->condition
  [{:keys [measured_at created_at] :as row}]
  (-> row
      (cond-> measured_at (update :measured_at timestamp->iso-string))
      (cond-> created_at (update :created_at timestamp->iso-string))))

(defn- instant->sql-timestamp
  [^Instant instant]
  (some-> instant
          Timestamp/from))

(defn device->db-device
  [{:keys [capabilities token_expires_at last_seen] :as device}]
  (cond-> device
    capabilities (update :capabilities
                         #(sql-cast :jsonb (json/write-value-as-string %)))
    (instance? Instant token_expires_at) (update :token_expires_at
                                                 instant->sql-timestamp)
    (instance? Instant last_seen) (update :last_seen instant->sql-timestamp)))

(defn db-device->device
  [{:keys [token_expires_at last_seen created_at updated_at] :as row}]
  (-> row
      (cond-> token_expires_at (update :token_expires_at timestamp->iso-string))
      (cond-> last_seen (update :last_seen timestamp->iso-string))
      (cond-> created_at (update :created_at timestamp->iso-string))
      (cond-> updated_at (update :updated_at timestamp->iso-string))))

(defn execute-sql-query
  "Executes a raw SQL query string and returns the result."
  [sql-string]
  (jdbc/execute! ds [sql-string] db-opts))

(defn ping-db
  "Simple function to test database connectivity"
  []
  (jdbc/execute-one! ds ["SELECT 1 as result"] db-opts))

;; Wine operations
(defn create-wine
  ([wine] (create-wine ds wine))
  ([tx-or-ds wine]
   (jdbc/execute-one! tx-or-ds
                      (sql/format {:insert-into :wines
                                   :values [(wine->db-wine wine)]
                                   :returning :*})
                      db-opts)))

(defn create-conversation!
  "Create a new AI conversation row."
  ([conversation] (create-conversation! ds conversation))
  ([tx-or-ds {:keys [user_email] :as conversation}]
   (assert user_email "user_email is required to create a conversation")
   (let [db-row (-> conversation
                    (dissoc :user_email)
                    (conversation->db-conversation)
                    (assoc :user_email user_email))]
     (some-> (jdbc/execute-one! tx-or-ds
                                (sql/format {:insert-into :ai_conversations
                                             :values [db-row]
                                             :returning :*})
                                db-opts)
             db-conversation->conversation))))

(defn list-conversations-for-user
  "Return conversations for the given user ordered by recent activity."
  [user-email]
  (->> (jdbc/execute! ds
                      (sql/format {:select :*
                                   :from :ai_conversations
                                   :where [:= :user_email user-email]
                                   :order-by [[:pinned :desc]
                                              [:last_message_at :desc]
                                              [:created_at :desc]]})
                      db-opts)
       (map db-conversation->conversation)
       vec))

(defn get-conversation
  [conversation-id]
  (some-> (jdbc/execute-one! ds
                             (sql/format {:select :*
                                          :from :ai_conversations
                                          :where [:= :id conversation-id]})
                             db-opts)
          db-conversation->conversation))

(defn append-conversation-message!
  "Insert a message and update parent metadata atomically."
  [{:keys [conversation_id tokens_used] :as message}]
  (jdbc/with-transaction
   [tx ds]
   (let [inserted (jdbc/execute-one!
                   tx
                   (sql/format {:insert-into :ai_conversation_messages
                                :values [(conversation-message->db-message
                                          message)]
                                :returning :*})
                   db-opts)
         token-inc (or tokens_used 0)]
     (jdbc/execute-one! tx
                        (sql/format {:update :ai_conversations
                                     :set {:last_message_at [:now]
                                           :updated_at [:now]
                                           :total_tokens_used
                                           [:+ :total_tokens_used token-inc]}
                                     :where [:= :id conversation_id]
                                     :returning :*})
                        db-opts)
     (db-conversation-message->message inserted))))

(defn- refresh-conversation-metadata!
  "Recalculate and persist the aggregate metadata for a conversation.
   Returns the updated conversation map."
  [tx conversation-id]
  (let [{:keys [total_tokens last_created_at]}
        (jdbc/execute-one!
         tx
         (sql/format {:select [[[:coalesce [:sum :tokens_used] 0] :total_tokens]
                               [[:max :created_at] :last_created_at]]
                      :from :ai_conversation_messages
                      :where [:= :conversation_id conversation-id]})
         db-opts)
        metadata {:total_tokens_used total_tokens
                  :updated_at [:now]
                  :last_message_at (or last_created_at [:now])}]
    (some-> (jdbc/execute-one! tx
                               (sql/format {:update :ai_conversations
                                            :set metadata
                                            :where [:= :id conversation-id]
                                            :returning :*})
                               db-opts)
            db-conversation->conversation)))

(defn update-conversation-message!
  "Update an existing conversation message and optionally truncate later messages.
   Returns the updated message, any deleted message ids, and the refreshed conversation."
  [{:keys [conversation_id message_id content image_data tokens_used
           truncate_after?]
    :as opts}]
  (jdbc/with-transaction
   [tx ds]
   (let [set-map (cond-> {:content content}
                   (contains? opts :image_data)
                   (assoc :image_data (base64->bytes image_data))
                   (contains? opts :tokens_used) (assoc :tokens_used
                                                        tokens_used))
         updated-row (jdbc/execute-one! tx
                                        (sql/format
                                         {:update :ai_conversation_messages
                                          :set set-map
                                          :where [:and [:= :id message_id]
                                                  [:= :conversation_id
                                                   conversation_id]]
                                          :returning :*})
                                        db-opts)
         updated (some-> updated-row
                         db-conversation-message->message)]
     (when-not updated
       (throw (ex-info "Conversation message not found"
                       {:status 404
                        :conversation-id conversation_id
                        :message-id message_id})))
     (let [deleted (when truncate_after?
                     (jdbc/execute!
                      tx
                      (sql/format {:delete-from :ai_conversation_messages
                                   :where [:and
                                           [:= :conversation_id conversation_id]
                                           [:> :id message_id]]
                                   :returning [:id :tokens_used]})
                      db-opts))
           conversation (refresh-conversation-metadata! tx conversation_id)]
       {:message updated
        :deleted-message-ids (vec (map :id deleted))
        :conversation conversation}))))

(defn list-messages-for-conversation
  [conversation-id]
  (jdbc/execute! ds
                 (sql/format {:select :*
                              :from :ai_conversation_messages
                              :where [:= :conversation_id conversation-id]
                              :order-by [[:created_at :asc] [:id :asc]]})
                 db-opts))

(defn delete-conversation!
  ([conversation-id] (delete-conversation! ds conversation-id))
  ([tx-or-ds conversation-id]
   (jdbc/execute-one! tx-or-ds
                      (sql/format {:delete-from :ai_conversations
                                   :where [:= :id conversation-id]
                                   :returning :id})
                      db-opts)))

(defn update-conversation!
  ([conversation-id updates] (update-conversation! ds conversation-id updates))
  ([tx-or-ds conversation-id updates]
   (let [sanitized
         (-> updates
             (dissoc :id :user_email :created_at :updated_at :last_message_at)
             (conversation->db-conversation))
         set-map (cond-> sanitized true (assoc :updated_at [:now]))]
     (if (seq sanitized)
       (some-> (jdbc/execute-one! tx-or-ds
                                  (sql/format {:update :ai_conversations
                                               :set set-map
                                               :where [:= :id conversation-id]
                                               :returning :*})
                                  db-opts)
               db-conversation->conversation)
       (get-conversation conversation-id)))))

(defn wine-exists?
  [id]
  (jdbc/execute-one! ds
                     (sql/format {:select :id :from :wines :where [:= :id id]})
                     db-opts))

(def wine-list-fields
  [:id :producer :country :region :aoc :classification :vineyard :level :name
   :vintage :style :closure_type :bottle_format :location :purveyor :quantity
   :original_quantity :price :drink_from_year :drink_until_year
   :alcohol_percentage :disgorgement_year :label_thumbnail :created_at
   :updated_at :verified :purchase_date :latest_internal_rating
   :average_external_rating :varieties])

(defn get-wines-for-list
  []
  (let [wines (jdbc/execute! ds
                             ;; Listing columns explicitly since the only
                             ;; image we are getting is the label_thumbnail
                             (sql/format {:select wine-list-fields
                                          :from :enriched_wines
                                          :order-by [[:created_at :desc]]})
                             db-opts)]
    ;; The :varieties JSON will be automatically parsed by our PgObject
    ;; extension
    (mapv db-wine->wine wines)))

(defn get-wine
  [id include-images?]
  (-> (jdbc/execute-one! ds
                         (sql/format {:select
                                      (if include-images? :* wine-list-fields)
                                      :from :enriched_wines
                                      :where [:= :id id]})
                         db-opts)
      db-wine->wine))

(def enriched-wine-fields-for-ai
  (conj wine-list-fields :tasting_notes :tasting_window_commentary :ai_summary))

(defn get-enriched-wines-by-ids
  "Get enriched wines with full tasting notes for AI chat context (no images)"
  [wine-ids]
  (when (seq wine-ids)
    (let [wines (jdbc/execute! ds
                               (sql/format {:select enriched-wine-fields-for-ai
                                            :from :enriched_wines
                                            :where [:in :id wine-ids]
                                            :order-by [[:created_at :desc]]})
                               db-opts)]
      (mapv db-wine->wine wines))))

(defn update-wine!
  [id wine]
  (-> (jdbc/execute-one! ds
                         (sql/format
                          {:update :wines
                           :set (assoc (wine->db-wine wine) :updated_at [:now])
                           :where [:= :id id]
                           :returning :*})
                         db-opts)
      db-wine->wine))

(defn adjust-quantity
  [id adjustment]
  (jdbc/execute-one! ds
                     (sql/format {:update :wines
                                  :set {:quantity [:+ :quantity adjustment]
                                        :updated_at [:now]}
                                  :where [:= :id id]})
                     db-opts))

(defn delete-wine!
  [id]
  (jdbc/execute-one! ds
                     (sql/format {:delete-from :wines :where [:= :id id]})
                     db-opts))

;; Classification operations
(defn create-or-update-classification
  "Creates a new classification or updates an existing one by combining levels"
  ([classification]
   (jdbc/with-transaction [tx ds]
                          (create-or-update-classification tx classification)))
  ([tx classification]
   (let [existing-query
         {:select :*
          :from :wine_classifications
          :where [:and [:= :country (:country classification)]
                  [:= :region (:region classification)]
                  [:= [:coalesce :aoc ""] [:coalesce (:aoc classification) ""]]
                  [:= [:coalesce :classification ""]
                   [:coalesce (:classification classification) ""]]
                  [:= [:coalesce :vineyard ""]
                   [:coalesce (:vineyard classification) ""]]]}
         existing (jdbc/execute-one! tx (sql/format existing-query) db-opts)]
     (if existing
       ;; Update existing classification - merge levels
       (let [levels1 (or (:levels existing) [])
             levels2 (or (:levels classification) [])
             combined-levels (vec (distinct (concat levels1 levels2)))
             update-query {:update :wine_classifications
                           :set {:levels (->pg-array combined-levels)}
                           :where [:= :id (:id existing)]
                           :returning :*}]
         (jdbc/execute-one! tx (sql/format update-query) db-opts))
       ;; Create new classification
       (let [insert-query {:insert-into :wine_classifications
                           :values [(update classification :levels ->pg-array)]
                           :returning :*}]
         (jdbc/execute-one! tx (sql/format insert-query) db-opts))))))

(defn get-classifications
  []
  (jdbc/execute! ds
                 (sql/format {:select :*
                              :from :wine_classifications
                              :order-by [:country :region :aoc]})
                 db-opts))

(defn get-classification
  [id]
  (jdbc/execute-one!
   ds
   (sql/format {:select :* :from :wine_classifications :where [:= :id id]})
   db-opts))

(defn update-classification!
  [id classification]
  (jdbc/execute-one! ds
                     (sql/format {:update :wine_classifications
                                  :set
                                  (update classification :levels ->pg-array)
                                  :where [:= :id id]
                                  :returning :*})
                     db-opts))

(defn delete-classification!
  [id]
  (jdbc/execute-one! ds
                     (sql/format {:delete-from :wine_classifications
                                  :where [:= :id id]})
                     db-opts))

(defn get-regions-by-country
  [country]
  (jdbc/execute! ds
                 (sql/format {:select [:distinct :region]
                              :from :wine_classifications
                              :where [:= :country country]
                              :order-by [:region]})
                 db-opts))

(defn get-aocs-by-region
  [country region]
  (jdbc/execute! ds
                 (sql/format {:select [:distinct :aoc]
                              :from :wine_classifications
                              :where [:and [:= :country country]
                                      [:= :region region]]
                              :order-by [:aoc]})
                 db-opts))

;; Tasting Notes Operations
(defn create-tasting-note
  ([note] (create-tasting-note ds note))
  ([ds-or-tx note]
   (jdbc/execute-one! ds-or-tx
                      (sql/format {:insert-into :tasting_notes
                                   :values [(-> note
                                                tasting-note->db-tasting-note
                                                (assoc :updated_at [:now]))]
                                   :returning :*})
                      db-opts)))

(defn update-tasting-note!
  [id note]
  (tap> ["update-tasting-note!" id note])
  (jdbc/execute-one! ds
                     (sql/format {:update :tasting_notes
                                  :set (-> note
                                           tasting-note->db-tasting-note
                                           (assoc :updated_at [:now]))
                                  :where [:= :id id]
                                  :returning :*})
                     db-opts))

(defn get-tasting-note
  [id]
  (jdbc/execute-one! ds
                     (sql/format
                      {:select :* :from :tasting_notes :where [:= :id id]})
                     db-opts))

(defn get-tasting-notes-by-wine
  [wine-id]
  (jdbc/execute! ds
                 (sql/format {:select :*
                              :from :tasting_notes
                              :where [:= :wine_id wine-id]
                              :order-by [[:tasting_date :desc]]})
                 db-opts))

(defn delete-tasting-note!
  [id]
  (jdbc/execute-one! ds
                     (sql/format {:delete-from :tasting_notes
                                  :where [:= :id id]})
                     db-opts))

(defn get-tasting-note-sources
  "Returns a list of unique source names from external tasting notes"
  []
  (->> (jdbc/execute! ds
                      (sql/format {:select-distinct [:source]
                                   :from :tasting_notes
                                   :where [:and [:= :is_external true]
                                           [:not [:= :source nil]]
                                           [:not [:= :source ""]]]
                                   :order-by [[:source :asc]]})
                      db-opts)
       (map :source)))

;; Grape Varieties Operations
(defn create-grape-variety
  [name]
  (jdbc/execute-one! ds
                     (sql/format {:insert-into :grape_varieties
                                  :values [{:name name}]
                                  :returning :*})
                     db-opts))

(defn get-all-grape-varieties
  []
  (jdbc/execute! ds
                 (sql/format
                  {:select :* :from :grape_varieties :order-by [:name]})
                 db-opts))

(defn get-grape-variety
  [id]
  (jdbc/execute-one! ds
                     (sql/format
                      {:select :* :from :grape_varieties :where [:= :id id]})
                     db-opts))

(defn update-grape-variety!
  [id name]
  (jdbc/execute-one! ds
                     (sql/format {:update :grape_varieties
                                  :set {:name name}
                                  :where [:= :id id]
                                  :returning :*})
                     db-opts))

(defn delete-grape-variety!
  [id]
  (jdbc/execute-one! ds
                     (sql/format {:delete-from :grape_varieties
                                  :where [:= :id id]})
                     db-opts))

;; Wine Grape Varieties Operations
(defn associate-grape-variety-with-wine
  [wine-id variety-id percentage]
  (jdbc/execute-one!
   ds
   (sql/format
    {:insert-into :wine_grape_varieties
     :values [{:wine_id wine-id :variety_id variety-id :percentage percentage}]
     :on-conflict [:wine_id :variety_id]
     :do-update-set {:percentage :excluded.percentage}
     :returning :*})
   db-opts))

(defn get-wine-grape-varieties
  [wine-id]
  (jdbc/execute! ds
                 (sql/format {:select [:wgv.* [:gv.name :variety_name]]
                              :from [[:wine_grape_varieties :wgv]]
                              :join [[:grape_varieties :gv]
                                     [:= :wgv.variety_id :gv.id]]
                              :where [:= :wgv.wine_id wine-id]
                              :order-by [:gv.name]})
                 db-opts))

(defn remove-grape-variety-from-wine
  [wine-id variety-id]
  (jdbc/execute-one! ds
                     (sql/format {:delete-from :wine_grape_varieties
                                  :where [:and [:= :wine_id wine-id]
                                          [:= :variety_id variety-id]]})
                     db-opts))

(defn mark-all-wines-unverified
  "Mark all wines as unverified for inventory verification"
  []
  (let [result (jdbc/execute-one! ds
                                  (sql/format {:update :wines
                                               :set {:verified false}})
                                  db-opts)]
    (:next.jdbc/update-count result)))

;; Cellar condition helpers

(defn create-cellar-condition!
  ([condition] (create-cellar-condition! ds condition))
  ([tx-or-ds condition]
   (some-> (jdbc/execute-one! tx-or-ds
                              (sql/format {:insert-into :cellar_conditions
                                           :values [(cellar-condition->db-row
                                                     condition)]
                                           :returning :*})
                              db-opts)
           db-cellar-condition->condition)))

(defn list-cellar-conditions
  ([] (list-cellar-conditions {}))
  ([{:keys [device_id limit]}]
   (let [limit (min (or limit 100) 500)
         query (cond-> {:select :*
                        :from :cellar_conditions
                        :order-by [[:measured_at :desc]]
                        :limit limit}
                 device_id (assoc :where [:= :device_id device_id]))]
     (->> (jdbc/execute! ds (sql/format query) db-opts)
          (map db-cellar-condition->condition)
          vec))))

(defn latest-cellar-condition
  [device-id]
  (some-> (jdbc/execute-one! ds
                             (sql/format {:select :*
                                          :from :cellar_conditions
                                          :where [:= :device_id device-id]
                                          :order-by [[:measured_at :desc]]
                                          :limit 1})
                             db-opts)
          db-cellar-condition->condition))

(defn latest-cellar-conditions
  "Return the most recent reading per device, or for a specific device when
  device-id is provided. Results are ordered by device_id ASC."
  ([] (latest-cellar-conditions nil))
  ([device-id]
   (let
     [sql-params
      (if device-id
        (sql/format {:select :*
                     :from :cellar_conditions
                     :where [:= :device_id device-id]
                     :order-by [[:measured_at :desc]]
                     :limit 1})
        ["SELECT DISTINCT ON (device_id) * FROM cellar_conditions WHERE device_id IS NOT NULL ORDER BY device_id ASC, measured_at DESC"])]
     (->> (jdbc/execute! ds sql-params db-opts)
          (map db-cellar-condition->condition)
          vec))))

(def ^:private bucket->seconds
  {"15m" 900  ;; 15 minutes
   "1h" 3600  ;; 1 hour
   "6h" 21600 ;; 6 hours
   "1d" 86400}) ;; 1 day

(defn cellar-condition-series
  "Return aggregated cellar readings bucketed by the requested interval. Results
  always include :device_id and :bucket_start (ISO string). Metrics include
  avg/min/max per bucket for available numeric fields."
  [{:keys [device_id from to bucket]}]
  (let [bucket-seconds (get bucket->seconds bucket 3600)
        bucket-expr
        [:raw
         (format "to_timestamp(floor(extract(epoch from measured_at)/%d)*%d)"
                 bucket-seconds
                 bucket-seconds)]
        conditions (cond-> []
                     device_id (conj [:= :device_id device_id])
                     from (conj [:>= :measured_at (->sql-timestamp from)])
                     to (conj [:<= :measured_at (->sql-timestamp to)]))
        where-clause (when (seq conditions) (into [:and] conditions))
        query (cond-> {:select [[:device_id :device_id]
                                [bucket-expr :bucket_start]
                                [[:avg :temperature_c] :avg_temperature_c]
                                [[:min :temperature_c] :min_temperature_c]
                                [[:max :temperature_c] :max_temperature_c]
                                [[:avg :humidity_pct] :avg_humidity_pct]
                                [[:min :humidity_pct] :min_humidity_pct]
                                [[:max :humidity_pct] :max_humidity_pct]
                                [[:avg :pressure_hpa] :avg_pressure_hpa]
                                [[:min :pressure_hpa] :min_pressure_hpa]
                                [[:max :pressure_hpa] :max_pressure_hpa]
                                [[:avg :illuminance_lux] :avg_illuminance_lux]
                                [[:min :illuminance_lux] :min_illuminance_lux]
                                [[:max :illuminance_lux] :max_illuminance_lux]
                                [[:avg :co2_ppm] :avg_co2_ppm]
                                [[:min :co2_ppm] :min_co2_ppm]
                                [[:max :co2_ppm] :max_co2_ppm]
                                [[:avg :battery_mv] :avg_battery_mv]
                                [[:min :battery_mv] :min_battery_mv]
                                [[:max :battery_mv] :max_battery_mv]]
                       :from :cellar_conditions
                       :group-by [:device_id bucket-expr]
                       :order-by [[:bucket_start :asc] [:device_id :asc]]}
                where-clause (assoc :where where-clause))
        rows (jdbc/execute! ds (sql/format query) db-opts)]
    (->> rows
         (map (fn [row]
                (cond-> row
                  (:bucket_start row) (update :bucket_start
                                              timestamp->iso-string))))
         vec)))

;; Device provisioning helpers
(defn get-device
  [device-id]
  (some-> (jdbc/execute-one! ds
                             (sql/format {:select :*
                                          :from :devices
                                          :where [:= :device_id device-id]})
                             db-opts)
          db-device->device))

(defn list-devices
  []
  (->> (jdbc/execute! ds
                      (sql/format {:select :*
                                   :from :devices
                                   :order-by [[:updated_at :desc]
                                              [:created_at :desc]]})
                      db-opts)
       (map db-device->device)
       vec))

(defn upsert-device-claim!
  [{:keys [device_id claim_code_hash firmware_version capabilities]}]
  (jdbc/with-transaction
   [tx ds]
   (let [existing (jdbc/execute-one! tx
                                     (sql/format {:select :*
                                                  :from :devices
                                                  :where [:= :device_id
                                                          device_id]
                                                  :for [:update]})
                                     db-opts)
         base-row (device->db-device {:device_id device_id
                                      :claim_code_hash claim_code_hash
                                      :firmware_version firmware_version
                                      :capabilities capabilities})
         result (cond (nil? existing)
                      (jdbc/execute-one!
                       tx
                       (sql/format {:insert-into :devices
                                    :values [(assoc base-row :status "pending")]
                                    :returning :*})
                       db-opts)
                      (= "blocked" (:status existing)) existing
                      (= "active" (:status existing)) existing
                      :else (jdbc/execute-one!
                             tx
                             (sql/format {:update :devices
                                          :set (merge base-row
                                                      {:status "pending"
                                                       :refresh_token_hash nil
                                                       :token_expires_at nil
                                                       :updated_at [:now]})
                                          :where [:= :device_id device_id]
                                          :returning :*})
                             db-opts))]
     (db-device->device result))))

(defn update-device!
  "Generic device update by device_id. Accepts already-cleaned fields."
  [device-id fields]
  (some-> (jdbc/execute-one! ds
                             (sql/format {:update :devices
                                          :set (merge (device->db-device fields)
                                                      {:updated_at [:now]})
                                          :where [:= :device_id device-id]
                                          :returning :*})
                             db-opts)
          db-device->device))

(defn activate-device!
  [{:keys [device_id refresh_token_hash token_expires_at]}]
  (update-device! device_id
                  {:status "active"
                   :refresh_token_hash refresh_token_hash
                   :token_expires_at token_expires_at}))

(defn set-device-refresh-token!
  [{:keys [device_id refresh_token_hash token_expires_at]}]
  (update-device! device_id
                  {:refresh_token_hash refresh_token_hash
                   :token_expires_at token_expires_at
                   :last_seen [:now]}))

(defn block-device!
  [device-id]
  (update-device!
   device-id
   {:status "blocked" :refresh_token_hash nil :token_expires_at nil}))

(defn unblock-device!
  "Move device to pending and clear tokens; used by admin unblock."
  [device-id]
  (update-device!
   device-id
   {:status "pending" :refresh_token_hash nil :token_expires_at nil}))

(defn delete-device!
  [device-id]
  (jdbc/execute-one! ds
                     (sql/format {:delete-from :devices
                                  :where [:= :device_id device-id]})
                     db-opts))

(defn touch-device!
  "Update last_seen and optionally token_expires_at (when a new access token is
  minted)."
  [device-id & [{:keys [token_expires_at]}]]
  (update-device! device-id
                  (cond-> {:last_seen [:now]}
                    token_expires_at (assoc :token_expires_at
                                            token_expires_at))))
