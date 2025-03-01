(ns wine-cellar.db
  (:require [wine-cellar.common :as common]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs])
  (:import [org.postgresql.jdbc PgArray]))

(extend-protocol rs/ReadableColumn
  PgArray (read-column-by-label [^PgArray v _]
            (.getArray v))
  PgArray (read-column-by-index [^PgArray v _2 _3]
            (.getArray v)))

(def db
  {:dbtype "postgresql"
   :dbname "wine_cellar"
   :user "wine_cellar"
   :password "chianti"})

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
    [:levels :wine_level :array]  ; Change from single level to array
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
    [:wine_id :integer [:not nil] [:references :wines :id] [:on-delete :cascade]]
    [:tasting_date :date [:not nil]]
    [:notes :text [:not nil]]
    [:rating :integer [:check [:and [:>= :rating 1] [:<= :rating 100]]]]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]]})

(defn- sql-execute-helper [tx sql-map]
  (let [sql (sql/format sql-map)]
    (println "Compiled SQL:" sql)
    (println "DB Response:" (jdbc/execute-one! tx sql db-opts))))

(defn ensure-tables
  ([] (jdbc/with-transaction [tx ds]
        (ensure-tables tx)))
  ([tx]
   (sql-execute-helper tx create-wine-style-type)
   (sql-execute-helper tx create-wine-level-type)
   (sql-execute-helper tx classifications-table-schema)
   (sql-execute-helper tx wines-table-schema)
   (sql-execute-helper tx tasting-notes-table-schema)))

(defn- drop-tables [tx]
  (sql-execute-helper tx {:drop-table [:if-exists :wines]})
  (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_style CASCADE"]})
  (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_level CASCADE"]})
  (sql-execute-helper tx {:drop-table [:if-exists :wine_classifications]}))

(defn- ->pg-array [coll]
  {:raw (str "'{" (string/join "," coll) "}'")})

(defn sql-cast [sql-type field]
  [:cast field sql-type])

#_(->pg-array ["a" "bd" "d"])

(defn create-wine [wine]
  (jdbc/execute-one! ds
                     (sql/format
                      {:insert-into :wines
                       :values [(cond-> wine
                                  true (update :styles ->pg-array)
                                  (:level wine) (update :level (partial sql-cast :wine_level)))]})
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
(defn create-classification [classification]
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

(defn- reset-db! []
  (jdbc/with-transaction [tx ds]
    (drop-tables tx)
    (ensure-tables tx))
  (seed-classifications!))

#_(reset-db!)
