#include "cellar_auth.h"

#include <stdio.h>
#include <string.h>
#include <time.h>

#include "config.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "nvs.h"
#include "nvs_flash.h"

typedef struct {
    char *buf;
    size_t len;
    size_t cap;
} resp_accum_t;

static esp_err_t http_event_handler_accum(esp_http_client_event_t *evt) {
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
    }
    return ESP_OK;
}

#ifndef DEVICE_ID
#define DEVICE_ID "esp32-sentinel"
#endif

static const char *TAG = "cellar_auth";
static const char *NVS_NAMESPACE = "cellar";
static const char *KEY_ACCESS = "access";
static const char *KEY_REFRESH = "refresh";
static const char *KEY_EXP = "access_exp";  // epoch seconds
static const char *KEY_CLAIM = "claim_code";

static char s_access_token[768] = {0};
static char s_refresh_token[256] = {0};
static time_t s_access_expiry = 0;
static char s_claim_code[24] = {0};

static void ensure_claim_code(void);

static esp_err_t read_str(nvs_handle_t nvs, const char *key, char *buf, size_t buf_len) {
    size_t len = buf_len;
    esp_err_t err = nvs_get_str(nvs, key, buf, &len);
    if (err == ESP_ERR_NVS_NOT_FOUND) return ESP_OK;
    return err;
}

static void load_tokens(void) {
    nvs_handle_t nvs;
    if (nvs_open(NVS_NAMESPACE, NVS_READONLY, &nvs) != ESP_OK) {
        return;
    }
    read_str(nvs, KEY_ACCESS, s_access_token, sizeof(s_access_token));
    read_str(nvs, KEY_REFRESH, s_refresh_token, sizeof(s_refresh_token));
    int64_t exp = 0;
    nvs_get_i64(nvs, KEY_EXP, &exp);
    s_access_expiry = (time_t)exp;
    read_str(nvs, KEY_CLAIM, s_claim_code, sizeof(s_claim_code));
    nvs_close(nvs);
    // If an older, longer claim code is present, truncate to 8 hex chars.
    size_t len = strlen(s_claim_code);
    if (len > 8) {
        s_claim_code[8] = '\0';
    }
}

static void persist_tokens(void) {
    nvs_handle_t nvs;
    if (nvs_open(NVS_NAMESPACE, NVS_READWRITE, &nvs) != ESP_OK) {
        return;
    }
    nvs_set_str(nvs, KEY_ACCESS, s_access_token);
    nvs_set_str(nvs, KEY_REFRESH, s_refresh_token);
    nvs_set_i64(nvs, KEY_EXP, (int64_t)s_access_expiry);
    if (s_claim_code[0]) {
        nvs_set_str(nvs, KEY_CLAIM, s_claim_code);
    }
    nvs_commit(nvs);
    nvs_close(nvs);
}

void cellar_auth_clear(void) {
    memset(s_access_token, 0, sizeof(s_access_token));
    memset(s_refresh_token, 0, sizeof(s_refresh_token));
    s_access_expiry = 0;
    // keep claim code
    nvs_handle_t nvs;
    if (nvs_open(NVS_NAMESPACE, NVS_READWRITE, &nvs) == ESP_OK) {
        nvs_erase_key(nvs, KEY_ACCESS);
        nvs_erase_key(nvs, KEY_REFRESH);
        nvs_erase_key(nvs, KEY_EXP);
        nvs_commit(nvs);
        nvs_close(nvs);
    }
}

void cellar_auth_clear_claim_code(void) {
    memset(s_claim_code, 0, sizeof(s_claim_code));
    nvs_handle_t nvs;
    if (nvs_open(NVS_NAMESPACE, NVS_READWRITE, &nvs) == ESP_OK) {
        nvs_erase_key(nvs, KEY_CLAIM);
        nvs_commit(nvs);
        nvs_close(nvs);
    }
    ensure_claim_code();
}

void cellar_auth_init(void) { load_tokens(); }

const char *cellar_auth_access_token(void) {
    return (s_access_token[0] != '\0') ? s_access_token : NULL;
}

