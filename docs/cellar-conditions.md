# Cellar Condition Telemetry API

This endpoint pair lets hardware (ESP32/Raspberry Pi, etc.) push cellar environment readings directly into the wine-cellar backend and lets the UI fetch the recent history.

## Authentication & Provisioning (low-friction flow)
1. Flash firmware with `DEVICE_ID` and a per-device `CLAIM_CODE`.
2. Device calls `POST /api/device-claim` (unauthenticated) and then polls `POST /api/device-claim/poll`.
3. You approve the pending device in the admin UI (`/api/admin/devices`); once approved, the next poll returns an access token (short-lived JWT) plus a rotating refresh token.
4. Device sends readings with `Authorization: Bearer <access_token>` and rotates via `POST /api/device-token` before expiry.
5. If a device loses its tokens, it can re-enter the claim/poll loop with the same claim code.

Never bake an access or refresh token into firmware; only `DEVICE_ID` and `CLAIM_CODE` live in `main/config.h`.

## Record a Reading
`POST /api/cellar-conditions` (auth required)

Body fields:
- `device_id` *(string, required)* – human-friendly ID like `esp32-sentinel-1`.
- `measured_at` *(ISO8601 string, optional)* – defaults to server time if omitted. The ESP32 sentinel now populates this using SNTP once Wi-Fi is up for consistent timestamps.
- `temperature_c`, `humidity_pct`, `pressure_hpa`, `co2_ppm` *(numbers, optional)*.
- `illuminance_lux` *(number, optional)* – ambient light level from the GA1A12S202 breakout.
- `battery_mv` *(integer, optional)*.
- `leak_detected` *(boolean, optional)*.
- `notes` *(string, optional)*.

At least one measurement field must be included.

Example using `curl`:
```bash
curl -X POST https://your-domain.example/api/cellar-conditions \
  -H "Authorization: Bearer $DEVICE_JWT" \
  -H "Content-Type: application/json" \
  -d '{
        "device_id": "esp32-sentinel-1",
        "measured_at": "2025-11-18T19:20:30Z",
        "temperature_c": 12.4,
        "humidity_pct": 71.2,
        "pressure_hpa": 1012.8,
        "illuminance_lux": 24.6,
        "battery_mv": 3740,
        "leak_detected": false
      }'
```

## Provision a Device (claim + poll)
`POST /api/device-claim`

```bash
curl -X POST https://your-domain.example/api/device-claim \
  -H "Content-Type: application/json" \
  -d '{"device_id":"esp32-sentinel-1","claim_code":"abc123"}'
# → { "status":"pending","retry_after_seconds":30 }
```

`POST /api/device-claim/poll`

```bash
curl -X POST https://your-domain.example/api/device-claim/poll \
  -H "Content-Type: application/json" \
  -d '{"device_id":"esp32-sentinel-1","claim_code":"abc123"}'
# → { "status":"approved","device_id":"esp32-sentinel-1",
#      "access_token":"…","access_expires_at":"2025-11-24T…Z",
#      "refresh_token":"…" }
```

`POST /api/device-token` (rotate, uses refresh token)

```bash
curl -X POST https://your-domain.example/api/device-token \
  -H "Content-Type: application/json" \
  -d '{"device_id":"esp32-sentinel-1","refresh_token":"<from poll>"}'
```

## Fetch Recent Readings
`GET /api/cellar-conditions?device_id=esp32-sentinel-1&limit=20`

- `device_id` *(optional)* filters to a single device.
- `limit` *(optional, default 100, max 500)* returns the most recent entries ordered by `measured_at` descending.

Example:
```bash
curl -H "Authorization: Bearer $DEVICE_JWT" \
     "https://your-domain.example/api/cellar-conditions?device_id=esp32-sentinel-1&limit=5"
```

The response is a vector of records, each containing the stored fields plus `recorded_by` (if the JWT carried an email) and timestamps as ISO8601 strings. Use this endpoint to mock the ESP32 while you track down the physical sensors—the POST examples let you stuff synthetic values into the DB so the web UI can be built before the hardware is live.

## Fetch Latest Reading Per Device
`GET /api/cellar-conditions/latest?device_id=esp32-sentinel-1`

- When `device_id` is provided, returns a single latest record for that device.
- When omitted, returns the latest record for every device (ordered by device_id).

## Fetch Aggregated Time Series (bucketed averages)
`GET /api/cellar-conditions/series?device_id=esp32-sentinel-1&bucket=1d&from=2025-11-01T00:00:00Z`

Query params:
- `device_id` *(optional)* – filter to one device; omit to aggregate each device separately.
- `bucket` *(optional)* – time bucket size; one of `15m`, `1h`, `6h`, `1d` (default `1h`).
- `from` / `to` *(ISO8601 strings, optional)* – time window bounds; defaults to all data up to now.

Each bucket returns `device_id`, `bucket_start` (ISO string), and avg/min/max for temperature, humidity, pressure, CO₂, and battery voltage. Use this for long-range charts without pulling millions of raw samples.
