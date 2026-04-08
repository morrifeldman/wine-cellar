(ns wine-cellar.db.api
  (:require [clojure.string :as str]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [jsonista.core :as json]
            [wine-cellar.db.connection :refer [db-opts ds]])
  (:import [java.sql Date Timestamp]
           [java.time Instant]
           [java.util Base64]))

;; Query helpers — wrap the recurring `(jdbc/execute*! ds (sql/format ...)
;; db-opts)`
;; shape so call sites can stay focused on the honeysql map.
(defn- q-one
  ([query] (q-one ds query))
  ([tx-or-ds query] (jdbc/execute-one! tx-or-ds (sql/format query) db-opts)))

(defn- q-many
  ([query] (q-many ds query))
  ([tx-or-ds query] (jdbc/execute! tx-or-ds (sql/format query) db-opts)))

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
  [^String input]
  (when input
    (try (Timestamp/from (Instant/parse input))
         (catch Exception _
           (try (let [date (java.time.LocalDate/parse input)]
                  (Timestamp/from (-> date
                                      (.atStartOfDay (java.time.ZoneId/of
                                                      "UTC"))
                                      .toInstant)))
                (catch Exception _ nil))))))

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
  [{:keys [style label_image label_thumbnail back_label_image purchase_date
           metadata]
    :as wine}]
  (cond-> wine
    purchase_date (update :purchase_date ->sql-date)
    style (update :style (partial sql-cast :wine_style))
    metadata (update :metadata
                     #(sql-cast :jsonb (json/write-value-as-string %)))
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

(defn sensor-reading->db-row
  [{:keys [measured_at temperatures] :as condition}]
  (cond-> condition
    measured_at (update :measured_at ->sql-timestamp)
    temperatures (update :temperatures
                         #(sql-cast :jsonb (json/write-value-as-string %)))))

(defn db-sensor-reading->reading
  [{:keys [measured_at created_at] :as row}]
  (-> row
      (cond-> measured_at (update :measured_at timestamp->iso-string))
      (cond-> created_at (update :created_at timestamp->iso-string))))

(defn- instant->sql-timestamp
  [^Instant instant]
  (some-> instant
          Timestamp/from))

(defn device->db-device
  [{:keys [capabilities sensor_config token_expires_at last_seen] :as device}]
  (cond-> device
    capabilities (update :capabilities
                         #(sql-cast :jsonb (json/write-value-as-string %)))
    sensor_config (update :sensor_config
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

(defn get-db-schema
  []
  (->> (q-many {:select [:t.table_name :t.table_type :c.column_name
                         :c.data_type]
                :from [[:information_schema.tables :t]]
                :join [[:information_schema.columns :c]
                       [:and [:= :t.table_name :c.table_name]
                        [:= :t.table_schema :c.table_schema]]]
                :where [:and [:= :t.table_schema "public"]
                        [:in :t.table_type ["BASE TABLE" "VIEW"]]]
                :order-by [:t.table_name :c.ordinal_position]})
       (group-by (juxt :table_name :table_type))
       (map (fn [[[table-name table-type] columns]]
              {:table_name table-name
               :table_type table-type
               :columns (mapv #(select-keys % [:column_name :data_type])
                              columns)}))
       (sort-by :table_name)
       vec))

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
   (q-one tx-or-ds
          {:insert-into :wines :values [(wine->db-wine wine)] :returning :*})))

(defn create-conversation!
  "Create a new AI conversation row."
  ([conversation] (create-conversation! ds conversation))
  ([tx-or-ds {:keys [user_email] :as conversation}]
   (assert user_email "user_email is required to create a conversation")
   (let [db-row (-> conversation
                    (dissoc :user_email)
                    (conversation->db-conversation)
                    (assoc :user_email user_email))]
     (some-> (q-one
              tx-or-ds
              {:insert-into :ai_conversations :values [db-row] :returning :*})
             db-conversation->conversation))))

(defn list-conversations-for-user
  "Return conversations for the given user ordered by recent activity.
   Optionally filter by search text matching title or message content.
   chat-type defaults to 'wine' when not provided."
  ([user-email] (list-conversations-for-user user-email nil nil))
  ([user-email search-text]
   (list-conversations-for-user user-email search-text nil))
  ([user-email search-text chat-type]
   (let [resolved-type (or chat-type "wine")
         base-query {:select [:c.*]
                     :from [[:ai_conversations :c]]
                     :where [:and [:= :c.user_email user-email]
                             [:= :c.chat_type resolved-type]]
                     :order-by [[:c.pinned :desc] [:c.last_message_at :desc]
                                [:c.created_at :desc]]}
         query
         (if (str/blank? search-text)
           base-query
           (let [ts-query [:websearch_to_tsquery [:cast "english" :regconfig]
                           search-text]
                 pattern (str "%" search-text "%")
                 escaped (-> search-text
                             (str/replace #"[\\.+*?()\[\]{}^$|]" "\\\\$0")
                             (str/replace "'" "''"))
                 occurrence-sql
                 (str "(SELECT COUNT(*) FROM ai_conversation_messages m2, "
                      "LATERAL regexp_matches(m2.content, '" escaped
                      "', 'gi') matches " "WHERE m2.conversation_id = c.id)")]
             {:select [[[:coalesce [:raw occurrence-sql] 0] :match_count] :c.*]
              :from [[:ai_conversations :c]]
              :left-join [[:ai_conversation_messages :m]
                          [:and [:= :c.id :m.conversation_id]
                           [:raw ["m.fts_content @@ " ts-query]]]]
              :where [:and [:= :c.user_email user-email]
                      [:= :c.chat_type resolved-type]]
              :group-by [:c.id]
              :having [:or [:ilike :c.title pattern] [:> [:count :m.id] 0]]
              :order-by [[:match_count :desc] [:c.last_message_at :desc]]}))
         sql-vec (sql/format query)]
     (->> (jdbc/execute! ds sql-vec db-opts)
          (map db-conversation->conversation)
          vec))))

(defn get-conversation
  [conversation-id]
  (some-> (q-one
           {:select :* :from :ai_conversations :where [:= :id conversation-id]})
          db-conversation->conversation))

(defn append-conversation-message!
  "Insert a message and update parent metadata atomically."
  [{:keys [conversation_id tokens_used] :as message}]
  (jdbc/with-transaction
   [tx ds]
   (let [inserted (q-one tx
                         {:insert-into :ai_conversation_messages
                          :values [(conversation-message->db-message message)]
                          :returning :*})
         token-inc (or tokens_used 0)]
     (q-one tx
            {:update :ai_conversations
             :set {:last_message_at [:now]
                   :updated_at [:now]
                   :total_tokens_used [:+ :total_tokens_used token-inc]}
             :where [:= :id conversation_id]
             :returning :*})
     (db-conversation-message->message inserted))))

(defn- refresh-conversation-metadata!
  "Recalculate and persist the aggregate metadata for a conversation.
   Returns the updated conversation map."
  [tx conversation-id]
  (let [{:keys [total_tokens last_created_at]}
        (q-one tx
               {:select [[[:coalesce [:sum :tokens_used] 0] :total_tokens]
                         [[:max :created_at] :last_created_at]]
                :from :ai_conversation_messages
                :where [:= :conversation_id conversation-id]})
        metadata {:total_tokens_used total_tokens
                  :updated_at [:now]
                  :last_message_at (or last_created_at [:now])}]
    (some-> (q-one tx
                   {:update :ai_conversations
                    :set metadata
                    :where [:= :id conversation-id]
                    :returning :*})
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
         updated (some-> (q-one tx
                                {:update :ai_conversation_messages
                                 :set set-map
                                 :where [:and [:= :id message_id]
                                         [:= :conversation_id conversation_id]]
                                 :returning :*})
                         db-conversation-message->message)]
     (when-not updated
       (throw (ex-info "Conversation message not found"
                       {:status 404
                        :conversation-id conversation_id
                        :message-id message_id})))
     (let [deleted (when truncate_after?
                     (q-many tx
                             {:delete-from :ai_conversation_messages
                              :where [:and [:= :conversation_id conversation_id]
                                      [:> :id message_id]]
                              :returning [:id :tokens_used]}))
           conversation (refresh-conversation-metadata! tx conversation_id)]
       {:message updated
        :deleted-message-ids (vec (map :id deleted))
        :conversation conversation}))))

(defn list-messages-for-conversation
  [conversation-id]
  (q-many {:select :*
           :from :ai_conversation_messages
           :where [:= :conversation_id conversation-id]
           :order-by [[:created_at :asc] [:id :asc]]}))

(defn delete-conversation!
  ([conversation-id] (delete-conversation! ds conversation-id))
  ([tx-or-ds conversation-id]
   (q-one tx-or-ds
          {:delete-from :ai_conversations
           :where [:= :id conversation-id]
           :returning :id})))

(defn update-conversation!
  ([conversation-id updates] (update-conversation! ds conversation-id updates))
  ([tx-or-ds conversation-id updates]
   (let [sanitized
         (-> updates
             (dissoc :id :user_email :created_at :updated_at :last_message_at)
             (conversation->db-conversation))
         set-map (assoc sanitized :updated_at [:now])]
     (if (seq sanitized)
       (some-> (q-one tx-or-ds
                      {:update :ai_conversations
                       :set set-map
                       :where [:= :id conversation-id]
                       :returning :*})
               db-conversation->conversation)
       (get-conversation conversation-id)))))

(defn wine-exists? [id] (q-one {:select :id :from :wines :where [:= :id id]}))

(def wine-list-fields
  [:id :producer :country :region :appellation :appellation_tier :classification
   :vineyard :designation :name :vintage :style :closure_type :bottle_format
   :location :purveyor :quantity :original_quantity :price :drink_from_year
   :drink_until_year :alcohol_percentage :disgorgement_year :dosage
   :label_thumbnail :created_at :updated_at :verified :purchase_date
   :latest_internal_rating :average_external_rating :varieties :metadata])

(defn get-wines-for-list
  []
  ;; Listing columns explicitly since the only image we are getting is the
  ;; label_thumbnail. The :varieties JSON is auto-parsed by our PgObject
  ;; ext.
  (mapv db-wine->wine
        (q-many {:select wine-list-fields
                 :from :enriched_wines
                 :order-by [[:created_at :desc]]})))

(defn get-all-metadata-keys
  []
  (->> (q-many {:select-distinct [[[:jsonb_object_keys :metadata]]]
                :from :wines
                :where [:not [:= :metadata nil]]})
       (map :jsonb_object_keys)
       vec))

(defn get-wine
  [id include-images?]
  (-> (q-one {:select (if include-images? :* wine-list-fields)
              :from :enriched_wines
              :where [:= :id id]})
      db-wine->wine))

(def enriched-wine-fields-for-ai
  (conj wine-list-fields :tasting_notes :tasting_window_commentary :ai_summary))

(defn get-enriched-wines-by-ids
  "Get enriched wines with full tasting notes for AI chat context (no images)"
  [wine-ids]
  (when (seq wine-ids)
    (mapv db-wine->wine
          (q-many {:select enriched-wine-fields-for-ai
                   :from :enriched_wines
                   :where [:in :id wine-ids]
                   :order-by [[:created_at :desc]]}))))

(defn update-wine!
  [id wine]
  (-> (q-one {:update :wines
              :set (assoc (wine->db-wine wine) :updated_at [:now])
              :where [:= :id id]
              :returning :*})
      db-wine->wine))

(defn adjust-quantity
  ([id adjustment] (adjust-quantity id adjustment {}))
  ([id adjustment {:keys [reason notes]}]
   (jdbc/with-transaction
    [tx ds]
    (let [wine (q-one tx
                      {:select [:quantity :original_quantity]
                       :from :wines
                       :where [:= :id id]})
          current-quantity (:quantity wine 0)
          current-original-quantity (:original_quantity wine 0)
          new-quantity (+ current-quantity adjustment)
          actual-reason (or reason (if (neg? adjustment) "drunk" "return"))
          new-original-quantity (if (= actual-reason "restock")
                                  (+ current-original-quantity adjustment)
                                  current-original-quantity)
          update-map (cond-> {:quantity new-quantity :updated_at [:now]}
                       (= actual-reason "Restock")
                       (assoc :original_quantity
                              [:+ [:coalesce :original_quantity 0]
                               adjustment]))]
      (q-one tx
             {:insert-into :inventory_history
              :values [{:wine_id id
                        :change_amount adjustment
                        :reason actual-reason
                        :previous_quantity current-quantity
                        :new_quantity new-quantity
                        :original_quantity new-original-quantity
                        :notes notes}]})
      (q-one tx {:update :wines :set update-map :where [:= :id id]})))))

(defn get-inventory-history
  [wine-id]
  (q-many {:select :*
           :from :inventory_history
           :where [:= :wine_id wine-id]
           :order-by [[:occurred_at :desc] [:id :desc]]}))

(defn update-inventory-history!
  [id updates]
  (let [valid-updates (select-keys updates [:occurred_at :reason :notes])
        set-map (cond-> valid-updates
                  (:occurred_at valid-updates) (update :occurred_at
                                                       ->sql-timestamp))]
    (when (seq set-map)
      (q-one {:update :inventory_history
              :set set-map
              :where [:= :id id]
              :returning :*}))))

(defn delete-inventory-history!
  [id]
  (q-one {:delete-from :inventory_history :where [:= :id id]}))

(defn delete-wine! [id] (q-one {:delete-from :wines :where [:= :id id]}))

;; Classification operations
(defn create-or-update-classification
  "Returns an existing classification matching the given fields, or creates a new one."
  ([classification]
   (jdbc/with-transaction [tx ds]
                          (create-or-update-classification tx classification)))
  ([tx classification]
   (or (q-one tx
              {:select :*
               :from :wine_classifications
               :where [:and [:= :country (:country classification)]
                       [:= :region (:region classification)]
                       [:= [:coalesce :appellation ""]
                        [:coalesce (:appellation classification) ""]]
                       [:= [:coalesce :appellation_tier ""]
                        [:coalesce (:appellation_tier classification) ""]]
                       [:= [:coalesce :classification ""]
                        [:coalesce (:classification classification) ""]]]})
       (q-one tx
              {:insert-into :wine_classifications
               :values [classification]
               :returning :*}))))

(defn get-classifications
  []
  (q-many {:select :*
           :from :wine_classifications
           :order-by [:country :region :appellation]}))

(defn get-classification
  [id]
  (q-one {:select :* :from :wine_classifications :where [:= :id id]}))

(defn update-classification!
  [id classification]
  (q-one {:update :wine_classifications
          :set classification
          :where [:= :id id]
          :returning :*}))

(defn delete-classification!
  [id]
  (q-one {:delete-from :wine_classifications :where [:= :id id]}))

(defn get-regions-by-country
  [country]
  (q-many {:select [:distinct :region]
           :from :wine_classifications
           :where [:= :country country]
           :order-by [:region]}))

(defn get-appellations-by-region
  [country region]
  (q-many {:select [:distinct :appellation]
           :from :wine_classifications
           :where [:and [:= :country country] [:= :region region]]
           :order-by [:appellation]}))

;; Tasting Notes Operations
(defn create-tasting-note
  ([note] (create-tasting-note ds note))
  ([ds-or-tx note]
   (q-one ds-or-tx
          {:insert-into :tasting_notes
           :values [(-> note
                        tasting-note->db-tasting-note
                        (assoc :updated_at [:now]))]
           :returning :*})))

(defn update-tasting-note!
  [id note]
  (tap> ["update-tasting-note!" id note])
  (q-one {:update :tasting_notes
          :set (-> note
                   tasting-note->db-tasting-note
                   (assoc :updated_at [:now]))
          :where [:= :id id]
          :returning :*}))

(defn get-tasting-note
  [id]
  (q-one {:select :* :from :tasting_notes :where [:= :id id]}))

(defn get-tasting-notes-by-wine
  [wine-id]
  (q-many {:select :*
           :from :tasting_notes
           :where [:= :wine_id wine-id]
           :order-by [[:tasting_date :desc]]}))

(defn delete-tasting-note!
  [id]
  (q-one {:delete-from :tasting_notes :where [:= :id id]}))

(defn get-tasting-note-sources
  "Returns a list of unique source names from external tasting notes"
  []
  (->> (q-many {:select-distinct [:source]
                :from :tasting_notes
                :where [:and [:= :is_external true] [:not [:= :source nil]]
                        [:not [:= :source ""]]]
                :order-by [[:source :asc]]})
       (map :source)))

;; Blind Tasting Operations
(defn get-blind-tastings
  "Returns all blind tasting notes (both linked and unlinked)"
  []
  (q-many {:select [:tn.* [:w.producer :wine_producer] [:w.name :wine_name]
                    [:w.vintage :wine_vintage] [:w.country :wine_country]
                    [:w.region :wine_region]]
           :from [[:tasting_notes :tn]]
           :left-join [[:wines :w] [:= :tn.wine_id :w.id]]
           :where [:or [:= :tn.wine_id nil] [:= :tn.is_blind true]]
           :order-by [[:tn.created_at :desc]]}))

(defn create-blind-tasting
  "Create a blind tasting note (no wine_id)"
  [note]
  (q-one {:insert-into :tasting_notes
          :values [(-> note
                       (dissoc :wine_id)
                       tasting-note->db-tasting-note
                       (assoc :updated_at [:now]))]
          :returning :*}))

(defn link-blind-tasting
  "Link a blind tasting note to a wine and mark it as blind"
  [note-id wine-id]
  (q-one {:update :tasting_notes
          :set {:wine_id wine-id :is_blind true :updated_at [:now]}
          :where [:and [:= :id note-id] [:= :wine_id nil]]
          :returning :*}))

;; Grape Varieties Operations
(defn create-grape-variety
  [name]
  (q-one {:insert-into :grape_varieties :values [{:name name}] :returning :*}))

(defn get-all-grape-varieties
  []
  (q-many {:select :* :from :grape_varieties :order-by [:name]}))

(defn get-grape-variety
  [id]
  (q-one {:select :* :from :grape_varieties :where [:= :id id]}))

(defn update-grape-variety!
  [id name]
  (q-one {:update :grape_varieties
          :set {:name name}
          :where [:= :id id]
          :returning :*}))

(defn delete-grape-variety!
  [id]
  (q-one {:delete-from :grape_varieties :where [:= :id id]}))

;; Wine Grape Varieties Operations
(defn associate-grape-variety-with-wine
  [wine-id variety-id percentage]
  (q-one {:insert-into :wine_grape_varieties
          :values
          [{:wine_id wine-id :variety_id variety-id :percentage percentage}]
          :on-conflict [:wine_id :variety_id]
          :do-update-set {:percentage :excluded.percentage}
          :returning :*}))

(defn get-wine-grape-varieties
  [wine-id]
  (q-many {:select [:wgv.* [:gv.name :variety_name]]
           :from [[:wine_grape_varieties :wgv]]
           :join [[:grape_varieties :gv] [:= :wgv.variety_id :gv.id]]
           :where [:= :wgv.wine_id wine-id]
           :order-by [:gv.name]}))

(defn remove-grape-variety-from-wine
  [wine-id variety-id]
  (q-one {:delete-from :wine_grape_varieties
          :where [:and [:= :wine_id wine-id] [:= :variety_id variety-id]]}))

(defn mark-all-wines-unverified
  "Mark all wines as unverified for inventory verification"
  []
  (:next.jdbc/update-count (q-one {:update :wines :set {:verified false}})))

;; Sensor reading helpers

(defn create-sensor-reading!
  ([reading] (create-sensor-reading! ds reading))
  ([tx-or-ds reading]
   (jdbc/with-transaction
    [tx tx-or-ds]
    (let [row (sensor-reading->db-row reading)
          inserted (some-> (q-one tx
                                  {:insert-into :sensor_readings
                                   :values [row]
                                   :returning :*})
                           db-sensor-reading->reading)]
      (when-let [temps (:temperatures reading)]
        (let [reading-id (:id inserted)]
          (doseq [[addr temp] temps]
            (q-one tx
                   {:insert-into :sensor_temperatures
                    :values [{:reading_id reading-id
                              :sensor_addr (name addr)
                              :temperature_c (Double/parseDouble (str
                                                                  temp))}]}))))
      inserted))))

(defn list-sensor-readings
  ([] (list-sensor-readings {}))
  ([{:keys [device_id limit]}]
   (->> (q-many (cond-> {:select :*
                         :from :sensor_readings
                         :order-by [[:measured_at :desc]]
                         :limit (min (or limit 100) 500)}
                  device_id (assoc :where [:= :device_id device_id])))
        (map db-sensor-reading->reading)
        vec)))

(defn latest-sensor-reading
  [device-id]
  (some-> (q-one {:select :*
                  :from :sensor_readings
                  :where [:= :device_id device-id]
                  :order-by [[:measured_at :desc]]
                  :limit 1})
          db-sensor-reading->reading))

(defn latest-sensor-readings
  "Return the most recent reading per device, or for a specific device when
  device-id is provided. Results are ordered by device_id ASC."
  ([] (latest-sensor-readings nil))
  ([device-id]
   (let
     [rows
      (if device-id
        (q-many {:select :*
                 :from :sensor_readings
                 :where [:= :device_id device-id]
                 :order-by [[:measured_at :desc]]
                 :limit 1})
        (jdbc/execute!
         ds
         ["SELECT DISTINCT ON (device_id) * FROM sensor_readings WHERE device_id IS NOT NULL ORDER BY device_id ASC, measured_at DESC"]
         db-opts))]
     (->> rows
          (map db-sensor-reading->reading)
          vec))))

(def ^:private bucket->seconds
  {"15m" 900  ;; 15 minutes
   "1h" 3600  ;; 1 hour
   "6h" 21600 ;; 6 hours
   "1d" 86400}) ;; 1 day

(defn sensor-reading-series
  "Return aggregated sensor readings bucketed by the requested interval. Results
  always include :device_id and :bucket_start (ISO string). Metrics include
  avg/min/max per bucket for available numeric fields.
  Temperature aggregation: computes per-sensor averages via sensor_temperatures table
  and returns avg_temperatures as a JSONB map {sensor_key: avg_value}."
  [{:keys [device_id from to bucket]}]
  (let
    [bucket-seconds (get bucket->seconds bucket 3600)
     bucket-expr (format
                  "to_timestamp(floor(extract(epoch from measured_at)/%d)*%d)"
                  bucket-seconds
                  bucket-seconds)
     conditions (cond-> ["1=1"]
                  device_id (conj "sr.device_id = ?")
                  from (conj "sr.measured_at >= ?::timestamptz")
                  to (conj "sr.measured_at <= ?::timestamptz"))
     params (cond-> []
              device_id (conj device_id)
              from (conj from)
              to (conj to))
     where-clause (str/join " AND " conditions)
     sql-str
     (str
      "WITH buckets AS ("
      "  SELECT device_id, "
      bucket-expr
      " AS bucket_start,"
      "    AVG(humidity_pct) AS avg_humidity_pct,"
      "    MIN(humidity_pct) AS min_humidity_pct,"
      "    MAX(humidity_pct) AS max_humidity_pct,"
      "    AVG(pressure_hpa) AS avg_pressure_hpa,"
      "    MIN(pressure_hpa) AS min_pressure_hpa,"
      "    MAX(pressure_hpa) AS max_pressure_hpa,"
      "    AVG(illuminance_lux) AS avg_illuminance_lux,"
      "    MIN(illuminance_lux) AS min_illuminance_lux,"
      "    MAX(illuminance_lux) AS max_illuminance_lux,"
      "    AVG(co2_ppm) AS avg_co2_ppm,"
      "    MIN(co2_ppm) AS min_co2_ppm,"
      "    MAX(co2_ppm) AS max_co2_ppm,"
      "    AVG(battery_mv) AS avg_battery_mv,"
      "    MIN(battery_mv) AS min_battery_mv,"
      "    MAX(battery_mv) AS max_battery_mv"
      "  FROM sensor_readings sr"
      "  WHERE "
      where-clause
      "  GROUP BY device_id, bucket_start"
      "), temp_agg AS ("
      "  SELECT sr.device_id, "
      bucket-expr
      " AS bucket_start,"
      "    st.sensor_addr,"
      "    AVG(st.temperature_c) AS avg_val,"
      "    MIN(st.temperature_c) AS min_val,"
      "    MAX(st.temperature_c) AS max_val"
      "  FROM sensor_readings sr"
      "  JOIN sensor_temperatures st ON sr.id = st.reading_id"
      "  WHERE "
      where-clause
      "  GROUP BY sr.device_id, bucket_start, st.sensor_addr"
      "), temp_json AS (" "  SELECT device_id, bucket_start,"
      "    jsonb_object_agg(sensor_addr, round(avg_val::numeric, 2)) AS avg_temperatures,"
      "    jsonb_object_agg(sensor_addr, round(min_val::numeric, 2)) AS min_temperatures,"
      "    jsonb_object_agg(sensor_addr, round(max_val::numeric, 2)) AS max_temperatures"
      "  FROM temp_agg"
      "  GROUP BY device_id, bucket_start" ")"
      " SELECT b.device_id, b.bucket_start,"
      "   t.avg_temperatures, t.min_temperatures, t.max_temperatures,"
      "   b.avg_humidity_pct, b.min_humidity_pct, b.max_humidity_pct,"
      "   b.avg_pressure_hpa, b.min_pressure_hpa, b.max_pressure_hpa,"
      "   b.avg_illuminance_lux, b.min_illuminance_lux, b.max_illuminance_lux,"
      "   b.avg_co2_ppm, b.min_co2_ppm, b.max_co2_ppm,"
      "   b.avg_battery_mv, b.min_battery_mv, b.max_battery_mv"
      " FROM buckets b"
      " LEFT JOIN temp_json t ON b.device_id = t.device_id AND b.bucket_start = t.bucket_start"
      " ORDER BY b.bucket_start ASC, b.device_id ASC")
     rows
     (jdbc/execute! ds (into [sql-str] (vec (concat params params))) db-opts)]
    (->> rows
         (map (fn [row]
                (cond-> row
                  (:bucket_start row) (update :bucket_start
                                              timestamp->iso-string))))
         vec)))

;; Device provisioning helpers
(defn get-device
  [device-id]
  (some-> (q-one {:select :* :from :devices :where [:= :device_id device-id]})
          db-device->device))

(defn list-devices
  []
  (->> (q-many {:select :*
                :from :devices
                :order-by [[:updated_at :desc] [:created_at :desc]]})
       (map db-device->device)
       vec))

(defn upsert-device-claim!
  [{:keys [device_id claim_code_hash firmware_version capabilities]}]
  (jdbc/with-transaction
   [tx ds]
   (let [existing (q-one tx
                         {:select :*
                          :from :devices
                          :where [:= :device_id device_id]
                          :for [:update]})
         base-row (device->db-device {:device_id device_id
                                      :claim_code_hash claim_code_hash
                                      :firmware_version firmware_version
                                      :capabilities capabilities})
         result (cond (nil? existing)
                      (q-one tx
                             {:insert-into :devices
                              :values [(assoc base-row :status "pending")]
                              :returning :*})
                      (#{"blocked" "active"} (:status existing)) existing
                      :else (q-one tx
                                   {:update :devices
                                    :set (merge base-row
                                                {:status "pending"
                                                 :refresh_token_hash nil
                                                 :token_expires_at nil
                                                 :updated_at [:now]})
                                    :where [:= :device_id device_id]
                                    :returning :*}))]
     (db-device->device result))))

(defn update-device!
  "Generic device update by device_id. Accepts already-cleaned fields."
  [device-id fields]
  (some-> (q-one {:update :devices
                  :set (merge (device->db-device fields) {:updated_at [:now]})
                  :where [:= :device_id device-id]
                  :returning :*})
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
  (q-one {:delete-from :devices :where [:= :device_id device-id]}))

(defn touch-device!
  "Update last_seen and optionally token_expires_at (when a new access token is
  minted)."
  [device-id & [{:keys [token_expires_at]}]]
  (update-device! device-id
                  (cond-> {:last_seen [:now]}
                    token_expires_at (assoc :token_expires_at
                                            token_expires_at))))

;; Bar: Spirits
(defn- spirit->db-spirit
  [{:keys [purchase_date] :as spirit}]
  (cond-> spirit purchase_date (update :purchase_date ->sql-date)))

(defn get-spirits
  []
  (q-many {:select :* :from :spirits :order-by [[:created_at :desc]]}))

(defn get-spirit [id] (q-one {:select :* :from :spirits :where [:= :id id]}))

(defn create-spirit!
  [spirit]
  (q-one
   {:insert-into :spirits :values [(spirit->db-spirit spirit)] :returning :*}))

(defn update-spirit!
  [id spirit]
  (q-one {:update :spirits
          :set (assoc (spirit->db-spirit spirit) :updated_at [:now])
          :where [:= :id id]
          :returning :*}))

(defn delete-spirit! [id] (q-one {:delete-from :spirits :where [:= :id id]}))

;; Bar: Inventory Items
(defn get-bar-inventory-items
  []
  (q-many {:select :*
           :from :bar_inventory_items
           :order-by [:category :sort_order :name]}))

(defn update-bar-inventory-item!
  [id fields]
  (q-one {:update :bar_inventory_items
          :set (select-keys fields [:have_it :name :category :sort_order])
          :where [:= :id id]
          :returning :*}))

(defn create-bar-inventory-item!
  [item]
  (q-one {:insert-into :bar_inventory_items
          :values [(select-keys item [:name :category :sort_order :have_it])]
          :returning :*}))

(defn delete-bar-inventory-item!
  [id]
  (pos? (:next.jdbc/update-count (q-one {:delete-from :bar_inventory_items
                                         :where [:= :id id]}))))

;; Bar: Cocktail Recipes
(defn- recipe->db-recipe
  [{:keys [ingredients tags] :as recipe}]
  (cond-> recipe
    ingredients (update :ingredients
                        #(sql-cast :jsonb (json/write-value-as-string %)))
    tags (update :tags #(->pg-array %))))

(defn get-cocktail-recipes
  []
  (q-many {:select :* :from :cocktail_recipes :order-by [[:created_at :desc]]}))

(defn get-cocktail-recipe
  [id]
  (q-one {:select :* :from :cocktail_recipes :where [:= :id id]}))

(defn create-cocktail-recipe!
  [recipe]
  (q-one {:insert-into :cocktail_recipes
          :values [(recipe->db-recipe recipe)]
          :returning :*}))

(defn update-cocktail-recipe!
  [id recipe]
  (q-one {:update :cocktail_recipes
          :set (assoc (recipe->db-recipe recipe) :updated_at [:now])
          :where [:= :id id]
          :returning :*}))

(defn delete-cocktail-recipe!
  [id]
  (q-one {:delete-from :cocktail_recipes :where [:= :id id]}))
