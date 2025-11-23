#include "bmp085.h"

#include <math.h>
#include <stdbool.h>

#include "config.h"
#include "driver/i2c_master.h"
#include "esp_check.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#ifndef BMP085_I2C_PORT
#define BMP085_I2C_PORT I2C_NUM_0
#endif

#ifndef BMP085_I2C_SDA
#define BMP085_I2C_SDA I2C_SDA
#endif

#ifndef BMP085_I2C_SCL
#define BMP085_I2C_SCL I2C_SCL
#endif

#ifndef BMP085_I2C_FREQ_HZ
#define BMP085_I2C_FREQ_HZ I2C_FREQ_HZ
#endif

#ifndef BMP085_ADDRESS
#define BMP085_ADDRESS 0x77
#endif

#define REG_CALIB_START 0xAA
#define REG_CONTROL 0xF4
#define REG_DATA_MSB 0xF6
#define REG_CHIP_ID 0xD0
#define CMD_TEMP 0x2E
#define CMD_PRESSURE (0x34 + (BMP085_OSS << 6))

#ifndef BMP085_OSS
#define BMP085_OSS 0  // Oversampling setting (0..3)
#endif

static const char *TAG = "bmp085";

static bool s_initialized = false;
typedef struct {
    int16_t AC1;
    int16_t AC2;
    int16_t AC3;
    uint16_t AC4;
    uint16_t AC5;
    uint16_t AC6;
    int16_t B1;
    int16_t B2;
    int16_t MB;
    int16_t MC;
    int16_t MD;
} bmp085_calibration_t;

static bmp085_calibration_t s_calibration;

static const int I2C_TIMEOUT_MS = 100;
static i2c_master_bus_handle_t s_bus = NULL;
static i2c_master_dev_handle_t s_dev = NULL;

static esp_err_t i2c_write_byte(uint8_t reg, uint8_t value) {
    if (!s_dev) return ESP_ERR_INVALID_STATE;
    uint8_t data[2] = {reg, value};
    return i2c_master_transmit(s_dev, data, sizeof(data), I2C_TIMEOUT_MS);
}

static esp_err_t i2c_read_bytes(uint8_t reg, uint8_t *buf, size_t len) {
    if (!s_dev) return ESP_ERR_INVALID_STATE;
    return i2c_master_transmit_receive(s_dev, &reg, 1, buf, len, I2C_TIMEOUT_MS);
}

static esp_err_t read_calibration(bmp085_calibration_t *cal) {
    uint8_t data[22];
    ESP_RETURN_ON_ERROR(i2c_read_bytes(REG_CALIB_START, data, sizeof(data)), TAG, "Calib read fail");
    cal->AC1 = (int16_t)((data[0] << 8) | data[1]);
    cal->AC2 = (int16_t)((data[2] << 8) | data[3]);
    cal->AC3 = (int16_t)((data[4] << 8) | data[5]);
    cal->AC4 = (uint16_t)((data[6] << 8) | data[7]);
    cal->AC5 = (uint16_t)((data[8] << 8) | data[9]);
    cal->AC6 = (uint16_t)((data[10] << 8) | data[11]);
    cal->B1 = (int16_t)((data[12] << 8) | data[13]);
    cal->B2 = (int16_t)((data[14] << 8) | data[15]);
    cal->MB = (int16_t)((data[16] << 8) | data[17]);
    cal->MC = (int16_t)((data[18] << 8) | data[19]);
    cal->MD = (int16_t)((data[20] << 8) | data[21]);
    return ESP_OK;
}

