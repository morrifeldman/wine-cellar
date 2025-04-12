(ns wine-cellar.db.setup
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [wine-cellar.config-utils :as config-utils]
            [wine-cellar.db.schema :as schema])
  (:import [org.postgresql.jdbc PgArray]))

;; Protocol extension for PostgreSQL arrays
(extend-protocol rs/ReadableColumn
  PgArray
    (read-column-by-label [^PgArray v _] (.getArray v))
  PgArray
    (read-column-by-index [^PgArray v _2 _3] (.getArray v)))

(defn get-db-config
  []
  (if-let [jdbc-url (System/getenv "DATABASE_URL")]
    {:jdbcUrl jdbc-url}
    {:dbtype "postgresql",
     :dbname "wine_cellar",
     :user "wine_cellar",
     :password (config-utils/get-password-from-pass "wine-cellar/db")}))

(def ds (delay (jdbc/get-datasource (get-db-config))))

(def db-opts {:builder-fn rs/as-unqualified-maps})

;; Classification seeding
(def classifications-file "wine-classifications.edn")

(defn seed-classifications!
  []
  (let [wine-classifications
          (edn/read-string (slurp (or (io/resource "wine-classifications.edn")
                                      (io/file (str "resources/"
                                                    classifications-file)))))]
    (doseq [c wine-classifications]
      (require 'wine-cellar.db.api)
      ((resolve 'wine-cellar.db.api/create-or-update-classification) c))))

(defn classifications-exist?
  "Check if any classifications exist in the database"
  []
  (pos? (:count (jdbc/execute-one! @ds
                                   (sql/format {:select [[[:count :*]]],
                                                :from :wine_classifications})
                                   db-opts))))

(defn seed-classifications-if-needed!
  "Seeds classifications only if none exist in the database"
  []
  (when-not (classifications-exist?)
    (println "No wine classifications found. Seeding from file...")
    (seed-classifications!)
    (println "Wine classifications seeded successfully.")))

;; Database setup and teardown
(defn- sql-execute-helper
  [tx sql-map]
  (let [sql (sql/format sql-map)]
    (println "Compiled SQL:" sql)
    (println "DB Response:" (jdbc/execute-one! tx sql db-opts))))

(defn- ensure-tables
  ([] (jdbc/with-transaction [tx @ds] (ensure-tables tx)))
  ([tx]
   (sql-execute-helper tx schema/create-wine-style-type)
   (sql-execute-helper tx schema/create-wine-level-type)
   (sql-execute-helper tx schema/classifications-table-schema)
   (sql-execute-helper tx schema/wines-table-schema)
   (sql-execute-helper tx schema/tasting-notes-table-schema)
   (sql-execute-helper tx schema/wines-with-ratings-view-schema)))

(defn initialize-db [] (ensure-tables) (seed-classifications-if-needed!))

#_(initialize-db)

(defn- drop-tables
  ([] (jdbc/with-transaction [tx @ds] (drop-tables tx)))
  ([tx]
   (sql-execute-helper tx {:drop-view [:if-exists :wines-with-ratings]})
   (sql-execute-helper tx {:drop-table [:if-exists :tasting_notes]})
   (sql-execute-helper tx {:drop-table [:if-exists :wines]})
   (sql-execute-helper tx {:drop-table [:if-exists :wine_classifications]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_style CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_level CASCADE"]})))
#_(drop-tables)
