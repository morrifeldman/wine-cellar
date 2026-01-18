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
   (sql-execute-helper tx {:raw ["DROP VIEW IF EXISTS enriched_wines"]})
   (sql-execute-helper tx schema/create-wine-style-type)
   (sql-execute-helper tx schema/ensure-red-sparkling-style)
   (sql-execute-helper tx schema/ensure-appellation-tier-column)
   (sql-execute-helper tx schema/classifications-table-schema)
   (sql-execute-helper tx schema/wines-table-schema)
   (sql-execute-helper tx schema/tasting-notes-table-schema)
   (sql-execute-helper tx schema/ai-conversations-table-schema)
   (sql-execute-helper tx schema/ai-conversation-messages-table-schema)
   (sql-execute-helper tx schema/grape-varieties-table-schema)
   (sql-execute-helper tx schema/wine-grape-varieties-table-schema)
   (sql-execute-helper tx schema/inventory-history-table-schema)
   (sql-execute-helper tx schema/cellar-reports-table-schema)
   (sql-execute-helper tx schema/cellar-conditions-table-schema)
   (sql-execute-helper tx schema/devices-table-schema)
   (sql-execute-helper tx {:raw ["DROP VIEW IF EXISTS enriched_wines"]})
   (sql-execute-helper tx schema/enriched-wines-view-schema)))

(defn initialize-db
  "Initialize database with optional classification seeding"
  ([] (initialize-db true)) ; Default behavior - seed classifications
  ([seed-classifications?]
   (ensure-tables)
   (when seed-classifications? (seed-classifications-if-needed!))))

#_(initialize-db)

(defn drop-tables
  ([] (jdbc/with-transaction [tx ds] (drop-tables tx)))
  ([tx]
   (sql-execute-helper tx {:raw ["DROP VIEW IF EXISTS enriched_wines"]})
   (sql-execute-helper
    tx
    {:raw ["DROP TABLE IF EXISTS ai_conversation_messages CASCADE"]})
   (sql-execute-helper tx
                       {:raw ["DROP TABLE IF EXISTS ai_conversations CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TABLE IF EXISTS tasting_notes CASCADE"]})
   (sql-execute-helper tx
                       {:raw
                        ["DROP TABLE IF EXISTS cellar_conditions CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TABLE IF EXISTS devices CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TABLE IF EXISTS wines CASCADE"]})
   (sql-execute-helper tx
                       {:raw
                        ["DROP TABLE IF EXISTS wine_classifications CASCADE"]})
   (sql-execute-helper tx
                       {:raw
                        ["DROP TABLE IF EXISTS wine_grape_varieties CASCADE"]})
   (sql-execute-helper tx
                       {:raw ["DROP TABLE IF EXISTS grape_varieties CASCADE"]})
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_style CASCADE"]})))
#_(drop-tables)
