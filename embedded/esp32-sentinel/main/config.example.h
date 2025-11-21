#pragma once

// Copy this file to config.h and fill in your local settings.
// Wi-Fi + backend
#define WIFI_SSID "YourWifiName"
#define WIFI_PASS "super-secret"
#define CELLAR_API_URL "http://192.168.1.100:3000/api/cellar-conditions"
#define DEVICE_JWT "paste-device-jwt-here"
#define DEVICE_ID "esp32-sentinel-1"

// I2C bus (BMP085 now; you can drop a BME280 here later)
#define I2C_SDA 21
#define I2C_SCL 22
#define I2C_FREQ_HZ 100000

// BMP085 pressure/temperature sensor
#define BMP085_ADDRESS 0x77
#define BMP085_OSS 0  // 0..3 oversampling; higher = slower but smoother pressure

// Optional: SSD1306 OLED status display
#define OLED_ADDRESS 0x3C
#define OLED_WIDTH 128
#define OLED_HEIGHT 64
// Column offset: use 0 for true SSD1306; many SH1106 modules need 2.
#define SSD1306_COL_OFFSET 0
// If your panel behaves like SH1106 (scrambled text), set this to 1 and COL_OFFSET to 2.
// #define SSD1306_IS_SH1106 1
// Flip font column bits if characters look vertically scrambled (0=bit0 top, 1=bit7 top)
#define SSD1306_FONT_FLIP_VERT 1

/* Optional: pin your TLS cert for HTTPS endpoints
static const char CELLAR_API_CERT_PEM[] = "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n";
*/