static esp_err_t compensate_readings(const bmp085_calibration_t *cal, int32_t ut, int32_t up, int oversampling,
                                     float *temperature_c, float *pressure_hpa) {
    if (!cal) return ESP_ERR_INVALID_ARG;

    int32_t X1 = ((ut - (int32_t)cal->AC6) * (int32_t)cal->AC5) >> 15;
    int32_t X2 = ((int32_t)cal->MC << 11) / (X1 + cal->MD);
    int32_t B5 = X1 + X2;
    int32_t temp_tenths = (B5 + 8) >> 4;

    if (temperature_c) {
        *temperature_c = temp_tenths / 10.0f;
    }

    int32_t B6 = B5 - 4000;
    X1 = ((int32_t)cal->B2 * ((B6 * B6) >> 12)) >> 11;
    X2 = ((int32_t)cal->AC2 * B6) >> 11;
    int32_t X3 = X1 + X2;
    int32_t B3 = ((((int32_t)cal->AC1 * 4 + X3) << oversampling) + 2) >> 2;
    X1 = ((int32_t)cal->AC3 * B6) >> 13;
    X2 = ((int32_t)cal->B1 * ((B6 * B6) >> 12)) >> 16;
    X3 = ((X1 + X2) + 2) >> 2;
    uint32_t B4 = (uint32_t)cal->AC4 * (uint32_t)(X3 + 32768) >> 15;
    uint32_t B7 = ((uint32_t)up - B3) * (50000U >> oversampling);

    int32_t p;
    if (B7 < 0x80000000U) {
        p = (B7 << 1) / B4;
    } else {
        p = (B7 / B4) << 1;
    }
    X1 = (p >> 8) * (p >> 8);
    X1 = (X1 * 3038) >> 16;
    X2 = (-7357 * p) >> 16;
    p = p + ((X1 + X2 + 3791) >> 4);

    if (pressure_hpa) {
        *pressure_hpa = p / 100.0f;
    }

    return ESP_OK;
}

static void run_datasheet_self_test(void) {
    const bmp085_calibration_t cal = {
        .AC1 = 408,
        .AC2 = -72,
        .AC3 = -14383,
        .AC4 = 32741,
        .AC5 = 32757,
        .AC6 = 23153,
        .B1 = 6190,
        .B2 = 4,
        .MB = -32768,
        .MC = -8711,
        .MD = 2868,
    };
    const int32_t ut = 27898;
    const int32_t up = 23843;
    float temp_c = NAN;
    float pressure_hpa = NAN;
    compensate_readings(&cal, ut, up, 0, &temp_c, &pressure_hpa);

    bool temp_ok = fabsf(temp_c - 15.0f) < 0.1f;
    bool pressure_ok = fabsf(pressure_hpa - 699.64f) < 0.1f;
    if (temp_ok && pressure_ok) {
        ESP_LOGI(TAG, "Datasheet self-test PASS: T=%.2fC P=%.2fhPa", temp_c, pressure_hpa);
    } else {
        ESP_LOGE(TAG, "Datasheet self-test FAIL: T=%.2fC (expect 15.0C) P=%.2fhPa (expect 699.64hPa)",
                 temp_c, pressure_hpa);
    }
}

esp_err_t bmp085_init(i2c_master_bus_handle_t bus_handle) {
    if (s_initialized) {
        return ESP_OK;
    }

    if (!bus_handle) {
        return ESP_ERR_INVALID_STATE;
    }

    s_bus = bus_handle;
    i2c_device_config_t dev_cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = BMP085_ADDRESS,
        .scl_speed_hz = BMP085_I2C_FREQ_HZ,
    };
    ESP_RETURN_ON_ERROR(i2c_master_bus_add_device(s_bus, &dev_cfg, &s_dev), TAG, "Add bmp085 device");

    uint8_t chip_id = 0;
    esp_err_t id_err = i2c_read_bytes(REG_CHIP_ID, &chip_id, 1);
    if (id_err == ESP_OK) {
        ESP_LOGI(TAG, "Chip ID=0x%02X (expect 0x55 for BMP085/BMP180)", chip_id);
    } else {
        ESP_LOGW(TAG, "Chip ID read failed: %s", esp_err_to_name(id_err));
    }

    // I2C bus is initialized in app_main; just read calibration once.
    ESP_RETURN_ON_ERROR(read_calibration(&s_calibration), TAG, "Calibration read failed");
    s_initialized = true;
    ESP_LOGI(TAG,
             "Calibration loaded AC1=%d AC2=%d AC3=%d AC4=%u AC5=%u AC6=%u B1=%d B2=%d MB=%d MC=%d MD=%d",
             s_calibration.AC1,
             s_calibration.AC2,
             s_calibration.AC3,
             s_calibration.AC4,
             s_calibration.AC5,
             s_calibration.AC6,
             s_calibration.B1,
             s_calibration.B2,
             s_calibration.MB,
             s_calibration.MC,
             s_calibration.MD);

    // Quick correctness check of compensation math against datasheet example values.
    run_datasheet_self_test();
    return ESP_OK;
}

