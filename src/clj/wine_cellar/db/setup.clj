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
   (sql-execute-helper tx schema/wines-with-ratings-view-schema)
   (sql-execute-helper tx schema/grape-varieties-table-schema)
   (sql-execute-helper tx schema/wine-grape-varieties-table-schema)))

(defn initialize-db [] (ensure-tables) (seed-classifications-if-needed!))

#_(initialize-db)

(defn- drop-tables
  ([] (jdbc/with-transaction [tx ds] (drop-tables tx)))
  ([tx]
   (sql-execute-helper tx {:raw ["DROP VIEW IF EXISTS wines_with_ratings CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TABLE IF EXISTS tasting_notes CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TABLE IF EXISTS wines CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TABLE IF EXISTS wine_classifications CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_style CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_level CASCADE"]})))
#_(drop-tables)

; Schema reset functionality
(def table-export-specs
  {:wines {:serialization-fn db-api/db-wine->wine
           :deserialization-fn db-api/wine->db-wine
           :primary-key :id}
   :wine_classifications
   {:deserialization-fn #(update % :levels db-api/->pg-array) :primary-key :id}
   :tasting_notes {:primary-key :id}})

(defn export-data
  "Export all data from the database to a map structure"
  []
  (->> (for [[table {:keys [serialization-fn] :or {serialization-fn identity}}]
             table-export-specs
             :let [data (jdbc/execute! ds
                                       (sql/format {:select :*
                                                    :from table
                                                    ;; Order by ID to ensure
                                                    ;; parent records come
                                                    ;; before children
                                                    :order-by [:id]})
                                       db-opts)
                   serialized-data (mapv serialization-fn data)]]
         [table serialized-data])
       (into {})))

#_(tap> (export-data))

(defn import-data
  "Import data from a map structure back into the database"
  [serialized-data]
  (jdbc/with-transaction
   [tx ds]
   (doseq [[table table-data] serialized-data
           :let [{:keys [deserialization-fn primary-key]
                  :or {deserialization-fn identity}}
                 (get table-export-specs table)
                 deserialized-data (mapv deserialization-fn table-data)
                 sql (sql/format {:insert-into table :values deserialized-data}
                                 db-opts)]
           :when (seq deserialized-data)]
     (tap> ["import-sql" table sql])
     (jdbc/execute! tx
                    (sql/format {:insert-into table :values deserialized-data}
                                db-opts))
     ;; Reset the sequence to the max ID + 1
     (when primary-key
       (let [sequence-name (str (name table) "_" (name primary-key) "_seq")
             reset-sql [(str "SELECT setval('"
                             sequence-name
                             "', COALESCE((SELECT MAX("
                             (name primary-key)
                             ") FROM "
                             (name table)
                             "), 0) + 1, false);")]]
         (println "Resetting sequence" sequence-name)
         (jdbc/execute! tx reset-sql db-opts))))))

(defn reset-schema!
  "Reset the database schema while preserving data:
   1. Export all data
   2. Drop all tables
   3. Recreate tables with the latest schema
   4. Import the data back"
  []
  (println "Exporting current data...")
  (let [data (export-data)]
    (println "Dropping existing tables...")
    (drop-tables)
    (println "Creating tables with updated schema...")
    (ensure-tables)
    (println "Importing data back into the database...")
    (import-data data)
    (println "Schema reset complete!")))

#_(do (drop-tables) (initialize-db) (reset-schema!))

#_(reset-schema!)
