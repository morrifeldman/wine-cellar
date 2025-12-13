#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "bmp280.h"
#include "driver/i2c_master.h"
#include "esp_chip_info.h"
#include "esp_event.h"
#include "esp_flash.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_netif_sntp.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/task.h"
#include "i2c_bus.h"
#include "nvs_flash.h"

#include "cellar_auth.h"
#include "cellar_display.h"
#include "cellar_http.h"
#include "config.h"  // User-provided Wi-Fi + API settings (see config.example.h)

#ifndef I2C_SDA
#define I2C_SDA 21
#endif
#ifndef I2C_SCL
#define I2C_SCL 22
#endif
#ifndef I2C_FREQ_HZ
#define I2C_FREQ_HZ 100000
#endif

// Default to standard BMP280 address if not in config
#ifndef BMP280_ADDRESS
#define BMP280_ADDRESS BMP280_I2C_ADDRESS_0
#endif

// Optional local altitude (meters above sea level) to derive sea-level pressure.
#ifndef SENSOR_ALTITUDE_M
#define SENSOR_ALTITUDE_M 0.0f
#endif

#ifndef WIFI_SSID
#error "WIFI_SSID must be defined in config.h"
#endif

#ifndef WIFI_PASS
#error "WIFI_PASS must be defined in config.h"
#endif

#ifndef CELLAR_API_BASE
#error "CELLAR_API_BASE must be defined in config.h"
#endif

#ifndef DEVICE_ID
#define DEVICE_ID "esp32-sentinel"
#endif

#define WIFI_CONNECTED_BIT BIT0
#define WIFI_FAIL_BIT BIT1
#define WIFI_MAXIMUM_RETRY 5
#define POST_INTERVAL_MS (30 * 1000)

static const char *TAG = "sentinel";
static EventGroupHandle_t s_wifi_event_group;
static int s_retry_num = 0;

// i2c_bus handle (wrapper)
static i2c_bus_handle_t s_i2c_bus_handle = NULL;
// Native handle extracted from wrapper (for cellar_display and bmp280)
static i2c_master_bus_handle_t s_i2c_bus = NULL;

static bmp280_handle_t s_bmp280;
static bool s_bmp280_ready = false;

static char s_ip_str[16] = "0.0.0.0";
static bool s_time_synced = false;

static inline float pressure_to_sea_level(float station_hpa, float altitude_m) {
    if (isnan(station_hpa) || altitude_m <= 0.0f) return station_hpa;
    // Barometric formula: P0 = P / (1 - h/44330)^5.255
    return station_hpa / powf(1.0f - (altitude_m / 44330.0f), 5.255f);
}

static void ensure_i2c_bus(void) {
    if (s_i2c_bus_handle) {
        return;
    }
    i2c_config_t conf = {
        .mode = I2C_MODE_MASTER,
        .sda_io_num = I2C_SDA,
        .scl_io_num = I2C_SCL,
        .sda_pullup_en = true,
        .scl_pullup_en = true,
        .master.clk_speed = I2C_FREQ_HZ,
        .clk_flags = 0,
    };
    s_i2c_bus_handle = i2c_bus_create(I2C_NUM_0, &conf);
    if (!s_i2c_bus_handle) {
        ESP_LOGE(TAG, "Failed to create I2C bus");
        return;
    }

    // Extract the native handle for other components (like display)
    s_i2c_bus = i2c_bus_get_internal_bus_handle(s_i2c_bus_handle);
    if (!s_i2c_bus) {
        ESP_LOGE(TAG, "Failed to get internal I2C bus handle");
    }
}

static void log_chip_info(void) {
    esp_chip_info_t chip_info;
    esp_chip_info(&chip_info);
    uint32_t flash_size = 0;
    esp_flash_get_size(NULL, &flash_size);

    ESP_LOGI(TAG, "Hello from the wine-cellar ESP32 sentinel!");
    ESP_LOGI(TAG,
             "Chip cores=%d WiFi%s%s rev=%d flash=%luMB",
             chip_info.cores,
             (chip_info.features & CHIP_FEATURE_BT) ? "/BT" : "",
             (chip_info.features & CHIP_FEATURE_BLE) ? "/BLE" : "",
             chip_info.revision,
             (unsigned long)(flash_size / (1024 * 1024)));
}

static void scan_i2c_bus(void) {
    if (!s_i2c_bus) {
        ensure_i2c_bus();
    }
    if (!s_i2c_bus) return; // Should not happen if ensure succeeded

    ESP_LOGI(TAG, "Scanning I2C bus on port %d", I2C_NUM_0);
    for (int address = 0x03; address <= 0x77; ++address) {
        esp_err_t err = i2c_master_probe(s_i2c_bus, address, 20);
        if (err == ESP_OK) {
            ESP_LOGI(TAG, " - Found device at 0x%02X", address);
        }
    }
}

