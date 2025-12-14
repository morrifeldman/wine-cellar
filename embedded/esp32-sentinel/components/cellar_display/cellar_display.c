#include "cellar_display.h"

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "config.h"
#include "esp_err.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_panel_ssd1306.h"
#include "esp_lcd_types.h"
#include "esp_log.h"

// Default pins/addresses match config.h fallback values
#ifndef OLED_ADDRESS
#define OLED_ADDRESS 0x3C
#endif
#ifndef OLED_WIDTH
#define OLED_WIDTH 128
#endif
#ifndef OLED_HEIGHT
#define OLED_HEIGHT 64
#endif
#ifndef I2C_FREQ_HZ
#define I2C_FREQ_HZ 100000
#endif

static const char *TAG = "cellar_display";
static esp_lcd_panel_io_handle_t s_panel_io = NULL;
static esp_lcd_panel_handle_t s_panel = NULL;
static bool s_display_ok = false;
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
    {0x7F, 0x10, 0x28, 0x44, 0x00},  // K
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
    {0x06, 0x09, 0x09, 0x06, 0x00},  // ^ (degree symbol stand-in)
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

static inline float hpa_to_inhg(float pressure_hpa) {
    return isnan(pressure_hpa) ? NAN : pressure_hpa * 0.029529983f;
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

static void render_status(const cellar_display_status_t *status) {
    clear_framebuffer();

    char line_temp[32];
    char line_ip[32];
    char line_press[32];
    char line_lux[32];
    char line_post[32];
    char line_status[32] = {0};

    float display_temp = status->temperature_c;
    char temp_unit = 'C';
#ifdef DISPLAY_TEMP_FAHRENHEIT
    display_temp = c_to_f(status->temperature_c);
    temp_unit = 'F';
#endif

    if (!isnan(display_temp)) {
        snprintf(line_temp, sizeof(line_temp), "%0.1f ^%c", display_temp, temp_unit);
    } else {
        snprintf(line_temp, sizeof(line_temp), "--.- ^%c", temp_unit);
    }

    if (!isnan(status->pressure_hpa)) {
#ifdef DISPLAY_PRESSURE_INHG
        float pressure_inhg = hpa_to_inhg(status->pressure_hpa);
        snprintf(line_press, sizeof(line_press), "%4.2f inHg", pressure_inhg);
#else
        snprintf(line_press, sizeof(line_press), "%4.0f hPa", status->pressure_hpa);
#endif
    } else {
        snprintf(line_press, sizeof(line_press), "----");
    }

    if (!isnan(status->illuminance_lux)) {
        snprintf(line_lux, sizeof(line_lux), "%0.0f lx", status->illuminance_lux);
    } else {
        snprintf(line_lux, sizeof(line_lux), "---- lx");
    }

    const char *ip = status->ip_address ? status->ip_address : "0.0.0.0";
    snprintf(line_ip, sizeof(line_ip), "IP %s", ip);

    if (status->post_err == ESP_OK) {
        snprintf(line_post, sizeof(line_post), "POST %d", status->http_status);
    } else {
        snprintf(line_post, sizeof(line_post), "POST %s", esp_err_to_name(status->post_err));
    }

    if (status->status_line && status->status_line[0]) {
        snprintf(line_status, sizeof(line_status), "%s", status->status_line);
        draw_text_scaled(0, 0, line_status, 2, false);
        draw_text_line(6, line_ip, false);
        draw_text_line(7, line_post, false);
    } else {
        draw_text_scaled(0, 0, line_temp, 2, false);
        draw_text_scaled(0, 16, line_press, 2, false);
        draw_text_scaled(0, 32, line_lux, 2, false);
        draw_text_line(6, line_ip, false);
        draw_text_line(7, line_post, false);
    }
    flush_display();
}

esp_err_t cellar_display_init(i2c_master_bus_handle_t bus) {
    if (s_display_ok) {
        return ESP_OK;
    }

    esp_lcd_panel_io_i2c_config_t io_config = {
        .dev_addr = OLED_ADDRESS,
        .scl_speed_hz = I2C_FREQ_HZ,
        .control_phase_bytes = 1,
        .dc_bit_offset = 6,
        .lcd_cmd_bits = 8,
        .lcd_param_bits = 8,
        .on_color_trans_done = NULL,
        .user_ctx = NULL,
        .flags = {
            .dc_low_on_data = 0,
            .disable_control_phase = 0,
        },
    };

    esp_err_t err = esp_lcd_new_panel_io_i2c(bus, &io_config, &s_panel_io);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "SSD1306 IO init failed: %s", esp_err_to_name(err));
        return err;
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
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "SSD1306 init failed: %s", esp_err_to_name(err));
        return err;
    }

    err = esp_lcd_panel_reset(s_panel);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "SSD1306 reset failed: %s", esp_err_to_name(err));
        return err;
    }

    err = esp_lcd_panel_init(s_panel);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "SSD1306 panel init failed: %s", esp_err_to_name(err));
        return err;
    }

    err = esp_lcd_panel_disp_on_off(s_panel, true);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "SSD1306 disp_on failed: %s", esp_err_to_name(err));
        return err;
    }
    
    // Optional mirror config
    esp_lcd_panel_mirror(s_panel, true, true);
    
    s_display_ok = true;
    ESP_LOGI(TAG, "SSD1306 ready at 0x%02X (%dx%d)", OLED_ADDRESS, OLED_WIDTH, OLED_HEIGHT);
    clear_framebuffer();
    return ESP_OK;
}

bool cellar_display_ready(void) { return s_display_ok; }

void cellar_display_show(const cellar_display_status_t *status) {
    if (!s_display_ok || !status) return;
    render_status(status);
}
