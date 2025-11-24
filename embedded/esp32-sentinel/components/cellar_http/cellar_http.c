#include "cellar_http.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

#include "config.h"
#include "esp_http_client.h"
#include "esp_log.h"

#ifndef DEVICE_ID
#define DEVICE_ID "esp32-sentinel"
#endif

#ifndef CELLAR_API_URL
#error "CELLAR_API_URL must be defined in config.h"
#endif

#ifndef DEVICE_JWT
#error "DEVICE_JWT must be defined in config.h"
#endif

static const char *TAG = "cellar_http";

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

esp_err_t cellar_http_post(const cellar_measurement_t *measurement, cellar_http_result_t *result_out) {
    if (!measurement) return ESP_ERR_INVALID_ARG;

    if (result_out) {
        result_out->status_code = -1;
        result_out->err = ESP_FAIL;
    }

    const char *device_id = measurement->device_id ? measurement->device_id : DEVICE_ID;
    char payload[320];
    int written = snprintf(payload, sizeof(payload), "{\"device_id\":\"%s\"", device_id);

    if (measurement->timestamp_iso8601 && measurement->timestamp_iso8601[0] != '\0') {
        written += snprintf(payload + written,
                            sizeof(payload) - written,
                            ",\"measured_at\":\"%s\"",
                            measurement->timestamp_iso8601);
    }

    bool has_measurement = false;

    if (!isnan(measurement->temperature_c)) {
        has_measurement = true;
        written += snprintf(payload + written, sizeof(payload) - written, ",\"temperature_c\":%.2f", measurement->temperature_c);
    }
    if (!isnan(measurement->pressure_hpa)) {
        has_measurement = true;
        written += snprintf(payload + written, sizeof(payload) - written, ",\"pressure_hpa\":%.2f", measurement->pressure_hpa);
    }
    if (!isnan(measurement->illuminance_lux)) {
        has_measurement = true;
        written += snprintf(payload + written, sizeof(payload) - written, ",\"illuminance_lux\":%.1f", measurement->illuminance_lux);
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

    if (result_out) {
        result_out->status_code = status;
        result_out->err = err;
    }

    esp_http_client_cleanup(client);
    return err;
}

