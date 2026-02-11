#pragma once

#include <stdbool.h>

#include "driver/i2c_master.h"
#include "esp_err.h"

#define CELLAR_DISPLAY_MAX_TEMPS 6
#define CELLAR_DISPLAY_LABEL_LEN 8

typedef struct {
    float temps[CELLAR_DISPLAY_MAX_TEMPS];
    char  temp_labels[CELLAR_DISPLAY_MAX_TEMPS][CELLAR_DISPLAY_LABEL_LEN];
    int   temp_count;
    float lux_primary;       // e.g. OPT3001
    float lux_secondary;     // e.g. VEML7700
    float pressure_hpa;
    float humidity_pct;
    int http_status;         // HTTP status code returned by the API (or -1 on failure)
    esp_err_t post_err;      // esp_err_t from the POST attempt
    char ip_address[16];     // dotted-quad string
    char status_line[32];    // optional status message (e.g., "Waiting for approval")
} cellar_display_status_t;

// Initialize the SSD1306 display on the provided I2C bus. Safe to call once.
esp_err_t cellar_display_init(i2c_master_bus_handle_t bus);

// Starts the display update task. Call this after init.
void cellar_display_start(void);

// Returns true if the display was successfully initialized.
bool cellar_display_ready(void);

// Updates the internal status data. The display task will render it asynchronously.
void cellar_display_update(const cellar_display_status_t *status);

// Deprecated: Alias for update
#define cellar_display_show(s) cellar_display_update(s)
