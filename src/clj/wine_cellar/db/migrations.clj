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
    "IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='cellar_conditions' AND COLUMN_NAME='temperature_c') THEN "
    "ALTER TABLE cellar_conditions ADD COLUMN IF NOT EXISTS temperatures JSONB; "
    "ALTER TABLE cellar_conditions DROP COLUMN temperature_c; "
    "END IF; END $$;"]})
