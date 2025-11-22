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
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_panel_ssd1306.h"
#include "esp_lcd_types.h"
#include "esp_netif.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/task.h"
#include "nvs_flash.h"

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
static esp_lcd_panel_io_handle_t s_panel_io = NULL;
static esp_lcd_panel_handle_t s_panel = NULL;
static bool s_display_ok = false;
static char s_ip_str[16] = "0.0.0.0";
static uint8_t s_framebuffer[OLED_WIDTH * OLED_HEIGHT / 8] = {0};

// Minimal 5x7 ASCII font (0x20-0x7F), columns packed LSB = top row.
static const uint8_t FONT_5X7[][5] = {
    {0x00, 0x00, 0x00, 0x00, 0x00},  // space
    {0x00, 0x00, 0x5F, 0x00, 0x00},  // !
    {0x00, 0x07, 0x00, 0x07, 0x00},  // "
    {0x14, 0x7F, 0x14, 0x7F, 0x14},  // #
    {0x24, 0x2A, 0x7F, 0x2A, 0x12},  // $
    {0x23, 0x13, 0x08, 0x64, 0x62},  // %
    {0x36, 0x49, 0x55, 0x22, 0x50},  // &
    {0x00, 0x05, 0x03, 0x00, 0x00},  // '
    {0x00, 0x1C, 0x22, 0x41, 0x00},  // (
    {0x00, 0x41, 0x22, 0x1C, 0x00},  // )
    {0x14, 0x08, 0x3E, 0x08, 0x14},  // *
    {0x08, 0x08, 0x3E, 0x08, 0x08},  // +
    {0x00, 0x50, 0x30, 0x00, 0x00},  // ,
    {0x08, 0x08, 0x08, 0x08, 0x08},  // -
    {0x00, 0x60, 0x60, 0x00, 0x00},  // .
    {0x20, 0x10, 0x08, 0x04, 0x02},  // /
    {0x3E, 0x51, 0x49, 0x45, 0x3E},  // 0
    {0x00, 0x42, 0x7F, 0x40, 0x00},  // 1
    {0x72, 0x49, 0x49, 0x49, 0x46},  // 2
    {0x21, 0x41, 0x49, 0x4D, 0x33},  // 3
    {0x18, 0x14, 0x12, 0x7F, 0x10},  // 4
    {0x27, 0x45, 0x45, 0x45, 0x39},  // 5
    {0x3C, 0x4A, 0x49, 0x49, 0x31},  // 6
    {0x41, 0x21, 0x11, 0x09, 0x07},  // 7
    {0x36, 0x49, 0x49, 0x49, 0x36},  // 8
    {0x46, 0x49, 0x49, 0x29, 0x1E},  // 9
    {0x00, 0x36, 0x36, 0x00, 0x00},  // :
    {0x00, 0x56, 0x36, 0x00, 0x00},  // ;
    {0x08, 0x14, 0x22, 0x41, 0x00},  // <
    {0x14, 0x14, 0x14, 0x14, 0x14},  // =
    {0x00, 0x41, 0x22, 0x14, 0x08},  // >
    {0x02, 0x01, 0x59, 0x09, 0x06},  // ?
    {0x3E, 0x41, 0x5D, 0x55, 0x1E},  // @
    {0x7C, 0x12, 0x11, 0x12, 0x7C},  // A
    {0x7F, 0x49, 0x49, 0x49, 0x36},  // B
    {0x3E, 0x41, 0x41, 0x41, 0x22},  // C
    {0x7F, 0x41, 0x41, 0x22, 0x1C},  // D
    {0x7F, 0x49, 0x49, 0x49, 0x41},  // E
    {0x7F, 0x09, 0x09, 0x09, 0x01},  // F
    {0x3E, 0x41, 0x49, 0x49, 0x7A},  // G
    {0x7F, 0x08, 0x08, 0x08, 0x7F},  // H
    {0x00, 0x41, 0x7F, 0x41, 0x00},  // I
    {0x20, 0x40, 0x41, 0x3F, 0x01},  // J
    {0x7F, 0x08, 0x14, 0x22, 0x41},  // K
    {0x7F, 0x40, 0x40, 0x40, 0x40},  // L
    {0x7F, 0x02, 0x0C, 0x02, 0x7F},  // M
    {0x7F, 0x04, 0x08, 0x10, 0x7F},  // N
    {0x3E, 0x41, 0x41, 0x41, 0x3E},  // O
    {0x7F, 0x09, 0x09, 0x09, 0x06},  // P
    {0x3E, 0x41, 0x51, 0x21, 0x5E},  // Q
    {0x7F, 0x09, 0x19, 0x29, 0x46},  // R
    {0x26, 0x49, 0x49, 0x49, 0x32},  // S
    {0x01, 0x01, 0x7F, 0x01, 0x01},  // T
    {0x3F, 0x40, 0x40, 0x40, 0x3F},  // U
    {0x1F, 0x20, 0x40, 0x20, 0x1F},  // V
    {0x3F, 0x40, 0x38, 0x40, 0x3F},  // W
    {0x63, 0x14, 0x08, 0x14, 0x63},  // X
    {0x07, 0x08, 0x70, 0x08, 0x07},  // Y
    {0x61, 0x51, 0x49, 0x45, 0x43},  // Z
    {0x00, 0x7F, 0x41, 0x41, 0x00},  // [
    {0x02, 0x04, 0x08, 0x10, 0x20},  // backslash
    {0x00, 0x41, 0x41, 0x7F, 0x00},  // ]
    {0x04, 0x02, 0x01, 0x02, 0x04},  // ^
    {0x40, 0x40, 0x40, 0x40, 0x40},  // _
    {0x00, 0x01, 0x02, 0x04, 0x00},  // `
    {0x20, 0x54, 0x54, 0x54, 0x78},  // a
    {0x7F, 0x48, 0x44, 0x44, 0x38},  // b
    {0x38, 0x44, 0x44, 0x44, 0x20},  // c
    {0x38, 0x44, 0x44, 0x48, 0x7F},  // d
    {0x38, 0x54, 0x54, 0x54, 0x18},  // e
    {0x08, 0x7E, 0x09, 0x01, 0x02},  // f
    {0x0C, 0x52, 0x52, 0x52, 0x3E},  // g
    {0x7F, 0x08, 0x04, 0x04, 0x78},  // h
    {0x00, 0x44, 0x7D, 0x40, 0x00},  // i
    {0x20, 0x40, 0x44, 0x3D, 0x00},  // j
    {0x7F, 0x10, 0x28, 0x44, 0x00},  // k
    {0x00, 0x41, 0x7F, 0x40, 0x00},  // l
    {0x7C, 0x04, 0x18, 0x04, 0x78},  // m
    {0x7C, 0x08, 0x04, 0x04, 0x78},  // n
    {0x38, 0x44, 0x44, 0x44, 0x38},  // o
    {0x7C, 0x14, 0x14, 0x14, 0x08},  // p
    {0x08, 0x14, 0x14, 0x18, 0x7C},  // q
    {0x7C, 0x08, 0x04, 0x04, 0x08},  // r
    {0x48, 0x54, 0x54, 0x54, 0x20},  // s
    {0x04, 0x3F, 0x44, 0x40, 0x20},  // t
    {0x3C, 0x40, 0x40, 0x20, 0x7C},  // u
    {0x1C, 0x20, 0x40, 0x20, 0x1C},  // v
    {0x3C, 0x40, 0x30, 0x40, 0x3C},  // w
    {0x44, 0x28, 0x10, 0x28, 0x44},  // x
    {0x0C, 0x50, 0x50, 0x50, 0x3C},  // y
    {0x44, 0x64, 0x54, 0x4C, 0x44},  // z
    {0x00, 0x08, 0x36, 0x41, 0x00},  // {
    {0x00, 0x00, 0x7F, 0x00, 0x00},  // |
    {0x00, 0x41, 0x36, 0x08, 0x00},  // }
    {0x08, 0x04, 0x08, 0x10, 0x08},  // ~
};

