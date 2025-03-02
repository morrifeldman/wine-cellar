(ns wine-cellar.db
  (:require [wine-cellar.common :as common]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [org.postgresql.jdbc PgArray]
           [java.sql Date]))

(extend-protocol rs/ReadableColumn
  PgArray (read-column-by-label [^PgArray v _]
            (.getArray v))
  PgArray (read-column-by-index [^PgArray v _2 _3]
            (.getArray v)))

(defn get-password-from-pass [password-path]
  (let [result (sh/sh "pass" password-path)]
    (if (= 0 (:exit result))
      (string/trim (:out result))
      (throw (ex-info (str "Failed to retrieve password from pass: " (:err result))
                      {:type :password-retrieval-error
                       :path password-path})))))

(def password-pass-name "wine_cellar/db")

(def db
  {:dbtype "postgresql"
   :dbname "wine_cellar"
   :user "wine_cellar"
   :password (get-password-from-pass password-pass-name)})

(def ds
  (jdbc/get-datasource db))

#_(jdbc/execute-one! ds ["select 1"])

(def db-opts
  {:builder-fn rs/as-unqualified-maps})

(def create-wine-level-type
  {:raw ["DO $$ BEGIN "
         "IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'wine_level') THEN "
         "CREATE TYPE wine_level AS ENUM "
         [:inline (vec common/wine-levels)]
         "; END IF; END $$;"]})

(def create-wine-style-type
  {:raw ["DO $$ BEGIN "
         "IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'wine_style') THEN "
         "CREATE TYPE wine_style AS ENUM "
         [:inline (vec common/wine-styles)]
         "; END IF; END $$;"]})

(def classifications-table-schema
  {:create-table [:wine_classifications :if-not-exists]
   :with-columns
   [[:id :serial :primary-key]
    [:country :varchar [:not nil]]
    [:region :varchar [:not nil]]
    [:aoc :varchar]
    [:communal_aoc :varchar]
    [:classification :varchar]
    [:vineyard :varchar]
    [:levels :wine_level :array]
    [:created_at :timestamp [:default [:now]]]
    [[:constraint :wine_classifications_natural_key]
     :unique
     [:composite :country :region :aoc :communal_aoc
      :classification :vineyard :levels]]]})

(def wines-table-schema
  {:create-table [:wines :if-not-exists]
   :with-columns
   [[:id :serial :primary-key]
    [:producer :varchar]
    [:country :varchar [:not nil]]
    [:region :varchar [:not nil]]
    [:aoc :varchar]
    [:communal_aoc :varchar]
    [:classification :varchar]
    [:vineyard :varchar]
    [:level :wine_level]  ; nullable by default
    [:name :varchar]
    [:vintage :integer]
    [:styles :wine_style :array]
    [:location :varchar]
    [:quantity :integer [:not nil] [:default 0]]
    [:price :decimal [10 2]]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]]})

(def tasting-notes-table-schema
  {:create-table [:tasting_notes :if-not-exists]
   :with-columns
   [[:id :serial :primary-key]
    [:wine_id :integer [:not nil]]
    [:tasting_date :date [:not nil]]
    [:notes :text [:not nil]]
    [:rating :integer [:check [:and [:>= :rating 1] [:<= :rating 100]]]]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]
    [[:foreign-key :wine_id]
     :references [:entity :wines] [:nest :id]
     :on-delete :cascade]]})

(def wines-with-ratings-view-schema
  {:create-or-replace-view [:wines-with-ratings]
   :select [:w.*
            [{:select :tn.rating
             :from [[:tasting_notes :tn]]
             :where [:= :tn.wine_id :w.id]
             :order-by [[:tn.tasting_date :desc]]
             :limit [:inline 1]} :latest_rating]]
   :from [[:wines :w]]})

(defn- ->pg-array [coll]
  {:raw (str "'{" (string/join "," coll) "}'")})

(defn sql-cast [sql-type field]
  [:cast field sql-type])

(defn create-wine [wine]
  (jdbc/execute-one! ds
                     (sql/format
                       {:insert-into :wines
                        :values [(cond-> wine
                                   true (update :styles ->pg-array)
                                   (:level wine) (update :level
                                                         (partial sql-cast :wine_level)))]})
                     db-opts))

(defn get-wine [id]
  (jdbc/execute-one! ds
                     (sql/format
                      {:select :*
                       :from :wines
                       :where [:= :id id]})
                     db-opts))

(defn get-all-wines []
  (jdbc/execute! ds
                 (sql/format
                  {:select :*
                   :from :wines
                   :order-by [[:created_at :desc]]})
                 db-opts))

(defn get-all-wines-with-ratings []
  (jdbc/execute! ds
                 (sql/format
                  {:select :*
                   :from :wines_with_ratings  ;; Use the view directly
                   :order-by [[:created_at :desc]]})
                 db-opts))

(defn update-wine! [id wine]
  (jdbc/execute-one! ds
                     (sql/format
                      {:update :wines
                       :set (assoc wine :updated_at [:now])
                       :where [:= :id id]})
                     db-opts))

(defn update-quantity [id new-quantity]
  (update-wine! id {:quantity new-quantity}))

