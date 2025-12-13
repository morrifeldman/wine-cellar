#pragma once

// Copy this file to config.h and fill in your local settings.
// Wi-Fi + backend
#define WIFI_SSID "YourWifiName"
#define WIFI_PASS "super-secret"
#define CELLAR_API_BASE "http://192.168.1.100:3000/api"
#define DEVICE_ID "esp32-sentinel-1"
#define CLAIM_CODE "set-per-device-claim-code"

// I2C bus
#define I2C_SDA 21
#define I2C_SCL 22
#define I2C_FREQ_HZ 100000

// BMP280 pressure/temperature sensor
#define BMP280_ADDRESS 0x76 // 0x76 or 0x77

// Display temperature in Fahrenheit on the OLED (defaults to Celsius)
// #define DISPLAY_TEMP_FAHRENHEIT 1

// Optional: SSD1306 OLED status display
#define OLED_ADDRESS 0x3C
#define OLED_WIDTH 128
#define OLED_HEIGHT 64

// Optional: site elevation in meters for sea-level pressure correction.
// Leave undefined or set to 0.0f to skip adding pressure_sea_level_hpa.
// #define SENSOR_ALTITUDE_M 0.0f

// Optional: display pressure in inches of mercury instead of hPa on the OLED.
// #define DISPLAY_PRESSURE_INHG 1

// #define CELLAR_API_USE_HTTPS 0

/* Optional: pin your TLS cert for HTTPS endpoints
static const char CELLAR_API_CERT_PEM[] = "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n";
*/
