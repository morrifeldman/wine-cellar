#pragma once

#include <stdbool.h>

#include "driver/i2c_master.h"
#include "esp_err.h"

typedef struct {
    float temperature_c;
    float pressure_hpa;
    float illuminance_lux;
    int http_status;        // HTTP status code returned by the API (or -1 on failure)
    esp_err_t post_err;     // esp_err_t from the POST attempt
    const char *ip_address; // dotted-quad string (optional)
    const char *status_line; // optional status message (e.g., "Waiting for approval")
} cellar_display_status_t;

// Initialize the SSD1306 display on the provided I2C bus. Safe to call once.
esp_err_t cellar_display_init(i2c_master_bus_handle_t bus);

// Returns true if the display was successfully initialized.
bool cellar_display_ready(void);

// Render the provided status on the display. Safe to call even if init failed.
void cellar_display_show(const cellar_display_status_t *status);
