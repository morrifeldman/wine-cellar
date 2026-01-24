(ns wine-cellar.db.migrations
  "Dedicated namespace for idempotent database migrations (ALTER TABLE, etc.)
   Migrations here should be idempotent (use IF NOT EXISTS).
   Remove migrations from here once they have run in all environments.")

;; Example:
;; (def ensure-some-column
;;   {:raw ["ALTER TABLE wines ADD COLUMN IF NOT EXISTS some_col VARCHAR"]})
