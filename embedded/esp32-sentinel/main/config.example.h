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

/* Optional: pin your TLS cert for HTTPS endpoints
static const char CELLAR_API_CERT_PEM[] = "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n";
*/