static esp_err_t read_uncompensated_temperature(int32_t *ut) {
    ESP_RETURN_ON_ERROR(i2c_write_byte(REG_CONTROL, CMD_TEMP), TAG, "CMD temp fail");
    vTaskDelay(pdMS_TO_TICKS(5) ?: 1); // Datasheet value is 4.5 ms; clamp to at least 1 tick
    uint8_t data[2];
    ESP_RETURN_ON_ERROR(i2c_read_bytes(REG_DATA_MSB, data, 2), TAG, "Temp read fail");
    int32_t ut_raw = (data[0] << 8) | data[1];
    *ut = ut_raw;
    ESP_LOGI(TAG, "Temp raw bytes: 0x%02X 0x%02X (UT=%ld)", data[0], data[1], (long)ut_raw);
    return ESP_OK;
}

static esp_err_t read_uncompensated_pressure(int32_t *up) {
    ESP_RETURN_ON_ERROR(i2c_write_byte(REG_CONTROL, CMD_PRESSURE), TAG, "CMD pressure fail");
    switch (BMP085_OSS) {
        case 0: vTaskDelay(pdMS_TO_TICKS(5) ?: 1); break;  // Datasheet value is 4.5 ms; clamp to 1 tick
        case 1: vTaskDelay(pdMS_TO_TICKS(8) ?: 1); break;  // Datasheet value is 7.5 ms
        case 2: vTaskDelay(pdMS_TO_TICKS(14) ?: 1); break; // Datasheet value is 13.5 ms
        default: vTaskDelay(pdMS_TO_TICKS(26) ?: 1); break; // Datasheet value is 25.5 ms
    }
    uint8_t data[3];
    ESP_RETURN_ON_ERROR(i2c_read_bytes(REG_DATA_MSB, data, 3), TAG, "Pressure read fail");
    int32_t up_raw = (((int32_t)data[0] << 16) | ((int32_t)data[1] << 8) | data[2]) >> (8 - BMP085_OSS);
    *up = up_raw;
    ESP_LOGI(TAG, "Pressure raw bytes: 0x%02X 0x%02X 0x%02X (UP=%ld)", data[0], data[1], data[2],
             (long)up_raw);
    return ESP_OK;
}

esp_err_t bmp085_read(float *temperature_c, float *pressure_hpa) {
    ESP_RETURN_ON_ERROR(bmp085_init(s_bus), TAG, "Not initialized");

    int32_t ut = 0;
    int32_t up = 0;
    ESP_RETURN_ON_ERROR(read_uncompensated_temperature(&ut), TAG, "UT fail");
    ESP_RETURN_ON_ERROR(read_uncompensated_pressure(&up), TAG, "UP fail");

    ESP_LOGI(TAG, "Raw UT=%ld UP=%ld", (long)ut, (long)up);

    float temp_c = NAN, press_hpa = NAN;
    ESP_RETURN_ON_ERROR(compensate_readings(&s_calibration, ut, up, BMP085_OSS, &temp_c, &press_hpa), TAG,
                        "Compensation failed");

    ESP_LOGI(TAG, "Compensated T=%.2fC P=%.2fhPa", temp_c, press_hpa);

    if (temperature_c) *temperature_c = temp_c;
    if (pressure_hpa) *pressure_hpa = press_hpa;
    return ESP_OK;
}