static void ensure_claim_code(void) {
#ifdef CLAIM_CODE
    if (s_claim_code[0] == '\0') {
        strncpy(s_claim_code, CLAIM_CODE, sizeof(s_claim_code) - 1);
    }
#else
    if (s_claim_code[0] == '\0') {
        uint32_t r = esp_random();
        // 8 hex chars ≈ 32 bits of entropy; good enough for low-risk LAN + rate limits.
        snprintf(s_claim_code, sizeof(s_claim_code), "%08X", (unsigned int)r);
        persist_tokens();
    }
#endif
    // Enforce display length of 8 chars for consistency.
    if (strlen(s_claim_code) > 8) {
        s_claim_code[8] = '\0';
    }
}

const char *cellar_auth_claim_code(void) {
    ensure_claim_code();
    return s_claim_code;
}

void cellar_auth_log_status(void) {
    ESP_LOGI(TAG,
             "access token %s, refresh %s, exp=%ld",
             s_access_token[0] ? "present" : "missing",
             s_refresh_token[0] ? "present" : "missing",
             (long)s_access_expiry);
}

static bool access_valid(void) {
    if (s_access_token[0] == '\0') return false;
    time_t now = 0;
    time(&now);
    // If we don't have an expiry, treat as invalid and reclaim.
    if (s_access_expiry <= 0) return false;
    return now + 60 < s_access_expiry;  // refresh if within 60s of expiry
}

// Very small helper to grab a JSON string value for "key":"value"
static bool json_get_string(const char *body, const char *key, char *out, size_t out_len) {
    const char *found = strstr(body, key);
    if (!found) return false;
    const char *colon = strchr(found, ':');
    if (!colon) return false;
    const char *start = colon + 1;
    while (*start == ' ' || *start == '\t' || *start == '"') start++;
    const char *end = start;
    while (*end && *end != '"' && *end != ',' && *end != '\n' && *end != '\r') end++;
    if (end <= start) return false;
    size_t len = (size_t)(end - start);
    if (len >= out_len) len = out_len - 1;
    memcpy(out, start, len);
    out[len] = '\0';
    return true;
}

static bool json_get_int64(const char *body, const char *key, int64_t *out) {
    const char *found = strstr(body, key);
    if (!found) return false;
    const char *colon = strchr(found, ':');
    if (!colon) return false;
    // skip spaces and quotes
    const char *p = colon + 1;
    while (*p == ' ' || *p == '"' || *p == '\t') p++;
    *out = atoll(p);
    return true;
}

static esp_err_t http_post_json(const char *url, const char *json_body, char *resp_buf, size_t resp_buf_len) {
    resp_accum_t acc = {0};
    acc.buf = resp_buf;
    acc.cap = resp_buf_len;
    if (resp_buf && resp_buf_len > 0) {
        resp_buf[0] = '\0';
    }
    esp_http_client_config_t cfg = {
        .url = url,
        .timeout_ms = 8000,
        .event_handler = http_event_handler_accum,
        .user_data = &acc,
        .buffer_size = resp_buf_len > 0 ? resp_buf_len : 512,
    };
#ifdef CELLAR_API_CERT_PEM
    cfg.cert_pem = (const char *)CELLAR_API_CERT_PEM;
#endif

    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) return ESP_FAIL;

    esp_http_client_set_method(client, HTTP_METHOD_POST);
    esp_http_client_set_header(client, "Content-Type", "application/json");
    esp_http_client_set_header(client, "Accept-Encoding", "identity");
    esp_http_client_set_post_field(client, json_body, strlen(json_body));

    esp_err_t err = esp_http_client_perform(client);
    if (err == ESP_OK) {
        int status = esp_http_client_get_status_code(client);
        int64_t len = esp_http_client_get_content_length(client);
        ESP_LOGI(TAG, "POST %s status=%d len=%lld", url, status, (long long)len);
        ESP_LOGI(TAG, "Read %u bytes body", (unsigned)acc.len);
    } else {
        ESP_LOGE(TAG, "HTTP POST failed to %s: %s", url, esp_err_to_name(err));
    }

    esp_http_client_cleanup(client);
    return err;
}

