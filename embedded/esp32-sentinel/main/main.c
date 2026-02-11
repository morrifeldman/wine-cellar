#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "bme280.h"
#include "driver/i2c_master.h"
#include "esp_chip_info.h"
#include "esp_event.h"
#include "esp_flash.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_netif_sntp.h"
#include "esp_sntp.h"
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
#include "opt3001.h"
#include "veml7700.h"
#include "onewire_bus.h"
#include "ds18b20.h"
#include "config.h"  // User-provided Wi-Fi + API settings (see config.example.h)

#ifndef I2C_SDA
#define I2C_SDA 21
#endif
#ifndef I2C_SCL
#define I2C_SCL 22
#endif
#ifndef ONEWIRE_BUS_GPIO
#define ONEWIRE_BUS_GPIO 4
#endif
#ifndef I2C_FREQ_HZ
#define I2C_FREQ_HZ 100000
#endif

// Default to standard BME280 address if not in config
#ifndef BME280_ADDRESS
#define BME280_ADDRESS BME280_I2C_ADDRESS_DEFAULT
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



#define WIFI_CONNECTED_BIT BIT0
#define WIFI_FAIL_BIT BIT1
#define WIFI_MAXIMUM_RETRY 5
#ifndef POST_INTERVAL_MS
#define POST_INTERVAL_MS (30 * 1000)
#endif

static const char *TAG = "sentinel";
static EventGroupHandle_t s_wifi_event_group;
static int s_retry_num = 0;

// i2c_bus handle (wrapper)
static i2c_bus_handle_t s_i2c_bus_handle = NULL;
// Native handle extracted from wrapper (for cellar_display and bme280)
static i2c_master_bus_handle_t s_i2c_bus = NULL;

static bme280_handle_t s_bme280;
static bool s_bme280_ready = false;

static opt3001_handle_t s_opt3001;
static bool s_opt3001_ready = false;

static veml7700_handle_t s_veml7700;
static bool s_veml7700_ready = false;