static inline float c_to_f(float temp_c) {
    return isnan(temp_c) ? NAN : (temp_c * 9.0f / 5.0f) + 32.0f;
}

static inline void set_pixel(int x, int y, bool on) {
    if (x < 0 || x >= OLED_WIDTH || y < 0 || y >= OLED_HEIGHT) {
        return;
    }
    size_t index = (y / 8) * OLED_WIDTH + x;
    uint8_t mask = 1u << (y % 8);
    if (on) {
        s_framebuffer[index] |= mask;
    } else {
        s_framebuffer[index] &= ~mask;
    }
}

static void clear_framebuffer(void) {
    memset(s_framebuffer, 0, sizeof(s_framebuffer));
}

static void fill_page(uint8_t page, bool on) {
    if (page >= OLED_HEIGHT / 8) return;
    uint8_t *row = &s_framebuffer[page * OLED_WIDTH];
    memset(row, on ? 0xFF : 0x00, OLED_WIDTH);
}

static void draw_char(int x, int y, char c, bool invert) {
    if (c < 0x20 || c > 0x7F) {
        c = '?';
    }
    const uint8_t *glyph = FONT_5X7[c - 0x20];
    for (int col = 0; col < 5; ++col) {
        uint8_t col_bits = glyph[col];
        for (int row = 0; row < 7; ++row) {
            bool pixel_on = (col_bits >> row) & 0x1;
            set_pixel(x + col, y + row, invert ? !pixel_on : pixel_on);
        }
    }
    // One-column spacing
    for (int row = 0; row < 7; ++row) {
        set_pixel(x + 5, y + row, invert ? true : false);
    }
}

