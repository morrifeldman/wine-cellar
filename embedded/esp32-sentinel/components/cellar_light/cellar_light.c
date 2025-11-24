#include "cellar_light.h"

#include <math.h>
#include <stdint.h>
#include <stdio.h>

#include "config.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_err.h"
#include "esp_log.h"

static const char *TAG = "cellar_light";

#ifndef GA1A12S202_ADC_UNIT
#define GA1A12S202_ADC_UNIT ADC_UNIT_1
#endif
#ifndef GA1A12S202_ADC_CHANNEL
#define GA1A12S202_ADC_CHANNEL ADC_CHANNEL_6  // GPIO34 on ESP32
#endif
#ifndef GA1A12S202_ATTEN
#define GA1A12S202_ATTEN ADC_ATTEN_DB_12     // 0-3.3V range (ESP-IDF 5.5 preferred)
#endif
#ifndef GA1A12S202_SUPPLY_MV
#define GA1A12S202_SUPPLY_MV 3300
#endif
#ifndef GA1A12S202_AVG_SAMPLES
#define GA1A12S202_AVG_SAMPLES 8
#endif

static adc_oneshot_unit_handle_t s_adc_handle = NULL;
static adc_cali_handle_t s_adc_cali = NULL;
static bool s_ready = false;

esp_err_t cellar_light_init(void) {
    if (s_ready) return ESP_OK;

    adc_oneshot_unit_init_cfg_t unit_cfg = {
        .unit_id = GA1A12S202_ADC_UNIT,
    };
    esp_err_t err = adc_oneshot_new_unit(&unit_cfg, &s_adc_handle);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "ADC init failed: %s", esp_err_to_name(err));
        return err;
    }

    adc_oneshot_chan_cfg_t chan_cfg = {
        .bitwidth = ADC_BITWIDTH_DEFAULT,
        .atten = GA1A12S202_ATTEN,
    };
    err = adc_oneshot_config_channel(s_adc_handle, GA1A12S202_ADC_CHANNEL, &chan_cfg);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "ADC channel cfg failed: %s", esp_err_to_name(err));
        return err;
    }

    adc_cali_line_fitting_config_t cal_cfg = {
        .unit_id = GA1A12S202_ADC_UNIT,
        .atten = GA1A12S202_ATTEN,
        .bitwidth = ADC_BITWIDTH_DEFAULT,
    };
    if (adc_cali_create_scheme_line_fitting(&cal_cfg, &s_adc_cali) == ESP_OK) {
        ESP_LOGI(TAG, "ADC calibration ready (line fitting)");
    } else {
        s_adc_cali = NULL;
        ESP_LOGW(TAG, "ADC calibration not available; using raw scaling");
    }

    s_ready = true;
    ESP_LOGI(TAG,
             "GA1A12S202 wired to ADC unit %d channel %d atten %d, supply %dmV",
             GA1A12S202_ADC_UNIT,
             GA1A12S202_ADC_CHANNEL,
             GA1A12S202_ATTEN,
             GA1A12S202_SUPPLY_MV);
    return ESP_OK;
}

bool cellar_light_ready(void) { return s_ready; }

esp_err_t cellar_light_read(float *lux_out, int *millivolts_out) {
    if (!s_ready || !s_adc_handle) {
        return ESP_ERR_INVALID_STATE;
    }

    int raw_sum = 0;
    for (int i = 0; i < GA1A12S202_AVG_SAMPLES; ++i) {
        int raw = 0;
        esp_err_t err = adc_oneshot_read(s_adc_handle, GA1A12S202_ADC_CHANNEL, &raw);
        if (err != ESP_OK) {
            return err;
        }
        raw_sum += raw;
    }

    int raw_avg = raw_sum / GA1A12S202_AVG_SAMPLES;
    int mv = -1;
    if (s_adc_cali) {
        ESP_ERROR_CHECK_WITHOUT_ABORT(adc_cali_raw_to_voltage(s_adc_cali, raw_avg, &mv));
    }
    if (mv < 0) {
        mv = (int)lroundf(((float)raw_avg / 4095.0f) * (float)GA1A12S202_SUPPLY_MV);
    }

    if (millivolts_out) {
        *millivolts_out = mv;
    }
    if (lux_out) {
        float log_lux = ((float)mv / (float)GA1A12S202_SUPPLY_MV) * 5.0f;  // 0..5 decades
        if (log_lux < 0.0f) log_lux = 0.0f;
        if (log_lux > 5.0f) log_lux = 5.0f;
        *lux_out = powf(10.0f, log_lux);
    }
    return ESP_OK;
}
