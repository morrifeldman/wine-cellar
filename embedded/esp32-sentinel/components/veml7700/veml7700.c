#include "veml7700.h"
#include "esp_log.h"
#include <math.h>

static const char *TAG = "veml7700";

#define VEML7700_REG_ALS_CONF 0x00
#define VEML7700_REG_ALS      0x04

// Configuration: Gain x1, IT 100ms, Power On
// Gain: Bits 12:11 -> 00 (x1)
// IT:   Bits 9:6   -> 0000 (100ms)
// SD:   Bit 0      -> 0 (On)
#define VEML7700_CONF_DEFAULT 0x0000 
#define VEML7700_RESOLUTION_DEFAULT 0.0576f

static esp_err_t write_register(veml7700_handle_t *handle, uint8_t reg, uint16_t value) {
    uint8_t data[3];
    data[0] = reg;
    data[1] = value & 0xFF;        // LSB
    data[2] = (value >> 8) & 0xFF; // MSB
    return i2c_master_transmit(handle->i2c_dev, data, 3, -1);
}

static esp_err_t read_register(veml7700_handle_t *handle, uint8_t reg, uint16_t *value) {
    uint8_t tx = reg;
    uint8_t rx[2];
    esp_err_t err = i2c_master_transmit_receive(handle->i2c_dev, &tx, 1, rx, 2, -1);
    if (err == ESP_OK) {
        *value = rx[0] | (rx[1] << 8); // LSB first
    }
    return err;
}

esp_err_t veml7700_init(i2c_master_bus_handle_t bus_handle, uint8_t address, veml7700_handle_t *out_handle) {
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

    // Configure sensor
    err = write_register(out_handle, VEML7700_REG_ALS_CONF, VEML7700_CONF_DEFAULT);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Failed to configure VEML7700");
        return err;
    }

    out_handle->resolution = VEML7700_RESOLUTION_DEFAULT;
    
    // Wait for first integration (100ms)
    // Note: Caller usually delays anyway, but we log success.
    ESP_LOGI(TAG, "VEML7700 initialized at 0x%02X", address);
    return ESP_OK;
}

esp_err_t veml7700_read_lux(veml7700_handle_t *handle, float *lux) {
    uint16_t raw;
    esp_err_t err = read_register(handle, VEML7700_REG_ALS, &raw);
    if (err != ESP_OK) return err;

    *lux = (float)raw * handle->resolution;
    
    // Simple overflow check (if raw is max 16-bit)
    if (raw == 0xFFFF) {
        ESP_LOGW(TAG, "Sensor saturation!");
    }
    
    return ESP_OK;
}