static void init_nvs(void) {
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);
}

static void wifi_event_handler(void *arg,
                               esp_event_base_t event_base,
                               int32_t event_id,
                               void *event_data) {
    if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_START) {
        esp_wifi_connect();
    } else if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        if (s_retry_num < WIFI_MAXIMUM_RETRY) {
            esp_wifi_connect();
            s_retry_num++;
            ESP_LOGW(TAG, "Retrying Wi-Fi connection (%d/%d)", s_retry_num, WIFI_MAXIMUM_RETRY);
        } else {
            xEventGroupSetBits(s_wifi_event_group, WIFI_FAIL_BIT);
        }
    } else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        ip_event_got_ip_t *event = (ip_event_got_ip_t *)event_data;
        ESP_LOGI(TAG, "Got IP: " IPSTR, IP2STR(&event->ip_info.ip));
        esp_ip4addr_ntoa(&event->ip_info.ip, s_ip_str, sizeof(s_ip_str));
        s_retry_num = 0;
        xEventGroupSetBits(s_wifi_event_group, WIFI_CONNECTED_BIT);
    }
}

static void wifi_init_sta(void) {
    s_wifi_event_group = xEventGroupCreate();
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));

    esp_event_handler_instance_t instance_any_id;
    esp_event_handler_instance_t instance_got_ip;
    ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT,
                                                        ESP_EVENT_ANY_ID,
                                                        &wifi_event_handler,
                                                        NULL,
                                                        &instance_any_id));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(IP_EVENT,
                                                        IP_EVENT_STA_GOT_IP,
                                                        &wifi_event_handler,
                                                        NULL,
                                                        &instance_got_ip));

    wifi_config_t wifi_config = {
        .sta = {
            .ssid = WIFI_SSID,
            .password = WIFI_PASS,
            .threshold.authmode = WIFI_AUTH_WPA2_PSK,
        },
    };

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wifi_config));
    ESP_ERROR_CHECK(esp_wifi_start());

    EventBits_t bits = xEventGroupWaitBits(s_wifi_event_group,
                                           WIFI_CONNECTED_BIT | WIFI_FAIL_BIT,
                                           pdFALSE,
                                           pdFALSE,
                                           portMAX_DELAY);

    if (bits & WIFI_CONNECTED_BIT) {
        ESP_LOGI(TAG, "Connected to SSID:%s", WIFI_SSID);
    } else if (bits & WIFI_FAIL_BIT) {
        ESP_LOGE(TAG, "Failed to connect to SSID:%s", WIFI_SSID);
    } else {
        ESP_LOGE(TAG, "Unexpected event while waiting for Wi-Fi");
    }
}

static bool time_is_set(void) {
    time_t now = 0;
    time(&now);
    // Rough cutoff: any value before 2023 likely means SNTP has not run yet.
    return now > 1672531200;  // 2023-01-01T00:00:00Z
}

static bool sync_time_with_sntp(void) {
    if (s_time_synced && time_is_set()) {
        return true;
    }

    esp_sntp_config_t config = ESP_NETIF_SNTP_DEFAULT_CONFIG("pool.ntp.org");
    esp_err_t init_err = esp_netif_sntp_init(&config);
    if (init_err != ESP_OK && init_err != ESP_ERR_INVALID_STATE) {
        ESP_LOGE(TAG, "Failed to init SNTP: %s", esp_err_to_name(init_err));
        return false;
    }

    esp_err_t wait_err = esp_netif_sntp_sync_wait(pdMS_TO_TICKS(5000));
    if (wait_err == ESP_OK && time_is_set()) {
        s_time_synced = true;
        time_t now = 0;
        time(&now);
        ESP_LOGI(TAG, "Time synced (epoch=%ld)", (long)now);
        return true;
    }

    if (wait_err == ESP_ERR_TIMEOUT) {
        ESP_LOGW(TAG, "SNTP sync timed out");
    } else if (wait_err != ESP_OK) {
        ESP_LOGW(TAG, "SNTP sync wait failed: %s", esp_err_to_name(wait_err));
    }
    return false;
}

static bool format_iso8601_now(char *out, size_t out_len) {
    if (!time_is_set()) {
        return false;
    }

    time_t now = 0;
    struct tm timeinfo;
    time(&now);
    if (gmtime_r(&now, &timeinfo) == NULL) {
        return false;
    }

    size_t written = strftime(out, out_len, "%Y-%m-%dT%H:%M:%SZ", &timeinfo);
    return written > 0 && written < out_len;
}