#define DS18B20_MAX_DEVICES 4
static ds18b20_device_handle_t s_ds18b20_devices[DS18B20_MAX_DEVICES];
static uint64_t s_ds18b20_addrs[DS18B20_MAX_DEVICES];
static int s_ds18b20_count = 0;

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

    if (!esp_sntp_enabled()) {
        esp_sntp_config_t config = ESP_NETIF_SNTP_DEFAULT_CONFIG("pool.ntp.org");
        esp_err_t init_err = esp_netif_sntp_init(&config);
        if (init_err != ESP_OK) {
            ESP_LOGE(TAG, "Failed to init SNTP: %s", esp_err_to_name(init_err));
            return false;
        }
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
        
        cellar_display_status_t waiting = {0};
        waiting.temp_count = 0;
        waiting.lux_primary = NAN;
        waiting.lux_secondary = NAN;
        waiting.pressure_hpa = NAN;
        waiting.humidity_pct = NAN;
        waiting.http_status = -1;
        waiting.post_err = ESP_FAIL;
        snprintf(waiting.ip_address, sizeof(waiting.ip_address), "%s", s_ip_str);
        snprintf(waiting.status_line, sizeof(waiting.status_line), "%s", claim_line);

        cellar_display_update(&waiting);
        return ESP_FAIL;
    }

    float temp_bme = NAN;
    float pressure = NAN;
    float humidity = NAN;
    float lux_opt = NAN;
    float lux_veml = NAN;

    char timestamp[32] = {0};
    bool have_timestamp = format_iso8601_now(timestamp, sizeof(timestamp));
    if (!have_timestamp && !s_time_synced) {
        s_time_synced = sync_time_with_sntp();
        have_timestamp = format_iso8601_now(timestamp, sizeof(timestamp));
    }

    // BME280 Reading
    if (s_bme280_ready) {
        esp_err_t t_err = bme280_read_temperature(s_bme280, &temp_bme);
        esp_err_t p_err = bme280_read_pressure(s_bme280, &pressure);
        esp_err_t h_err = bme280_read_humidity(s_bme280, &humidity);
        
        if (t_err != ESP_OK || p_err != ESP_OK || h_err != ESP_OK) {
             ESP_LOGW(TAG, "BME280 read failed, retrying...");
             vTaskDelay(pdMS_TO_TICKS(100));
             t_err = bme280_read_temperature(s_bme280, &temp_bme);
             p_err = bme280_read_pressure(s_bme280, &pressure);
             h_err = bme280_read_humidity(s_bme280, &humidity);
        }

        if (t_err != ESP_OK || p_err != ESP_OK || h_err != ESP_OK) {
            ESP_LOGE(TAG, "BME280 read failed");
            temp_bme = NAN;
            pressure = NAN;
            humidity = NAN;
        } else {
            ESP_LOGI(TAG, "BME280: T=%.2fC P=%.2fhPa H=%.1f%%", temp_bme, pressure, humidity);
        }
    }

    // DS18B20 Readings (multiple sensors)
    float ds_temps[DS18B20_MAX_DEVICES];
    int ds_temp_count = 0;
    if (s_ds18b20_count > 0) {
        // Trigger conversion on all devices
        for (int i = 0; i < s_ds18b20_count; i++) {
            esp_err_t ds_err = ds18b20_trigger_temperature_conversion(s_ds18b20_devices[i]);
            if (ds_err != ESP_OK) {
                ESP_LOGE(TAG, "DS18B20[%d] trigger failed: %s", i, esp_err_to_name(ds_err));
            }
        }
        vTaskDelay(pdMS_TO_TICKS(800)); // 12-bit conversion time
        // Read temperatures
        for (int i = 0; i < s_ds18b20_count; i++) {
            float t = 0.0f;
            esp_err_t ds_err = ds18b20_get_temperature(s_ds18b20_devices[i], &t);
            if (ds_err == ESP_OK) {
                ESP_LOGI(TAG, "DS18B20[%d]: T=%.2fC", i, t);
                ds_temps[ds_temp_count++] = t;
            } else {
                ESP_LOGE(TAG, "DS18B20[%d] read failed: %s", i, esp_err_to_name(ds_err));
            }
        }
    }

    // OPT3001 Reading
    if (s_opt3001_ready) {
        esp_err_t opt_err = opt3001_read_lux(&s_opt3001, &lux_opt);
        if (opt_err != ESP_OK) {
             ESP_LOGE(TAG, "OPT3001 read failed: %s", esp_err_to_name(opt_err));
             lux_opt = NAN;
        } else {
             ESP_LOGI(TAG, "OPT3001: Lux=%.2f", lux_opt);
        }
    }
    
    // VEML7700 Reading
    if (s_veml7700_ready) {
        esp_err_t veml_err = veml7700_read_lux(&s_veml7700, &lux_veml);
        if (veml_err != ESP_OK) {
             ESP_LOGE(TAG, "VEML7700 read failed: %s", esp_err_to_name(veml_err));
             lux_veml = NAN;
        } else {
             ESP_LOGI(TAG, "VEML7700: Lux=%.2f", lux_veml);
        }
    }

    // Prefer OPT3001 as primary
    float lux_primary = NAN;
    float lux_secondary = NAN;
    if (!isnan(lux_opt)) {
        lux_primary = lux_opt;
        if (!isnan(lux_veml)) lux_secondary = lux_veml;
    } else {
        lux_primary = lux_veml;
    }

    float reported_pressure = pressure_to_sea_level(pressure, SENSOR_ALTITUDE_M);
    if (isnan(reported_pressure)) {
        reported_pressure = pressure;
    }

    // Build ROM-keyed temperatures JSON: {"28AABB...":12.50,"bme280":11.80}
    char temps_json[256] = {0};
    int tj_written = 0;
    bool has_any_temp = false;
    tj_written += snprintf(temps_json + tj_written, sizeof(temps_json) - tj_written, "{");
    for (int i = 0; i < ds_temp_count; i++) {
        if (has_any_temp) {
            tj_written += snprintf(temps_json + tj_written, sizeof(temps_json) - tj_written, ",");
        }
        tj_written += snprintf(temps_json + tj_written, sizeof(temps_json) - tj_written,
                               "\"%012llX\":%.2f", (unsigned long long)s_ds18b20_addrs[i], ds_temps[i]);
        has_any_temp = true;
    }
    if (!isnan(temp_bme)) {
        if (has_any_temp) {
            tj_written += snprintf(temps_json + tj_written, sizeof(temps_json) - tj_written, ",");
        }
        tj_written += snprintf(temps_json + tj_written, sizeof(temps_json) - tj_written,
                               "\"bme280\":%.2f", temp_bme);
        has_any_temp = true;
    }
    tj_written += snprintf(temps_json + tj_written, sizeof(temps_json) - tj_written, "}");

    // Prepare HTTP Payload
    cellar_measurement_t measurement = {
        .temperatures_json = has_any_temp ? temps_json : NULL,
        .pressure_hpa = reported_pressure,
        .humidity_pct = humidity,
        .illuminance_lux = lux_primary,
        .timestamp_iso8601 = have_timestamp ? timestamp : NULL,
        .device_id = cellar_auth_device_id(),
    };

    cellar_http_result_t http_result;
    esp_err_t err = cellar_http_post(&measurement, &http_result);

    // Update Display – populate all temperature sensors
    cellar_display_status_t display_status = {0};
    display_status.temp_count = 0;
    for (int i = 0; i < ds_temp_count && display_status.temp_count < CELLAR_DISPLAY_MAX_TEMPS; i++) {
        display_status.temps[display_status.temp_count] = ds_temps[i];
        snprintf(display_status.temp_labels[display_status.temp_count],
                 CELLAR_DISPLAY_LABEL_LEN, "DS%d", i + 1);
        display_status.temp_count++;
    }
    if (!isnan(temp_bme) && display_status.temp_count < CELLAR_DISPLAY_MAX_TEMPS) {
        display_status.temps[display_status.temp_count] = temp_bme;
        snprintf(display_status.temp_labels[display_status.temp_count],
                 CELLAR_DISPLAY_LABEL_LEN, "BME280");
        display_status.temp_count++;
    }
    display_status.lux_primary = lux_primary;
    display_status.lux_secondary = lux_secondary;
    display_status.pressure_hpa = reported_pressure;
    display_status.humidity_pct = humidity;
    display_status.http_status = http_result.status_code;
    display_status.post_err = err;
    snprintf(display_status.ip_address, sizeof(display_status.ip_address), "%s", s_ip_str);
    
    cellar_display_update(&display_status);

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
    
    cellar_display_status_t waiting = {0};
    waiting.temp_count = 0;
    waiting.lux_primary = NAN;
    waiting.lux_secondary = NAN;
    waiting.pressure_hpa = NAN;
    waiting.humidity_pct = NAN;
    waiting.http_status = -1;
    waiting.post_err = ESP_OK;
    snprintf(waiting.ip_address, sizeof(waiting.ip_address), "%s", s_ip_str);
    snprintf(waiting.status_line, sizeof(waiting.status_line), "%s", claim_line);

    if (!sync_time_with_sntp()) {
        ESP_LOGW(TAG, "Proceeding without SNTP timestamp; API will fill server time");
    }
    
    // Init I2C bus (and display)
    ensure_i2c_bus();
    scan_i2c_bus();

    // Init Sensors
    if (s_i2c_bus) {
        // Probe and Init BME280
        if (i2c_master_probe(s_i2c_bus, BME280_ADDRESS, 50) == ESP_OK) {
            ESP_LOGI(TAG, "Found BME280 at 0x%02X, initializing...", BME280_ADDRESS);
            s_bme280 = bme280_create(s_i2c_bus_handle, BME280_ADDRESS);
            if (!s_bme280) {
                 ESP_LOGE(TAG, "BME280 create failed");
            } else {
                 esp_err_t bme_err = bme280_default_init(s_bme280);
                 if (bme_err != ESP_OK) {
                      ESP_LOGE(TAG, "BME280 init failed: %s", esp_err_to_name(bme_err));
                 } else {
                      ESP_LOGI(TAG, "BME280 init success");
                      s_bme280_ready = true;
                 }
            }
        } else {
            ESP_LOGD(TAG, "BME280 not found at 0x%02X", BME280_ADDRESS);
        }

        // Probe and Init OPT3001
        if (i2c_master_probe(s_i2c_bus, OPT3001_I2C_ADDR_DEFAULT, 50) == ESP_OK) {
            ESP_LOGI(TAG, "Found OPT3001 at 0x%02X, initializing...", OPT3001_I2C_ADDR_DEFAULT);
            esp_err_t opt_err = opt3001_init(s_i2c_bus, OPT3001_I2C_ADDR_DEFAULT, &s_opt3001);
            if (opt_err != ESP_OK) {
                 ESP_LOGE(TAG, "OPT3001 init failed: %s", esp_err_to_name(opt_err));
            } else {
                 ESP_LOGI(TAG, "OPT3001 init success");
                 s_opt3001_ready = true;
            }
        } else {
            ESP_LOGD(TAG, "OPT3001 not found at 0x%02X", OPT3001_I2C_ADDR_DEFAULT);
        }

        // Probe and Init VEML7700
        if (i2c_master_probe(s_i2c_bus, VEML7700_I2C_ADDR_DEFAULT, 50) == ESP_OK) {
            ESP_LOGI(TAG, "Found VEML7700 at 0x%02X, initializing...", VEML7700_I2C_ADDR_DEFAULT);
            esp_err_t veml_err = veml7700_init(s_i2c_bus, VEML7700_I2C_ADDR_DEFAULT, &s_veml7700);
            if (veml_err != ESP_OK) {
                 ESP_LOGE(TAG, "VEML7700 init failed: %s", esp_err_to_name(veml_err));
            } else {
                 ESP_LOGI(TAG, "VEML7700 init success");
                 s_veml7700_ready = true;
            }
        } else {
             ESP_LOGD(TAG, "VEML7700 not found at 0x%02X", VEML7700_I2C_ADDR_DEFAULT);
        }
    }
    
    // Init 1-Wire (DS18B20) on GPIO 4
    onewire_bus_handle_t ow_bus = NULL;
    onewire_bus_config_t bus_config = {
        .bus_gpio_num = ONEWIRE_BUS_GPIO,
        .flags.en_pull_up = true, // Enables the ESP32 internal ~45k pull-up
    };
    onewire_bus_rmt_config_t rmt_config = {
        .max_rx_bytes = 10,
    };

    // Create 1-Wire bus and enumerate all DS18B20 devices
    if (onewire_new_bus_rmt(&bus_config, &rmt_config, &ow_bus) == ESP_OK) {
        onewire_device_iter_handle_t iter = NULL;
        onewire_device_t next_onewire_device;
        if (onewire_new_device_iter(ow_bus, &iter) == ESP_OK) {
            ESP_LOGI(TAG, "Scanning 1-Wire bus on GPIO %d...", ONEWIRE_BUS_GPIO);
            esp_err_t search_result;
            do {
                search_result = onewire_device_iter_get_next(iter, &next_onewire_device);
                if (search_result == ESP_OK) {
                    ds18b20_config_t ds_cfg = {};
                    ds18b20_device_handle_t dev = NULL;
                    if (ds18b20_new_device_from_enumeration(&next_onewire_device, &ds_cfg, &dev) == ESP_OK) {
                        ds18b20_set_resolution(dev, DS18B20_RESOLUTION_12B);
                        s_ds18b20_devices[s_ds18b20_count] = dev;
                        s_ds18b20_addrs[s_ds18b20_count] = next_onewire_device.address;
                        s_ds18b20_count++;
                        ESP_LOGI(TAG, "DS18B20[%d] init success (addr: %016llX)",
                                 s_ds18b20_count - 1, next_onewire_device.address);
                    } else {
                        ESP_LOGW(TAG, "1-Wire device at %016llX is not a DS18B20",
                                 next_onewire_device.address);
                    }
                }
            } while (search_result == ESP_OK && s_ds18b20_count < DS18B20_MAX_DEVICES);
            onewire_del_device_iter(iter);

            if (s_ds18b20_count == 0) {
                ESP_LOGW(TAG, "No DS18B20 devices found on 1-Wire bus");
            } else {
                ESP_LOGI(TAG, "Found %d DS18B20 device(s)", s_ds18b20_count);
            }
        }
    } else {
        ESP_LOGE(TAG, "Failed to create 1-Wire bus RMT");
    }

    // Give the sensor a moment
    vTaskDelay(pdMS_TO_TICKS(50));

    if (cellar_display_init(s_i2c_bus) != ESP_OK) {
        ESP_LOGW(TAG, "Display init failed; continuing headless");
    } else {
        cellar_display_start();
    }


    cellar_display_update(&waiting);

    // Allow sensors to settle
    vTaskDelay(pdMS_TO_TICKS(2000));

    while (true) {
        esp_err_t err = post_cellar_condition();
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "Telemetry send failed, will retry after delay");
        }
        vTaskDelay(pdMS_TO_TICKS(POST_INTERVAL_MS));
    }
}