static esp_err_t refresh_tokens(void) {
    if (s_refresh_token[0] == '\0') return ESP_FAIL;
    char body[320];
    snprintf(body, sizeof(body), "{\"device_id\":\"%s\",\"refresh_token\":\"%s\"}", DEVICE_ID, s_refresh_token);
    char resp[1024];
    esp_err_t err = http_post_json(CELLAR_API_BASE "/device-token", body, resp, sizeof(resp));
    if (err != ESP_OK) return err;

    char access[768] = {0};
    char refresh[256] = {0};
    if (!json_get_string(resp, "access_token", access, sizeof(access))) return ESP_FAIL;
    json_get_string(resp, "refresh_token", refresh, sizeof(refresh));
    // access_expires_at is ISO string; backend also sets exp in JWT, so approximate with 20 min from now if missing.
    time_t now = 0;
    time(&now);
    int64_t exp_guess = now + (20 * 60);
    int64_t exp_from_ms = 0;
    if (json_get_int64(resp, "access_expires_at", &exp_from_ms) && exp_from_ms > 0) {
        s_access_expiry = (time_t)(exp_from_ms / 1000);
    } else {
        s_access_expiry = (time_t)exp_guess;
    }

    strncpy(s_access_token, access, sizeof(s_access_token) - 1);
    if (refresh[0]) {
        strncpy(s_refresh_token, refresh, sizeof(s_refresh_token) - 1);
    }
    persist_tokens();
    return ESP_OK;
}

static esp_err_t claim_and_poll(void) {
    char claim_body[256];
    const char *claim = cellar_auth_claim_code();
    snprintf(claim_body, sizeof(claim_body), "{\"device_id\":\"%s\",\"claim_code\":\"%s\"}", DEVICE_ID, claim);
    char resp[1024];
    esp_err_t err = http_post_json(CELLAR_API_BASE "/device-claim", claim_body, resp, sizeof(resp));
    if (err != ESP_OK) return err;

    for (int attempt = 0; attempt < 10; ++attempt) {
        vTaskDelay(pdMS_TO_TICKS(3000));
        memset(resp, 0, sizeof(resp));
        err = http_post_json(CELLAR_API_BASE "/device-claim/poll", claim_body, resp, sizeof(resp));
        if (err != ESP_OK) continue;

        ESP_LOGI(TAG, "Poll resp attempt %d: %s", attempt, resp);
        char status[32] = {0};
        if (!json_get_string(resp, "status", status, sizeof(status))) continue;
        if (strcmp(status, "pending") == 0) {
            continue;
        }
        if (strcmp(status, "approved") == 0) {
            char access[768] = {0};
            char refresh[256] = {0};
            if (!json_get_string(resp, "access_token", access, sizeof(access))) {
                ESP_LOGW(TAG, "Poll approved but no access_token");
                continue;
            }
            json_get_string(resp, "refresh_token", refresh, sizeof(refresh));
            strncpy(s_access_token, access, sizeof(s_access_token) - 1);
            strncpy(s_refresh_token, refresh, sizeof(s_refresh_token) - 1);
            int64_t exp_ms = 0;
            if (json_get_int64(resp, "access_expires_at", &exp_ms) && exp_ms > 0) {
                s_access_expiry = (time_t)(exp_ms / 1000);
            } else {
                time_t now = 0;
                time(&now);
                s_access_expiry = now + (20 * 60);
            }
            ESP_LOGI(TAG, "Parsed token len=%d refresh_len=%d exp=%ld",
                     (int)strlen(s_access_token),
                     (int)strlen(s_refresh_token),
                     (long)s_access_expiry);
            persist_tokens();
            return ESP_OK;
        }
    }
    return ESP_ERR_TIMEOUT;
}

esp_err_t cellar_auth_ensure_access_token(void) {
    if (access_valid()) return ESP_OK;

    ESP_LOGI(TAG, "Access token missing/expiring; attempting refresh");
    if (refresh_tokens() == ESP_OK && access_valid()) {
        ESP_LOGI(TAG, "Refresh succeeded");
        return ESP_OK;
    }
    ESP_LOGW(TAG, "Refresh failed; attempting claim/poll");
    cellar_auth_clear();
    return claim_and_poll();
}
