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
   ;; Migrations
   (sql-execute-helper
    tx
    {:raw
     ["DO $$ BEGIN "
      "IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='spirits' AND column_name='abv') THEN "
      "UPDATE spirits SET abv = ROUND(abv * 2) WHERE abv IS NOT NULL; "
      "ALTER TABLE spirits RENAME COLUMN abv TO proof; "
      "ALTER TABLE spirits ALTER COLUMN proof TYPE integer USING proof::integer; "
      "END IF; END $$;"]})
   ;; Indexes
   (sql-execute-helper tx {:raw ["CREATE INDEX IF NOT EXISTS idx_sensor_readings_device_measured ON sensor_readings(device_id, measured_at DESC)"]})
   (sql-execute-helper tx {:raw ["CREATE INDEX IF NOT EXISTS idx_sensor_readings_measured ON sensor_readings(measured_at DESC)"]})
   (sql-execute-helper tx {:raw ["CREATE INDEX IF NOT EXISTS idx_sensor_temperatures_reading_id ON sensor_temperatures(reading_id)"]})
   ;; Seed data
   (sql-execute-helper
    tx
    {:raw
     ["DO $$ BEGIN "
      "IF NOT EXISTS (SELECT 1 FROM bar_inventory_items LIMIT 1) THEN "
      "INSERT INTO bar_inventory_items (name, category, sort_order) VALUES "
      "('lime juice', 'juice', 10), " "('lemon juice', 'juice', 20), "
      "('orange juice', 'juice', 30), " "('grapefruit juice', 'juice', 40), "
      "('pineapple juice', 'juice', 50), " "('cranberry juice', 'juice', 60), "
      "('club soda', 'soda', 10), " "('tonic water', 'soda', 20), "
      "('ginger beer', 'soda', 30), " "('ginger ale', 'soda', 40), "
      "('cola', 'soda', 50), " "('simple syrup', 'syrup', 10), "
      "('honey syrup', 'syrup', 20), " "('grenadine', 'syrup', 30), "
      "('orgeat', 'syrup', 40), " "('agave nectar', 'syrup', 50), "
      "('falernum', 'syrup', 60), " "('Angostura bitters', 'bitters', 10), "
      "('Peychaud''s bitters', 'bitters', 20), "
      "('orange bitters', 'bitters', 30), " "('mole bitters', 'bitters', 40), "
      "('lime wedges', 'garnish', 10), " "('lemon wedges', 'garnish', 20), "
      "('orange peel', 'garnish', 30), "
      "('maraschino cherries', 'garnish', 40), " "('olives', 'garnish', 50), "
      "('cocktail onions', 'garnish', 60), " "('fresh mint', 'garnish', 70), "
      "('fresh basil', 'garnish', 80), " "('rosemary', 'garnish', 90), "
      "('heavy cream', 'other', 10), " "('egg whites', 'other', 20), "
      "('coconut cream', 'other', 30); " "END IF; END $$;"]})
   ;; View
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
   (sql-execute-helper
    tx
    {:raw ["DROP TABLE IF EXISTS sensor_temperatures CASCADE"]})
   (sql-execute-helper tx
                       {:raw ["DROP TABLE IF EXISTS sensor_readings CASCADE"]})
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
