(ns wine-cellar.db.setup
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [wine-cellar.db.api :as db-api]
            [wine-cellar.db.connection :refer [db-opts ds q-one]]
            [wine-cellar.db.migrations :as migrations]
            [wine-cellar.db.schema :as schema]))

;; Classification seeding
(def classifications-file "wine-classifications.edn")

(defn seed-classifications!
  []
  (let [wine-classifications
        (edn/read-string (slurp (or (io/resource "wine-classifications.edn")
                                    (io/file (str "resources/"
                                                  classifications-file)))))]
    ;; The seed file also carries :designations, which has no column.
    (doseq [c wine-classifications]
      (db-api/create-or-update-classification
       (select-keys c
                    [:country :region :appellation :appellation_tier
                     :classification])))))

(defn classifications-exist?
  "Check if any classifications exist in the database"
  []
  (pos? (:count (q-one ds
                       {:select [[[:count :*]]] :from :wine_classifications}))))

(defn seed-classifications-if-needed!
  "Seeds classifications only if none exist in the database"
  []
  (when-not (classifications-exist?)
    (tap> "No wine classifications found. Seeding from file...")
    (seed-classifications!)
    (tap> "Wine classifications seeded successfully.")))

;; Database setup and teardown
(defn- sql-execute-helper
  [tx sql-map]
  (let [sql (sql/format sql-map)]
    (tap> ["Compiled SQL:" sql])
    (tap> ["DB Response:" (jdbc/execute-one! tx sql db-opts)])))

(defn- ensure-tables
  ([] (jdbc/with-transaction [tx ds] (ensure-tables tx)))
  ([tx]
   (sql-execute-helper tx {:raw ["DROP VIEW IF EXISTS enriched_wines"]})
   (sql-execute-helper tx schema/create-wine-style-type)
   (doseq [stmt schema/add-wine-style-values] (sql-execute-helper tx stmt))
   ;; Tables
   (sql-execute-helper tx schema/classifications-table-schema)
   (sql-execute-helper tx schema/wines-table-schema)
   (sql-execute-helper tx schema/tasting-notes-table-schema)
   (sql-execute-helper tx schema/ai-conversations-table-schema)
   (sql-execute-helper tx schema/ai-conversation-messages-table-schema)
   (sql-execute-helper tx schema/ensure-messages-fts-column)
   (sql-execute-helper tx schema/grape-varieties-table-schema)
   (sql-execute-helper tx schema/wine-grape-varieties-table-schema)
   (sql-execute-helper tx schema/inventory-history-table-schema)
   (sql-execute-helper tx schema/cellar-reports-table-schema)
   (sql-execute-helper tx schema/sensor-readings-table-schema)
   (sql-execute-helper tx schema/sensor-temperatures-table-schema)
   (sql-execute-helper tx schema/devices-table-schema)
   (sql-execute-helper tx schema/spirits-table-schema)
   (sql-execute-helper tx schema/bar-inventory-items-table-schema)
   (sql-execute-helper tx schema/cocktail-recipes-table-schema)
   (migrations/migrate! tx)
   ;; The view is derived; rebuild it on every start (dropped above).
   (sql-execute-helper tx schema/enriched-wines-view-schema)))

(defn initialize-db
  "Initialize database with optional classification seeding"
  ([] (initialize-db true)) ; Default behavior - seed classifications
  ([seed-classifications?]
   (ensure-tables)
   (when seed-classifications? (seed-classifications-if-needed!))))

#_(initialize-db)

(def ^:private app-tables
  "Every table ensure-tables creates. CASCADE takes care of the order."
  ["schema_migrations" "ai_conversation_messages" "ai_conversations"
   "tasting_notes" "inventory_history" "wine_grape_varieties" "grape_varieties"
   "cellar_reports" "sensor_temperatures" "sensor_readings" "devices"
   "cocktail_recipes" "bar_inventory_items" "spirits" "wines"
   "wine_classifications"])

(defn drop-tables
  ([] (jdbc/with-transaction [tx ds] (drop-tables tx)))
  ([tx]
   (sql-execute-helper tx {:raw ["DROP VIEW IF EXISTS enriched_wines"]})
   (doseq [table app-tables]
     (sql-execute-helper tx
                         {:raw
                          [(str "DROP TABLE IF EXISTS " table " CASCADE")]}))
   (sql-execute-helper tx {:raw ["DROP TYPE IF EXISTS wine_style CASCADE"]})))
#_(drop-tables)
