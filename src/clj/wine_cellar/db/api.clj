(ns wine-cellar.db.api
  (:require [clojure.string :as str]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [wine-cellar.db.schema :as schema]
            [wine-cellar.db.setup :refer [db-opts ds]])
  (:import [java.sql Date]
           [java.util Base64]))

;; Helper functions
(defn- ->sql-date
  [^String date-string]
  (some-> date-string
          (Date/valueOf)))

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
  [wine]
  (-> wine
      (update :style (partial schema/sql-cast :wine_style))
      (update :level (partial schema/sql-cast :wine_level))
      (update :label_image base64->bytes)
      (update :label_thumbnail base64->bytes)))

(defn db-wine->wine
  [db-wine]
  (some-> db-wine
          (update :label_image bytes->base64)
          (update :label_thumbnail bytes->base64)))

(defn ping-db
  "Simple function to test database connectivity"
  []
  (jdbc/execute-one! @ds ["SELECT 1 as result"] db-opts))

;; Wine operations
(defn create-wine
  [wine]
  (jdbc/execute-one! @ds
                     (sql/format {:insert-into :wines
                                  :values [(wine->db-wine wine)]})
                     db-opts))

(defn get-wine
  [id]
  (-> (jdbc/execute-one! @ds
                         (sql/format
                          {:select :* :from :wines :where [:= :id id]})
                         db-opts)
      db-wine->wine))

(defn get-all-wines-with-ratings
  []
  (let [wines (jdbc/execute!
               @ds
               (sql/format {:select [:id :producer :country :region :aoc
                                     :communal_aoc :classification :vineyard
                                     :level :name :vintage :style :location
                                     :purveyor :quantity :price :drink_from_year
                                     :drink_until_year :label_thumbnail
                                     :created_at :updated_at :latest_rating]
                            :from :wines_with_ratings
                            :order-by [[:created_at :desc]]})
               db-opts)]
    (mapv db-wine->wine wines)))

(defn update-wine!
  [id wine]
  (-> (jdbc/execute-one! @ds
                         (sql/format
                          {:update :wines
                           :set (assoc (wine->db-wine wine) :updated_at [:now])
                           :where [:= :id id]
                           :returning :*})
                         db-opts)
      db-wine->wine))

(defn update-wine-image!
  [id image-data]
  (some-> (jdbc/execute-one!
           @ds
           (sql/format {:update :wines
                        :set {:label_image (base64->bytes (:label_image
                                                           image-data))
                              :label_thumbnail (base64->bytes (:label_thumbnail
                                                               image-data))
                              :updated_at [:now]}
                        :where [:= :id id]
                        :returning :*})
           db-opts)
          db-wine->wine))

(defn adjust-quantity
  [id adjustment]
  (jdbc/execute-one! @ds
                     (sql/format {:update :wines
                                  :set {:quantity [:+ :quantity adjustment]
                                        :updated_at [:now]}
                                  :where [:= :id id]})
                     db-opts))

(defn delete-wine!
  [id]
  (jdbc/execute-one! @ds
                     (sql/format {:delete-from :wines :where [:= :id id]})
                     db-opts))

;; Classification operations
(defn create-or-update-classification
  "Creates a new classification or updates an existing one by combining levels"
  [classification]
  (jdbc/with-transaction
   [tx @ds]
   (let [existing-query
         {:select :*
          :from :wine_classifications
          :where [:and [:= :country (:country classification)]
                  [:= :region (:region classification)]
                  [:= [:coalesce :aoc ""] [:coalesce (:aoc classification) ""]]
                  [:= [:coalesce :communal_aoc ""]
                   [:coalesce (:communal_aoc classification) ""]]
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
                           :set {:levels (schema/->pg-array combined-levels)}
                           :where [:= :id (:id existing)]
                           :returning :*}]
         (jdbc/execute-one! tx (sql/format update-query) db-opts))
       ;; Create new classification
       (let [insert-query {:insert-into :wine_classifications
                           :values
                           [(update classification :levels schema/->pg-array)]
                           :returning :*}]
         (jdbc/execute-one! tx (sql/format insert-query) db-opts))))))

(defn get-classifications
  []
  (jdbc/execute! @ds
                 (sql/format {:select :*
                              :from :wine_classifications
                              :order-by [:country :region :aoc :communal_aoc]})
                 db-opts))

(defn get-regions-by-country
  [country]
  (jdbc/execute! @ds
                 (sql/format {:select [:distinct :region]
                              :from :wine_classifications
                              :where [:= :country country]
                              :order-by [:region]})
                 db-opts))

(defn get-aocs-by-region
  [country region]
  (jdbc/execute! @ds
                 (sql/format {:select [:distinct :aoc]
                              :from :wine_classifications
                              :where [:and [:= :country country]
                                      [:= :region region]]
                              :order-by [:aoc]})
                 db-opts))

;; Tasting Notes Operations
(defn create-tasting-note
  [note]
  (jdbc/execute-one! @ds
                     (sql/format {:insert-into :tasting_notes
                                  :values [(-> note
                                               (update :tasting_date ->sql-date)
                                               (assoc :updated_at [:now]))]
                                  :returning :*})
                     db-opts))

(defn update-tasting-note!
  [id note]
  (jdbc/execute-one! @ds
                     (sql/format {:update :tasting_notes
                                  :set (-> note
                                           (update :tasting_date ->sql-date)
                                           (assoc :updated_at [:now]))
                                  :where [:= :id id]
                                  :returning :*})
                     db-opts))

(defn get-tasting-note
  [id]
  (jdbc/execute-one! @ds
                     (sql/format
                      {:select :* :from :tasting_notes :where [:= :id id]})
                     db-opts))

(defn get-tasting-notes-by-wine
  [wine-id]
  (jdbc/execute! @ds
                 (sql/format {:select :*
                              :from :tasting_notes
                              :where [:= :wine_id wine-id]
                              :order-by [[:tasting_date :desc]]})
                 db-opts))

(defn delete-tasting-note!
  [id]
  (jdbc/execute-one! @ds
                     (sql/format {:delete-from :tasting_notes
                                  :where [:= :id id]})
                     db-opts))

