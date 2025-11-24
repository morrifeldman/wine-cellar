#pragma once

#include <stdbool.h>

#include "esp_err.h"

// Initialize the GA1A12S202 light sensor ADC channel. Safe to call once.
esp_err_t cellar_light_init(void);

// Returns true when initialized successfully.
bool cellar_light_ready(void);

// Read lux (and optional millivolts). Returns ESP_ERR_INVALID_STATE if not ready.
esp_err_t cellar_light_read(float *lux_out, int *millivolts_out);