static void draw_char_scaled(int x, int y, char c, int scale, bool invert) {
    if (scale <= 1) {
        draw_char(x, y, c, invert);
        return;
    }
    if (c < 0x20 || c > 0x7F) {
        c = '?';
    }
    const uint8_t *glyph = FONT_5X7[c - 0x20];
    for (int col = 0; col < 5; ++col) {
        uint8_t col_bits = glyph[col];
        for (int row = 0; row < 7; ++row) {
            bool pixel_on = (col_bits >> row) & 0x1;
            for (int dy = 0; dy < scale; ++dy) {
                for (int dx = 0; dx < scale; ++dx) {
                    set_pixel(x + col * scale + dx, y + row * scale + dy, invert ? !pixel_on : pixel_on);
                }
            }
        }
    }
    // spacing column
    for (int row = 0; row < 7 * scale; ++row) {
        for (int dx = 0; dx < scale; ++dx) {
            set_pixel(x + 5 * scale + dx, y + row, invert ? true : false);
        }
    }
}

static void draw_text_line(uint8_t page, const char *text, bool invert) {
    if (page >= OLED_HEIGHT / 8) return;
    if (invert) {
        fill_page(page, true);
    }
    char clipped[(OLED_WIDTH / 6) + 1];
    strncpy(clipped, text, sizeof(clipped) - 1);
    clipped[sizeof(clipped) - 1] = '\0';

    int x = 0;
    int y = page * 8;
    for (size_t i = 0; clipped[i] != '\0' && x + 5 < OLED_WIDTH; ++i, x += 6) {
        draw_char(x, y, clipped[i], invert);
    }
}

static void draw_text_scaled(int x, int y, const char *text, int scale, bool invert) {
    if (scale < 1) scale = 1;
    int cursor_x = x;
    for (size_t i = 0; text[i] != '\0' && cursor_x + (6 * scale) <= OLED_WIDTH; ++i) {
        draw_char_scaled(cursor_x, y, text[i], scale, invert);
        cursor_x += 6 * scale;
    }
}

