#include "opt3001.h"
#include "esp_log.h"
#include <math.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "opt3001";

#define OPT3001_REG_RESULT 0x00
#define OPT3001_REG_CONFIG 0x01
#define OPT3001_REG_MANUFACTURER_ID 0x7E
#define OPT3001_REG_DEVICE_ID 0x7F

// Config: Auto-range (1100), 800ms (1), Continuous (10)
// 1100 1100 0001 0000 = 0xCC10
#define OPT3001_CONFIG_DEFAULT 0xCC10 

static esp_err_t write_register(opt3001_handle_t *handle, uint8_t reg, uint16_t value) {
    uint8_t data[3];
    data[0] = reg;
    data[1] = (value >> 8) & 0xFF; // MSB
    data[2] = value & 0xFF;        // LSB
    return i2c_master_transmit(handle->i2c_dev, data, 3, -1);
}

static esp_err_t read_register(opt3001_handle_t *handle, uint8_t reg, uint16_t *value) {
    uint8_t tx = reg;
    uint8_t rx[2];
    esp_err_t err = i2c_master_transmit_receive(handle->i2c_dev, &tx, 1, rx, 2, -1);
    if (err == ESP_OK) {
        *value = (rx[0] << 8) | rx[1];
    }
    return err;
}

esp_err_t opt3001_init(i2c_master_bus_handle_t bus_handle, uint8_t address, opt3001_handle_t *out_handle) {
    i2c_device_config_t dev_cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = address,
        .scl_speed_hz = 100000,
    };

    esp_err_t err = i2c_master_bus_add_device(bus_handle, &dev_cfg, &out_handle->i2c_dev);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Failed to add I2C device");
        return err;
    }

    // Verify Manufacturer ID
    uint16_t mfg_id = 0;
    err = read_register(out_handle, OPT3001_REG_MANUFACTURER_ID, &mfg_id);
    if (err != ESP_OK) {
         ESP_LOGE(TAG, "Failed to communicate with OPT3001 at 0x%02X", address);
         return err;
    }
    if (mfg_id != 0x5449) { // TI ID
        ESP_LOGE(TAG, "Unexpected Manufacturer ID: 0x%04X (expected 0x5449)", mfg_id);
        return ESP_FAIL;
    }

    // Verify Device ID
    uint16_t dev_id = 0;
    err = read_register(out_handle, OPT3001_REG_DEVICE_ID, &dev_id);
    if (err != ESP_OK) {
         ESP_LOGE(TAG, "Failed to read Device ID from OPT3001");
         return err;
    }
    if (dev_id != 0x3001) { // OPT3001 Device ID
        ESP_LOGE(TAG, "Unexpected Device ID: 0x%04X (expected 0x3001)", dev_id);
        return ESP_FAIL;
    }

    // Configure sensor
    err = write_register(out_handle, OPT3001_REG_CONFIG, OPT3001_CONFIG_DEFAULT);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Failed to configure OPT3001");
        return err;
    }
    
    // Give the sensor time to complete its first conversion (800ms config)
    vTaskDelay(pdMS_TO_TICKS(1000));

    ESP_LOGI(TAG, "OPT3001 initialized at 0x%02X", address);
    return ESP_OK;
}

esp_err_t opt3001_read_lux(opt3001_handle_t *handle, float *lux) {
    uint16_t raw;
    esp_err_t err = read_register(handle, OPT3001_REG_RESULT, &raw);
    if (err != ESP_OK) return err;

    uint16_t exponent = (raw >> 12) & 0x0F;
    uint16_t mantissa = raw & 0x0FFF;

    *lux = 0.01f * powf(2, exponent) * mantissa;
    return ESP_OK;
}
