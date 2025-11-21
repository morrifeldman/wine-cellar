#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "driver/i2c_master.h"
#include "esp_chip_info.h"
#include "esp_event.h"
#include "esp_flash.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/task.h"
#include "nvs_flash.h"
#include "ssd1306.h"

#include "config.h"  // User-provided Wi-Fi + API settings (see config.example.h)
// Humidity sensor currently disabled; future BME280 can share this bus
#include "bmp085.h"

#ifndef I2C_SDA
#define I2C_SDA 21
#endif
#ifndef I2C_SCL
#define I2C_SCL 22
#endif
#ifndef I2C_FREQ_HZ
#define I2C_FREQ_HZ 100000
#endif

#ifndef OLED_ADDRESS
#define OLED_ADDRESS 0x3C
#endif
#ifndef OLED_WIDTH
#define OLED_WIDTH 128
#endif
#ifndef OLED_HEIGHT
#define OLED_HEIGHT 64
#endif

#ifndef WIFI_SSID
#error "WIFI_SSID must be defined in config.h"
#endif

#ifndef WIFI_PASS
#error "WIFI_PASS must be defined in config.h"
#endif

#ifndef CELLAR_API_URL
#error "CELLAR_API_URL must be defined in config.h"
#endif

#ifndef DEVICE_JWT
#error "DEVICE_JWT must be defined in config.h"
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
static bool s_i2c_bus_initialized = false;
static i2c_master_bus_handle_t s_i2c_bus = NULL;
static ssd1306_handle_t s_display = NULL;
static bool s_display_ok = false;
static char s_ip_str[16] = "0.0.0.0";

static void display_line(uint8_t page, const char *text, bool invert) {
    char clipped[17];
    strncpy(clipped, text, sizeof(clipped) - 1);
    clipped[sizeof(clipped) - 1] = '\0';
    ssd1306_display_text(s_display, page, clipped, invert);
}

static void display_status(float temperature, float pressure, int http_status, esp_err_t post_err) {
    if (!s_display_ok) return;
    ssd1306_clear_display(s_display, false);

    char line0[32];
    char line1[32];
    char line2[32];

    snprintf(line0, sizeof(line0), "IP %s", s_ip_str);
    if (!isnan(temperature) && !isnan(pressure)) {
        snprintf(line1, sizeof(line1), "T %5.1fC P %4.0f", temperature, pressure);
    } else if (!isnan(temperature)) {
        snprintf(line1, sizeof(line1), "T %5.1fC", temperature);
    } else {
        snprintf(line1, sizeof(line1), "T --.-C");
    }

    if (post_err == ESP_OK) {
        snprintf(line2, sizeof(line2), "POST %d", http_status);
    } else {
        snprintf(line2, sizeof(line2), "POST %s", esp_err_to_name(post_err));
    }

    display_line(0, line0, false);
    display_line(1, line1, false);
    display_line(2, line2, false);
}

