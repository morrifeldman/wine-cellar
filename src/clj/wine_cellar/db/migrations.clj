(ns wine-cellar.db.migrations
  "Dedicated namespace for idempotent database migrations (ALTER TABLE, etc.)
   Migrations here should be idempotent (use IF NOT EXISTS).
   Remove migrations from here once they have run in all environments.")

(def ensure-classifications-appellation-tier
  {:raw
   ["DO $$ BEGIN "
    "IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='wine_classifications' AND COLUMN_NAME='appellation_tier') THEN "
    "ALTER TABLE wine_classifications ADD COLUMN appellation_tier VARCHAR; "
    "ALTER TABLE wine_classifications DROP CONSTRAINT IF EXISTS wine_classifications_natural_key; "
    "ALTER TABLE wine_classifications ADD CONSTRAINT wine_classifications_natural_key UNIQUE NULLS NOT DISTINCT (country, region, appellation, appellation_tier, classification, vineyard); "
    "END IF; END $$;"]})

(def ensure-messages-fts-index
  {:raw
   ["DO $$ BEGIN "
    "IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='ai_conversation_messages' AND COLUMN_NAME='fts_content') THEN "
    "ALTER TABLE ai_conversation_messages ADD COLUMN fts_content tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED; "
    "CREATE INDEX IF NOT EXISTS idx_messages_fts_content ON ai_conversation_messages USING GIN (fts_content); "
    "END IF; END $$;"]})

(def ensure-blind-tasting-columns
  {:raw
   ["DO $$ BEGIN "
    "ALTER TABLE tasting_notes ALTER COLUMN wine_id DROP NOT NULL; "
    "EXCEPTION WHEN others THEN NULL; " "END $$; "
    "ALTER TABLE tasting_notes ADD COLUMN IF NOT EXISTS is_blind BOOLEAN DEFAULT false;"]})

(def ensure-devices-sensor-config
  {:raw ["ALTER TABLE devices ADD COLUMN IF NOT EXISTS sensor_config JSONB"]})

(def migrate-temperature-to-temperatures
  {:raw
   ["DO $$ BEGIN "
    "IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='sensor_readings' AND COLUMN_NAME='temperature_c') THEN "
    "ALTER TABLE sensor_readings ADD COLUMN IF NOT EXISTS temperatures JSONB; "
    "ALTER TABLE sensor_readings DROP COLUMN temperature_c; "
    "END IF; END $$;"]})

(def rename-cellar-conditions-to-sensor-readings
  {:raw ["ALTER TABLE IF EXISTS cellar_conditions RENAME TO sensor_readings"]})

(def seed-bar-inventory-items
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

(def remove-classification-vineyard-designations
  {:raw
   ["DO $$ BEGIN "
    "IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='wine_classifications' AND COLUMN_NAME='vineyard') THEN "
    "ALTER TABLE wine_classifications DROP CONSTRAINT IF EXISTS wine_classifications_natural_key; "
    "ALTER TABLE wine_classifications DROP COLUMN IF EXISTS vineyard; "
    "ALTER TABLE wine_classifications DROP COLUMN IF EXISTS designations; "
    "ALTER TABLE wine_classifications ADD CONSTRAINT wine_classifications_natural_key UNIQUE NULLS NOT DISTINCT (country, region, appellation, appellation_tier, classification); "
    "END IF; END $$;"]})