static void flush_display(void) {
    if (!s_display_ok) return;
    esp_err_t err = esp_lcd_panel_draw_bitmap(s_panel, 0, 0, OLED_WIDTH, OLED_HEIGHT, s_framebuffer);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "SSD1306 flush failed: %s", esp_err_to_name(err));
    }
}

static void display_status(float temperature, float pressure, int http_status, esp_err_t post_err) {
    if (!s_display_ok) return;
    clear_framebuffer();

    char line_temp[32];
    char line_ip[32];
    char line_press[32];
    char line_post[32];

    float display_temp = temperature;
    char temp_unit = 'C';
#ifdef DISPLAY_TEMP_FAHRENHEIT
    display_temp = c_to_f(temperature);
    temp_unit = 'F';
#endif

    if (!isnan(display_temp)) {
        snprintf(line_temp, sizeof(line_temp), "T %5.1f%c", display_temp, temp_unit);
    } else {
        snprintf(line_temp, sizeof(line_temp), "T --.-%c", temp_unit);
    }

    snprintf(line_ip, sizeof(line_ip), "IP %s", s_ip_str);

    if (!isnan(pressure)) {
        snprintf(line_press, sizeof(line_press), "P %4.0f hPa", pressure);
    } else {
        snprintf(line_press, sizeof(line_press), "P ----");
    }

    if (post_err == ESP_OK) {
        snprintf(line_post, sizeof(line_post), "POST %d", http_status);
    } else {
        snprintf(line_post, sizeof(line_post), "POST %s", esp_err_to_name(post_err));
    }

    // Big temperature on the top (two-line height), normal text below
    draw_text_scaled(0, 0, line_temp, 2, false);  // ~14 px tall
    draw_text_line(2, line_ip, false);            // starts at y=16
    draw_text_line(3, line_press, false);         // y=24
    draw_text_line(4, line_post, false);          // y=32
    flush_display();
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
    esp_lcd_panel_io_i2c_config_t io_config = {
        .dev_addr = OLED_ADDRESS,
        .scl_speed_hz = I2C_FREQ_HZ,
        .control_phase_bytes = 1,
        .dc_bit_offset = 6,  // SSD1306 uses control byte with D/C# at bit 6
        .lcd_cmd_bits = 8,
        .lcd_param_bits = 8,
        .on_color_trans_done = NULL,
        .user_ctx = NULL,
        .flags = {
            .dc_low_on_data = 0,
            .disable_control_phase = 0,
        },
    };

    esp_err_t err = esp_lcd_new_panel_io_i2c(s_i2c_bus, &io_config, &s_panel_io);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "SSD1306 IO init failed: %s", esp_err_to_name(err));
        return;
    }

    esp_lcd_panel_ssd1306_config_t ssd1306_cfg = {
        .height = OLED_HEIGHT,
    };

    esp_lcd_panel_dev_config_t panel_config = {
        .reset_gpio_num = -1,
        .color_space = ESP_LCD_COLOR_SPACE_MONOCHROME,
        .bits_per_pixel = 1,
        .vendor_config = &ssd1306_cfg,
    };

    err = esp_lcd_new_panel_ssd1306(s_panel_io, &panel_config, &s_panel);
    if (err == ESP_OK) {
        ESP_ERROR_CHECK(esp_lcd_panel_reset(s_panel));
        ESP_ERROR_CHECK(esp_lcd_panel_init(s_panel));
        ESP_ERROR_CHECK(esp_lcd_panel_disp_on_off(s_panel, true));
        // Many small OLED modules are mounted upside-down relative to the panel's native orientation.
        // Mirror both axes to match the previous layout where text appears at the top.
        ESP_ERROR_CHECK(esp_lcd_panel_mirror(s_panel, true, true));
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
#ifdef SENSOR_TEMP_IS_FAHRENHEIT
        // Some drop-in sensors ship configured for Fahrenheit; normalize to Celsius.
        bmp_temp = (bmp_temp - 32.0f) * (5.0f / 9.0f);
#endif
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
