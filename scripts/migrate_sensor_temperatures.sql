-- Backfill sensor_temperatures from sensor_readings.temperatures JSONB column.
-- Safe to re-run: uses ON CONFLICT DO NOTHING.
--
-- Run against your production DB:
--   psql "$DATABASE_URL" -f scripts/migrate_sensor_temperatures.sql
--
-- Or via fly proxy:
--   fly proxy 15432:5432 -a <app-name>
--   psql postgres://...@localhost:15432/... -f scripts/migrate_sensor_temperatures.sql

-- Ensure the table and indexes exist (setup.clj normally handles this on restart)
CREATE TABLE IF NOT EXISTS sensor_temperatures (
  id BIGSERIAL PRIMARY KEY,
  reading_id BIGINT NOT NULL REFERENCES sensor_readings(id) ON DELETE CASCADE,
  sensor_addr VARCHAR NOT NULL,
  temperature_c DOUBLE PRECISION NOT NULL,
  UNIQUE (reading_id, sensor_addr)
);

CREATE INDEX IF NOT EXISTS idx_sensor_temperatures_reading_id
  ON sensor_temperatures(reading_id);

-- Backfill all temperatures in a single server-side INSERT ... SELECT.
-- This runs entirely within PostgreSQL — no app memory involved.
-- The LATERAL expansion + INSERT streams rows; PostgreSQL doesn't need to
-- materialize the full result set in memory the way the old aggregation query did.
-- ON CONFLICT DO NOTHING makes this idempotent / safe to re-run.
INSERT INTO sensor_temperatures (reading_id, sensor_addr, temperature_c)
SELECT
  sr.id,
  kv.key,
  (kv.value)::double precision
FROM sensor_readings sr,
     LATERAL jsonb_each_text(sr.temperatures) AS kv(key, value)
WHERE sr.temperatures IS NOT NULL
  AND kv.value IS NOT NULL
ON CONFLICT (reading_id, sensor_addr) DO NOTHING;
