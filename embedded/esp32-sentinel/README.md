# ESP32 Sentinel Prototype

This folder contains the ESP-IDF firmware that will evolve into the cellar environment sensor.
The current build connects to Wi-Fi, assembles a placeholder JSON payload, and POSTs it to
`/api/sensor-readings` with the device JWT so we can exercise the backend before wiring sensors.

## Configure Wi-Fi + Auth
1. Copy `main/config.example.h` to `main/config.h` (gitignored).
2. Fill in your Wi-Fi SSID/PASS, the backend URL (local dev can use `http://<host>:3000/api/sensor-readings`),
   the JWT stored in `device.jwt`, and a friendly `DEVICE_ID`.
3. If you hit an HTTPS endpoint with a private CA, embed the PEM in `CELLAR_API_CERT_PEM` per the comment in the example file.
4. Current build uses only the BMP085 (pressure + temperature) on I²C pins GPIO21/22. Add a BME280 later to regain humidity using the same pins.

### Wiring the BMP085/BMP180 breakout
- `VIN` (or `3V3`) → 3.3 V, `GND` → ground.
- `SDA`/`SCL` GPIO21/GPIO22 pullups needed
- Leave `XCLR` and `EOC` unconnected unless your board requires them (most breakouts pull `XCLR` high internally).
On boot you’ll see the I²C scan log (look for `0x77`). The firmware reads the calibration constants automatically and adds `pressure_hpa` (and a fallback temperature) to the JSON payload.

### Wiring the Adafruit GA1A12S202 light sensor
- Connect `VIN` → 3.3 V, `GND` → ground, and the analog output `OUT` to GPIO34 (ADC1_CH6).
- The firmware averages a few ADC samples, converts millivolts to lux with the GA1A12S202 log curve, and adds `illuminance_lux` to the POST body.
- If you pick a different ADC-capable pin, update `GA1A12S202_ADC_CHANNEL` (and optionally attenuation) in `main/config.h`.

## Prerequisites
1. Install ESP-IDF v5.x from Espressif's installer or `idf.py` CLI.
2. Export the environment for your shell (e.g. `. $IDF_PATH/export.sh`).
3. Connect the ESP32 via micro-USB and note the serial port (e.g. `/dev/ttyUSB0`).

## Build / Flash / Monitor
```bash
cd embedded/esp32-sentinel
idf.py set-target esp32     # once per checkout
idf.py build        # builds with your config.h
idf.py -p /dev/ttyUSB0 flash
idf.py -p /dev/ttyUSB0 monitor
```
You should see log lines similar to:
```
I (340) sentinel: Hello from the wine-cellar ESP32 sentinel!
I (1180) sentinel: Connected to SSID:MyWifi
I (2230) sentinel: POST http://192.168.1.5:3000/api/sensor-readings
I (2450) sentinel: POST status=201, content_length=123
```
Press `Ctrl+]` to exit the monitor.

### Optional: tiny OLED status screen (SSD1306 via esp_lcd)
- Wire the 0.96" I²C OLED to the same bus as the BMP085: `VCC→3V3`, `GND→GND`, `SCL→GPIO22`, `SDA→GPIO21`.
- Most boards use address `0x3C`; confirm in the boot scan log. Set `OLED_ADDRESS`/`OLED_WIDTH`/`OLED_HEIGHT` in `config.h` if needed.
- The screen shows IP, latest temperature/pressure, and last POST status.

## Next Steps
- Swap the placeholder random generator for real SHT21/BMP085 (or other) sensor readings.
- Persist Wi-Fi + JWT in NVS so you can rotate credentials without reflashing.
- Wire up SNTP to send accurate `measured_at` timestamps.
- See `docs/sensor-readings.md` for the REST contract and curl examples—the firmware now speaks the same payloads.
