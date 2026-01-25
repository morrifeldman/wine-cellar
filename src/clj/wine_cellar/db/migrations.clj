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

;; Example:
;; (def ensure-some-column
;;   {:raw ["ALTER TABLE wines ADD COLUMN IF NOT EXISTS some_col VARCHAR"]})
