#pragma once

#include <stdbool.h>
#include <stddef.h>

#include "esp_err.h"

typedef struct {
    char *buf;
    size_t len;
    size_t cap;
} resp_accum_t;

typedef struct {
    float temperature_c;
    float pressure_hpa;
    float illuminance_lux;
    const char *timestamp_iso8601;  // optional
    const char *device_id;          // optional, falls back to DEVICE_ID macro
} cellar_measurement_t;

typedef struct {
    int status_code;  // HTTP response status or -1 if request failed
    esp_err_t err;    // esp_err_t from esp_http_client_perform
} cellar_http_result_t;

// POST the given measurement JSON to CELLAR_API_URL.
// Skips NaN fields; returns ESP_FAIL if no measurement fields are present.
esp_err_t cellar_http_post(const cellar_measurement_t *measurement, cellar_http_result_t *result_out);
