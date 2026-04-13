# ESP32 Sentinel — Agent Instructions

## Overview
ESP-IDF v5.x firmware for the wine cellar environment sensor. Reads BMP085/BMP180 (pressure/temp), GA1A12S202 (lux), and optional SSD1306 OLED, then POSTs JSON to the backend `/api/sensor-readings`.

## Build & Flash
```bash
. $IDF_PATH/export.sh
idf.py set-target esp32          # once per checkout
idf.py build
idf.py -p /dev/ttyUSB0 flash
idf.py -p /dev/ttyUSB0 monitor   # Ctrl+] to exit
```

## Configuration
Copy `main/config.example.h` → `main/config.h` (gitignored). Set Wi-Fi SSID/PASS, backend URL, device JWT, and `DEVICE_ID`.

## Working Style
- One change at a time; ask user to test before moving on
- Keep C code simple and readable — this runs on a constrained device
- See `docs/sensor-readings.md` in the project root for the REST contract