static esp_err_t post_cellar_condition(void) {
    if (cellar_auth_ensure_access_token() != ESP_OK) {
        ESP_LOGW(TAG, "No valid access token; skipping post");
        static char claim_line[32];
        const char *claim = cellar_auth_claim_code();
        snprintf(claim_line, sizeof(claim_line), "%s", claim);
        cellar_display_status_t waiting = {
            .temperature_c = NAN,
            .pressure_hpa = NAN,
            .illuminance_lux = NAN,
            .http_status = -1,
            .post_err = ESP_FAIL,
            .ip_address = s_ip_str,
            .status_line = claim_line,
        };
        cellar_display_show(&waiting);
        return ESP_FAIL;
    }

    float temperature = NAN;
    float pressure = NAN;
    float illuminance_lux = NAN;
    char timestamp[32] = {0};
    bool have_timestamp = format_iso8601_now(timestamp, sizeof(timestamp));
    if (!have_timestamp && !s_time_synced) {
        s_time_synced = sync_time_with_sntp();
        have_timestamp = format_iso8601_now(timestamp, sizeof(timestamp));
    }

    if (s_bmp280_ready) {
        esp_err_t bmp_read_status = bmp280_read_float(&s_bmp280, &temperature, &pressure);
        if (bmp_read_status != ESP_OK) {
            ESP_LOGE(TAG, "BMP280 read failed: %s", esp_err_to_name(bmp_read_status));
        } else {
            ESP_LOGI(TAG, "BMP280: T=%.2fC P=%.2fhPa", temperature, pressure);
        }
    } else {
        ESP_LOGW(TAG, "BMP280 not initialized, skipping read");
    }

    float reported_pressure = pressure_to_sea_level(pressure, SENSOR_ALTITUDE_M);
    if (isnan(reported_pressure)) {
        reported_pressure = pressure;
    } else {
        ESP_LOGI(TAG, "Pressure station=%.2fhPa sea_level=%.2fhPa (alt=%.1fm)",
                 pressure,
                 reported_pressure,
                 (double)SENSOR_ALTITUDE_M);
    }

    cellar_measurement_t measurement = {
        .temperature_c = temperature,
        .pressure_hpa = reported_pressure,
        .illuminance_lux = illuminance_lux,
        .timestamp_iso8601 = have_timestamp ? timestamp : NULL,
        .device_id = DEVICE_ID,
    };

    cellar_http_result_t http_result;
    esp_err_t err = cellar_http_post(&measurement, &http_result);

    cellar_display_status_t display_status = {
        .temperature_c = temperature,
        .pressure_hpa = reported_pressure,
        .illuminance_lux = illuminance_lux,
        .http_status = http_result.status_code,
        .post_err = err,
        .ip_address = s_ip_str,
    };
    cellar_display_show(&display_status);

    if (http_result.status_code == 401 || http_result.status_code == 403) {
        ESP_LOGW(TAG, "Auth rejected (status %d), clearing tokens to force re-claim", http_result.status_code);
        cellar_auth_clear();
    }

    return err;
}

void app_main(void) {
    log_chip_info();
    init_nvs();
#if defined(RESET_CLAIM_CODE) && RESET_CLAIM_CODE
    cellar_auth_clear_claim_code();
#endif
    wifi_init_sta();
    cellar_auth_init();
    const char *claim = cellar_auth_claim_code();
    static char claim_line[32];
    snprintf(claim_line, sizeof(claim_line), "%s", claim);
    cellar_display_status_t waiting = {
        .temperature_c = NAN,
        .pressure_hpa = NAN,
        .illuminance_lux = NAN,
        .http_status = -1,
        .post_err = ESP_OK,
        .ip_address = s_ip_str,
        .status_line = claim_line,
    };
    if (!sync_time_with_sntp()) {
        ESP_LOGW(TAG, "Proceeding without SNTP timestamp; API will fill server time");
    }
    
    // Init I2C bus (and display)
    ensure_i2c_bus();
    scan_i2c_bus();

    // Init BMP280
    if (s_i2c_bus) {
        esp_err_t bmp_err = bmp280_init(s_i2c_bus, BMP280_ADDRESS, &s_bmp280);
        if (bmp_err != ESP_OK) {
             ESP_LOGE(TAG, "BMP280 init failed: %s", esp_err_to_name(bmp_err));
        } else {
             ESP_LOGI(TAG, "BMP280 init success");
             s_bmp280_ready = true;
        }
    }
    
    // Give the sensor a moment
    vTaskDelay(pdMS_TO_TICKS(50));

    if (cellar_display_init(s_i2c_bus) != ESP_OK) {
        ESP_LOGW(TAG, "Display init failed; continuing headless");
    }


    cellar_display_show(&waiting);

    while (true) {
        esp_err_t err = post_cellar_condition();
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "Telemetry send failed, will retry after delay");
        }
        vTaskDelay(pdMS_TO_TICKS(POST_INTERVAL_MS));
    }
}
