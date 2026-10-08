;; Schema changes, each applied once and recorded in schema_migrations.
;;
;; Tables are still created from wine-cellar.db.schema (CREATE ... IF NOT
;; EXISTS), which is all a fresh database needs. Anything that changes an
;; existing table goes here instead: append a new entry, never edit or
;; reorder an applied one. 0001-0014 are the old run-every-boot blocks; they
;; are idempotent, so recording them on a database that already has them is
;; harmless.
(ns wine-cellar.db.migrations
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [wine-cellar.common :as common]
            [wine-cellar.db.connection :refer [db-opts]]))

(def migrations
  [{:id "0001-spirits-abv-to-proof"
    :sql
    {:raw
     ["DO $$ BEGIN "
      "IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='spirits' AND column_name='abv') THEN "
      "UPDATE spirits SET abv = ROUND(abv * 2) WHERE abv IS NOT NULL; "
      "ALTER TABLE spirits RENAME COLUMN abv TO proof; "
      "ALTER TABLE spirits ALTER COLUMN proof TYPE integer USING proof::integer; "
      "END IF; END $$;"]}}
   {:id "0002-spirits-subcategory"
    :sql
    {:raw
     ["DO $$ BEGIN "
      "IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='spirits' AND column_name='subcategory') THEN "
      "ALTER TABLE spirits ADD COLUMN subcategory varchar; "
      "END IF; END $$;"]}}
   {:id "0003-recipes-notes"
    :sql
    {:raw
     ["DO $$ BEGIN "
      "IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='cocktail_recipes' AND column_name='notes') THEN "
      "ALTER TABLE cocktail_recipes ADD COLUMN notes text; "
      "END IF; END $$;"]}}
   {:id "0004-recipes-rating"
    :sql
    {:raw
     ["ALTER TABLE cocktail_recipes ADD COLUMN IF NOT EXISTS rating integer; "
      "ALTER TABLE cocktail_recipes DROP CONSTRAINT IF EXISTS cocktail_recipes_rating_check; "
      "ALTER TABLE cocktail_recipes ADD CONSTRAINT cocktail_recipes_rating_check CHECK (rating IS NULL OR (rating BETWEEN "
      [:inline (first common/recipe-rating-range)] " AND "
      [:inline (second common/recipe-rating-range)] "));"]}}
   {:id "0005-recipes-drop-spirit-tags"
    :sql {:raw
          ["ALTER TABLE cocktail_recipes DROP COLUMN IF EXISTS spirit_tags;"]}}
   {:id "0006-recipes-caption"
    :sql
    {:raw
     ["ALTER TABLE cocktail_recipes ADD COLUMN IF NOT EXISTS caption varchar;"]}}
   {:id "0007-recipes-timers"
    :sql
    {:raw
     ["ALTER TABLE cocktail_recipes ADD COLUMN IF NOT EXISTS timers jsonb;"]}}
   {:id "0008-messages-context-note"
    :sql
    {:raw
     ["ALTER TABLE ai_conversation_messages ADD COLUMN IF NOT EXISTS context_note jsonb;"]}}
   {:id "0009-wines-open-bottle"
    :sql
    {:raw
     ["DO $$ BEGIN "
      "IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='wines' AND column_name='open_bottle_opened_at') THEN "
      "ALTER TABLE wines ADD COLUMN open_bottle_opened_at timestamptz; "
      "ALTER TABLE wines ADD COLUMN open_bottle_oz_poured numeric(5,2); "
      "END IF; END $$;"]}}
   {:id "0010-history-oz"
    :sql
    {:raw
     ["DO $$ BEGIN "
      "IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='inventory_history' AND column_name='oz') THEN "
      "ALTER TABLE inventory_history ADD COLUMN oz numeric(5,2); "
      "UPDATE inventory_history "
      "SET oz = substring(notes from '([0-9]+\\.?[0-9]*)\\s*oz')::numeric, "
      "    notes = NULLIF(trim(BOTH ' —' FROM regexp_replace(notes, '^[0-9]+\\.?[0-9]*\\s*oz\\s*—?\\s*', '')), '') "
      "WHERE reason = 'coravin_pour' AND notes ~ '[0-9]+\\.?[0-9]*\\s*oz'; "
      "END IF; END $$;"]}}
   {:id "0011-idx-sensor-readings-device"
    :sql
    {:raw
     ["CREATE INDEX IF NOT EXISTS idx_sensor_readings_device_measured ON sensor_readings(device_id, measured_at DESC)"]}}
   {:id "0012-idx-sensor-readings-measured"
    :sql
    {:raw
     ["CREATE INDEX IF NOT EXISTS idx_sensor_readings_measured ON sensor_readings(measured_at DESC)"]}}
   {:id "0013-idx-sensor-temperatures-reading"
    :sql
    {:raw
     ["CREATE INDEX IF NOT EXISTS idx_sensor_temperatures_reading_id ON sensor_temperatures(reading_id)"]}}
   {:id "0014-seed-bar-inventory"
    :sql
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
      "('coconut cream', 'other', 30); " "END IF; END $$;"]}}
   ;; enriched_wines runs a correlated subquery per wine on tasting_notes.
   {:id "0015-idx-tasting-notes-wine"
    :sql
    {:raw
     ["CREATE INDEX IF NOT EXISTS idx_tasting_notes_wine_id ON tasting_notes(wine_id)"]}}
   {:id "0016-idx-inventory-history-wine"
    :sql
    {:raw
     ["CREATE INDEX IF NOT EXISTS idx_inventory_history_wine_id ON inventory_history(wine_id)"]}}
   {:id "0017-idx-messages-conversation"
    :sql
    {:raw
     ["CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON ai_conversation_messages(conversation_id)"]}}
   ;; NOT VALID: new writes are checked; existing rows don't block the
   ;; deploy.
   {:id "0018-devices-status-check"
    :sql
    {:raw
     ["ALTER TABLE devices ADD CONSTRAINT devices_status_check CHECK (status IN ('pending', 'active', 'blocked')) NOT VALID"]}}
   {:id "0019-conversations-provider-check"
    :sql
    {:raw
     ["ALTER TABLE ai_conversations ADD CONSTRAINT ai_conversations_provider_check CHECK (provider IS NULL OR provider IN "
      [:inline (vec (sort (map name common/ai-providers)))] ") NOT VALID"]}}])

(def ^:private create-migrations-table
  {:create-table [:schema_migrations :if-not-exists]
   :with-columns [[:id :varchar :primary-key]
                  [:applied_at :timestamptz [:not nil] [:default [:now]]]]})

(defn- applied-ids
  [tx]
  (->> (jdbc/execute! tx
                      (sql/format {:select [:id] :from [:schema_migrations]})
                      db-opts)
       (map :id)
       set))

(defn migrate!
  "Applies, in order, every migration not yet recorded. Runs inside the
  caller's transaction; the advisory lock keeps two booting instances from
  applying the same one twice."
  ([tx] (migrate! tx migrations))
  ([tx migrations]
   (jdbc/execute!
    tx
    ["SELECT pg_advisory_xact_lock(hashtext('schema_migrations'))"])
   (jdbc/execute! tx (sql/format create-migrations-table))
   (let [done (applied-ids tx)]
     (doseq [{:keys [id sql]} migrations
             :when (not (done id))]
       (tap> ["Applying migration" id])
       (jdbc/execute! tx (sql/format sql))
       (jdbc/execute! tx
                      (sql/format {:insert-into :schema_migrations
                                   :values [{:id id}]}))))))
