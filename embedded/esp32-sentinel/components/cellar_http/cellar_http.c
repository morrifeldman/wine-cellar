#include "cellar_http.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

#include "config.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "cellar_auth.h"

#ifndef DEVICE_ID
#define DEVICE_ID "esp32-sentinel"
#endif

#ifndef CELLAR_API_BASE
#error "CELLAR_API_BASE must be defined in config.h (e.g. http://host:3000/api)"
#endif

static const char *POST_URL = CELLAR_API_BASE "/cellar-conditions";

static const char *TAG = "cellar_http";

static esp_err_t http_event_handler(esp_http_client_event_t *evt) {
    if (evt->event_id == HTTP_EVENT_ON_DATA && evt->user_data) {
        resp_accum_t *acc = (resp_accum_t *)evt->user_data;
        if (acc->buf && acc->cap > 1 && evt->data_len > 0) {
            size_t space = (acc->cap - 1) - acc->len;
            size_t to_copy = evt->data_len > space ? space : evt->data_len;
            if (to_copy > 0) {
                memcpy(acc->buf + acc->len, evt->data, to_copy);
                acc->len += to_copy;
                acc->buf[acc->len] = '\0';
            }
        }
    } else if (evt->event_id == HTTP_EVENT_ERROR) {
        ESP_LOGW(TAG, "HTTP event error");
    }
    return ESP_OK;
}

extern const char server_root_cert_pem_start[] asm("_binary_server_root_cert_pem_start");
extern const char server_root_cert_pem_end[]   asm("_binary_server_root_cert_pem_end");

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
        .url = POST_URL,
        .event_handler = http_event_handler,
        .timeout_ms = 8000,
#if CELLAR_API_USE_HTTPS
        .cert_pem = server_root_cert_pem_start,
#endif
    };

    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (!client) {
        ESP_LOGE(TAG, "Failed to init HTTP client");
        return ESP_FAIL;
    }

    esp_http_client_set_method(client, HTTP_METHOD_POST);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    const char *access = cellar_auth_access_token();
    if (!access) {
        ESP_LOGE(TAG, "No access token available");
        esp_http_client_cleanup(client);
        return ESP_FAIL;
    }
    char auth_header[900];
    snprintf(auth_header, sizeof(auth_header), "Bearer %s", access);
    esp_http_client_set_header(client, "Authorization", auth_header);
    esp_http_client_set_post_field(client, payload, written);

    ESP_LOGI(TAG, "POST %s", POST_URL);
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
