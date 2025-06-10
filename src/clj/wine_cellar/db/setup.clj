(ns wine-cellar.db.setup
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [wine-cellar.db.api :as db-api]
            [wine-cellar.db.connection :refer [db-opts ds]]
            [wine-cellar.db.schema :as schema]))

;; Classification seeding
(def classifications-file "wine-classifications.edn")

(defn seed-classifications!
  []
  (let [wine-classifications
        (edn/read-string (slurp (or (io/resource "wine-classifications.edn")
                                    (io/file (str "resources/"
                                                  classifications-file)))))]
    (doseq [c wine-classifications]
      (db-api/create-or-update-classification c))))

(defn classifications-exist?
  "Check if any classifications exist in the database"
  []
  (pos? (:count (jdbc/execute-one! ds
                                   (sql/format {:select [[[:count :*]]]
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
  ([] (jdbc/with-transaction [tx ds] (ensure-tables tx)))
  ([tx]
   (sql-execute-helper tx schema/create-wine-style-type)
   (sql-execute-helper tx schema/create-wine-level-type)
   (sql-execute-helper tx schema/classifications-table-schema)
   (sql-execute-helper tx schema/wines-table-schema)
   (sql-execute-helper tx schema/tasting-notes-table-schema)
   (sql-execute-helper tx schema/grape-varieties-table-schema)
   (sql-execute-helper tx schema/wine-grape-varieties-table-schema)
   (sql-execute-helper tx schema/enriched-wines-view-schema)))

(defn initialize-db [] (ensure-tables) (seed-classifications-if-needed!))

#_(initialize-db)

(defn- drop-tables
  ([] (jdbc/with-transaction [tx ds] (drop-tables tx)))
  ([tx]
   (sql-execute-helper tx {:raw ["DROP VIEW IF EXISTS enriched_wines CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TABLE IF EXISTS tasting_notes CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TABLE IF EXISTS wines CASCADE"]})
   (sql-execute-helper tx
                       {:raw
                        ["DROP TABLE IF EXISTS wine_classifications CASCADE"]})
   (sql-execute-helper tx
                       {:raw
                        ["DROP TABLE IF EXISTS wine_grape_varieties CASCADE"]})
   (sql-execute-helper tx
                       {:raw ["DROP TABLE IF EXISTS grape_varieties CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_style CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_level CASCADE"]})))
#_(drop-tables)