static void ensure_i2c_bus(void) {
    if (s_i2c_bus_initialized) {
        return;
    }
    i2c_master_bus_config_t bus_cfg = {
        .i2c_port = I2C_NUM_0,
        .sda_io_num = I2C_SDA,
        .scl_io_num = I2C_SCL,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags = {
            .enable_internal_pullup = true,
        },
    };

    ESP_ERROR_CHECK(i2c_new_master_bus(&bus_cfg, &s_i2c_bus));
    s_i2c_bus_initialized = true;
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
    if (!s_i2c_bus_initialized) {
        ensure_i2c_bus();
    }
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

static esp_err_t http_event_handler(esp_http_client_event_t *evt) {
    switch (evt->event_id) {
        case HTTP_EVENT_ON_DATA:
            ESP_LOGD(TAG, "HTTP data len=%d", evt->data_len);
            break;
        case HTTP_EVENT_ERROR:
            ESP_LOGW(TAG, "HTTP event error");
            break;
        default:
            break;
    }
    return ESP_OK;
}

static void init_display(void) {
    ssd1306_config_t cfg = I2C_SSD1306_128x64_CONFIG_DEFAULT;
    cfg.i2c_address = OLED_ADDRESS;
    cfg.i2c_clock_speed = I2C_FREQ_HZ;
    cfg.flip_enabled = false;

    esp_err_t err = ssd1306_init(s_i2c_bus, &cfg, &s_display);
    if (err == ESP_OK && s_display != NULL) {
        s_display_ok = true;
        ESP_LOGI(TAG, "SSD1306 ready at 0x%02X (%dx%d)", OLED_ADDRESS, OLED_WIDTH, OLED_HEIGHT);
    } else {
        ESP_LOGW(TAG, "SSD1306 init failed: %s", esp_err_to_name(err));
    }
}

static void update_display(float temperature, float pressure, int http_status, esp_err_t post_err) {
    display_status(temperature, pressure, http_status, post_err);
}

static esp_err_t post_cellar_condition(void) {
    float temperature = NAN;
    float pressure = NAN;

    float bmp_temp = temperature;
    esp_err_t bmp_err = bmp085_read(isnan(bmp_temp) ? &bmp_temp : NULL, &pressure);
    if (bmp_err != ESP_OK) {
        ESP_LOGE(TAG, "BMP085 read failed: %s", esp_err_to_name(bmp_err));
    } else if (isnan(temperature) && !isnan(bmp_temp)) {
        temperature = bmp_temp;
    }

    char payload[256];
    int written = snprintf(payload, sizeof(payload), "{\"device_id\":\"%s\"", DEVICE_ID);

    bool has_measurement = false;

    if (!isnan(temperature)) {
        has_measurement = true;
        written += snprintf(payload + written,
                            sizeof(payload) - written,
                            ",\"temperature_c\":%.2f",
                            temperature);
    }
    if (!isnan(pressure)) {
        has_measurement = true;
        written += snprintf(payload + written,
                            sizeof(payload) - written,
                            ",\"pressure_hpa\":%.2f",
                            pressure);
    }

    written += snprintf(payload + written, sizeof(payload) - written, "}");

    if (!has_measurement) {
        ESP_LOGE(TAG, "No valid measurements to send");
        return ESP_FAIL;
    }
    if (written < 0 || written >= (int)sizeof(payload)) {
        ESP_LOGE(TAG, "Payload buffer too small");
        return ESP_FAIL;
    }

    esp_http_client_config_t config = {
        .url = CELLAR_API_URL,
        .event_handler = http_event_handler,
        .timeout_ms = 8000,
#ifdef CELLAR_API_CERT_PEM
        .cert_pem = (const char *)CELLAR_API_CERT_PEM,
#endif
    };

    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (!client) {
        ESP_LOGE(TAG, "Failed to init HTTP client");
        return ESP_FAIL;
    }

    esp_http_client_set_method(client, HTTP_METHOD_POST);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_header(client, "Authorization", "Bearer " DEVICE_JWT);
    esp_http_client_set_post_field(client, payload, written);

    ESP_LOGI(TAG, "POST %s", CELLAR_API_URL);
    esp_err_t err = esp_http_client_perform(client);
    int status = -1;
    if (err == ESP_OK) {
        status = esp_http_client_get_status_code(client);
        ESP_LOGI(TAG, "POST status=%d, content_length=%lld", status, esp_http_client_get_content_length(client));
    } else {
        ESP_LOGE(TAG, "HTTP POST failed: %s", esp_err_to_name(err));
    }

    esp_http_client_cleanup(client);
    update_display(temperature, pressure, status, err);
    return err;
}

void app_main(void) {
    log_chip_info();
    init_nvs();
    wifi_init_sta();
    ensure_i2c_bus();
    esp_err_t bmp_status = bmp085_init(s_i2c_bus);
    if (bmp_status != ESP_OK) {
        ESP_LOGE(TAG, "BMP085 init failed: %s", esp_err_to_name(bmp_status));
    }
    init_display();
    // Initial splash
    display_status(NAN, NAN, -1, ESP_OK);
    scan_i2c_bus();

    while (true) {
        esp_err_t err = post_cellar_condition();
        if (err != ESP_OK) {
            ESP_LOGW(TAG, "Telemetry send failed, will retry after delay");
        }
        vTaskDelay(pdMS_TO_TICKS(POST_INTERVAL_MS));
    }
}
