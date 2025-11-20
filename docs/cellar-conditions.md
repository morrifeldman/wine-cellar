# Cellar Condition Telemetry API

This endpoint pair lets hardware (ESP32/Raspberry Pi, etc.) push cellar environment readings directly into the wine-cellar backend and lets the UI fetch the recent history.

## Authentication
Devices send an existing JWT in the `Authorization: Bearer <token>` header. During early testing you can mint one manually (e.g., via `wine-cellar.auth.core/create-jwt-token`) and paste it into your firmware or `curl` command. The middleware now checks both the cookie and the header, so browser users and headless devices share the same authorization path.

## Record a Reading
`POST /api/cellar-conditions` (auth required)

Body fields:
- `device_id` *(string, required)* – human-friendly ID like `esp32-sentinel-1`.
- `measured_at` *(ISO8601 string, optional)* – defaults to server time if omitted.
- `temperature_c`, `humidity_pct`, `pressure_hpa`, `co2_ppm` *(numbers, optional)*.
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
        "battery_mv": 3740,
        "leak_detected": false
      }'
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
