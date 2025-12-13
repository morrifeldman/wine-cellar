#include "bmp280.h"

#include <string.h>
#include "esp_log.h"
#include "esp_check.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "bmp280";

// Registers
#define REG_DIG_T1    0x88
#define REG_CHIP_ID   0xD0
#define REG_RESET     0xE0
#define REG_STATUS    0xF3
#define REG_CTRL_MEAS 0xF4
#define REG_CONFIG    0xF5
#define REG_PRESS_MSB 0xF7

// Constants
#define CHIP_ID_BMP280 0x58
#define RESET_CMD      0xB6
#define I2C_TIMEOUT_MS 100

static esp_err_t write_byte(bmp280_handle_t *dev, uint8_t reg, uint8_t data) {
    uint8_t buffer[2] = {reg, data};
    return i2c_master_transmit(dev->i2c_dev, buffer, sizeof(buffer), I2C_TIMEOUT_MS);
}

static esp_err_t read_bytes(bmp280_handle_t *dev, uint8_t reg, uint8_t *data, size_t len) {
    return i2c_master_transmit_receive(dev->i2c_dev, &reg, 1, data, len, I2C_TIMEOUT_MS);
}

esp_err_t bmp280_init(i2c_master_bus_handle_t bus_handle, uint8_t address, bmp280_handle_t *out_handle) {
    i2c_device_config_t dev_cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = address,
        .scl_speed_hz = 100000, // Standard mode
    };

    ESP_RETURN_ON_ERROR(i2c_master_bus_add_device(bus_handle, &dev_cfg, &out_handle->i2c_dev), TAG, "I2C add device failed");

    // Check Chip ID
    uint8_t chip_id = 0;
    ESP_RETURN_ON_ERROR(read_bytes(out_handle, REG_CHIP_ID, &chip_id, 1), TAG, "Read Chip ID failed");
    if (chip_id != CHIP_ID_BMP280) {
        ESP_LOGE(TAG, "Invalid Chip ID: 0x%02X (Expected 0x%02X)", chip_id, CHIP_ID_BMP280);
        // Continue anyway? Or fail? Let's warn but continue for now in case of clones.
        // return ESP_ERR_INVALID_STATE;
    } else {
        ESP_LOGI(TAG, "Found BMP280 (ID: 0x58)");
    }

    // Reset
    write_byte(out_handle, REG_RESET, RESET_CMD);
    vTaskDelay(pdMS_TO_TICKS(10));

    // Read Calibration
    uint8_t cal_data[24];
    ESP_RETURN_ON_ERROR(read_bytes(out_handle, REG_DIG_T1, cal_data, 24), TAG, "Read calibration failed");

    out_handle->dig_T1 = (cal_data[1] << 8) | cal_data[0];
    out_handle->dig_T2 = (int16_t)((cal_data[3] << 8) | cal_data[2]);
    out_handle->dig_T3 = (int16_t)((cal_data[5] << 8) | cal_data[4]);
    out_handle->dig_P1 = (cal_data[7] << 8) | cal_data[6];
    out_handle->dig_P2 = (int16_t)((cal_data[9] << 8) | cal_data[8]);
    out_handle->dig_P3 = (int16_t)((cal_data[11] << 8) | cal_data[10]);
    out_handle->dig_P4 = (int16_t)((cal_data[13] << 8) | cal_data[12]);
    out_handle->dig_P5 = (int16_t)((cal_data[15] << 8) | cal_data[14]);
    out_handle->dig_P6 = (int16_t)((cal_data[17] << 8) | cal_data[16]);
    out_handle->dig_P7 = (int16_t)((cal_data[19] << 8) | cal_data[18]);
    out_handle->dig_P8 = (int16_t)((cal_data[21] << 8) | cal_data[20]);
    out_handle->dig_P9 = (int16_t)((cal_data[23] << 8) | cal_data[22]);

    // Config: Normal mode, Standby 1000ms, Filter 16x
    // Register 0xF5 (CONFIG): t_sb=101 (1000ms), filter=100 (16x), spi3w_en=0
    // Binary: 101 100 00 -> 0xB4
    // However, datasheet says standby 0x05 is 1000ms
    write_byte(out_handle, REG_CONFIG, 0xA0); // Standby 1000ms (101), Filter 4x (010) -> 10101000 = 0xA8?
                                            // Let's use Filter 16x (100) -> 10110000 = 0xB0.
                                            // Let's stick to safe defaults: Filter 4x
                                            // CONFIG: 101 (1000ms) | 010 (Filter 4) | 00 = 0xA8
    write_byte(out_handle, REG_CONFIG, 0xA8);

    // Ctrl Meas: Osrs_T x2, Osrs_P x16, Normal Mode
    // Register 0xF4 (CTRL_MEAS): osrs_t=010, osrs_p=101, mode=11
    // Binary: 010 101 11 -> 0x57
    write_byte(out_handle, REG_CTRL_MEAS, 0x57);

    return ESP_OK;
}

esp_err_t bmp280_read_float(bmp280_handle_t *handle, float *temperature_c, float *pressure_hpa) {
    uint8_t data[6];
    ESP_RETURN_ON_ERROR(read_bytes(handle, REG_PRESS_MSB, data, 6), TAG, "Read data failed");

    int32_t adc_P = (data[0] << 12) | (data[1] << 4) | (data[2] >> 4);
    int32_t adc_T = (data[3] << 12) | (data[4] << 4) | (data[5] >> 4);

    // Compensation Temperature
    double var1, var2, T;
    var1 = (((double)adc_T) / 16384.0 - ((double)handle->dig_T1) / 1024.0) * ((double)handle->dig_T2);
    var2 = ((((double)adc_T) / 131072.0 - ((double)handle->dig_T1) / 8192.0) *
            (((double)adc_T) / 131072.0 - ((double)handle->dig_T1) / 8192.0)) *
           ((double)handle->dig_T3);
    handle->t_fine = (int32_t)(var1 + var2);
    T = (var1 + var2) / 5120.0;
    if (temperature_c) *temperature_c = (float)T;

    // Compensation Pressure
    double p;
    var1 = ((double)handle->t_fine / 2.0) - 64000.0;
    var2 = var1 * var1 * ((double)handle->dig_P6) / 32768.0;
    var2 = var2 + var1 * ((double)handle->dig_P5) * 2.0;
    var2 = (var2 / 4.0) + (((double)handle->dig_P4) * 65536.0);
    var1 = (((double)handle->dig_P3) * var1 * var1 / 524288.0 + ((double)handle->dig_P2) * var1) / 524288.0;
    var1 = (1.0 + var1 / 32768.0) * ((double)handle->dig_P1);

    if (var1 == 0.0) {
        if (pressure_hpa) *pressure_hpa = 0; // Avoid division by zero
        return ESP_OK;
    }

    p = 1048576.0 - (double)adc_P;
    p = (p - (var2 / 4096.0)) * 6250.0 / var1;
    var1 = ((double)handle->dig_P9) * p * p / 2147483648.0;
    var2 = p * ((double)handle->dig_P8) / 32768.0;
    p = p + (var1 + var2 + ((double)handle->dig_P7)) / 16.0;

    if (pressure_hpa) *pressure_hpa = (float)(p / 100.0);

    return ESP_OK;
}