(defn adjust-quantity [id adjustment]
  (jdbc/execute-one! ds
                     (sql/format
                      {:update :wines
                       :set {:quantity [:+ :quantity adjustment]
                             :updated_at [:now]}
                       :where [:= :id id]})
                     db-opts))

(defn delete-wine! [id]
  (jdbc/execute-one! ds
                     (sql/format
                      {:delete-from :wines
                       :where [:= :id id]})
                     db-opts))

;; Classification operations
(defn create-classification
  [classification]
  (jdbc/execute-one! ds
                     (sql/format
                      {:insert-into :wine_classifications
                       :values [(update classification :levels ->pg-array)]})
                     db-opts))

(defn get-classifications []
  (jdbc/execute! ds
                 (sql/format
                  {:select :*
                   :from :wine_classifications
                   :order-by [:country :region :aoc :communal_aoc]})
                 db-opts))

(defn get-regions-by-country [country]
  (jdbc/execute! ds
                 (sql/format
                  {:select [:distinct :region]
                   :from :wine_classifications
                   :where [:= :country country]
                   :order-by [:region]})
                 db-opts))

(defn get-aocs-by-region [country region]
  (jdbc/execute! ds
                 (sql/format
                  {:select [:distinct :aoc]
                   :from :wine_classifications
                   :where [:and
                           [:= :country country]
                           [:= :region region]]
                   :order-by [:aoc]})
                 db-opts))

(defn- ->sql-date [^String date-string]
  (some-> date-string (Date/valueOf)))

;; Tasting Notes Operations
(defn create-tasting-note [note]
  (jdbc/execute-one! ds
                     (sql/format
                       {:insert-into :tasting_notes
                        :values [(-> note
                                    ;; Add date conversion here
                                    (update :tasting_date ->sql-date)
                                    (assoc :updated_at [:now]))]
                        :returning :*})
                     db-opts))

(defn update-tasting-note! [id note]
  (jdbc/execute-one! ds
                     (sql/format
                      {:update :tasting_notes
                       :set (-> note
                               ;; Add date conversion here
                               (update :tasting_date ->sql-date)
                               (assoc :updated_at [:now]))
                       :where [:= :id id]
                       :returning :*})
                     db-opts))

(defn get-tasting-note [id]
  (jdbc/execute-one! ds
                     (sql/format
                      {:select :*
                       :from :tasting_notes
                       :where [:= :id id]})
                     db-opts))

(defn get-tasting-notes-by-wine [wine-id]
  (jdbc/execute! ds
                 (sql/format
                  {:select :*
                   :from :tasting_notes
                   :where [:= :wine_id wine-id]
                   :order-by [[:tasting_date :desc]]})
                 db-opts))

(defn delete-tasting-note! [id]
  (jdbc/execute-one! ds
                     (sql/format
                      {:delete-from :tasting_notes
                       :where [:= :id id]})
                     db-opts))

(defn get-wine-with-tasting-notes [id]
  (let [wine (get-wine id)
        tasting-notes (get-tasting-notes-by-wine id)]
    (when wine
      (assoc wine :tasting_notes tasting-notes))))

(def classifications-file "wine-classifications.edn")

(defn seed-classifications! []
  (let [wine-classifications (edn/read-string
                              (slurp (or
                                      (io/resource "wine-classifications.edn")
                                      (io/file
                                       (str "resources/"
                                            classifications-file)))))]
    (doseq [c wine-classifications]
      (create-classification c))))

(defn classifications-exist?
  "Check if any classifications exist in the database"
  []
  (pos? (:count (jdbc/execute-one! ds
                                   (sql/format
                                     {:select [[[:count :*]]]
                                      :from :wine_classifications})
                                   db-opts))))

(defn seed-classifications-if-needed!
  "Seeds classifications only if none exist in the database"
  []
  (when-not (classifications-exist?)
    (println "No wine classifications found. Seeding from file...")
    (seed-classifications!)
    (println "Wine classifications seeded successfully.")))

(defn- sql-execute-helper [tx sql-map]
  (let [sql (sql/format sql-map)]
    (println "Compiled SQL:" sql)
    (println "DB Response:" (jdbc/execute-one! tx sql db-opts))))

(defn- ensure-tables
  ([] (jdbc/with-transaction [tx ds]
        (ensure-tables tx)))
  ([tx]
   (sql-execute-helper tx create-wine-style-type)
   (sql-execute-helper tx create-wine-level-type)
   (sql-execute-helper tx classifications-table-schema)
   (sql-execute-helper tx wines-table-schema)
   (sql-execute-helper tx tasting-notes-table-schema)
   (sql-execute-helper tx wines-with-ratings-view-schema)))

(defn initialize-db []
  (ensure-tables)
  (seed-classifications-if-needed!))

#_(initialize-db)

(defn- drop-tables
  ([] (jdbc/with-transaction [tx ds]
        (drop-tables tx)))
  ([tx]
   (sql-execute-helper tx {:drop-view [:if-exists :wines-with-ratings]})
   (sql-execute-helper tx {:drop-table [:if-exists :tasting_notes]})
   (sql-execute-helper tx {:drop-table [:if-exists :wines]})
   (sql-execute-helper tx {:drop-table [:if-exists :wine_classifications]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_style CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_level CASCADE"]})))

#_(drop-tables)
