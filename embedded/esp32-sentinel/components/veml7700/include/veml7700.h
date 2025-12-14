#pragma once

#include "esp_err.h"
#include "driver/i2c_master.h"

#ifdef __cplusplus
extern "C" {
#endif

#define VEML7700_I2C_ADDR_DEFAULT 0x10

typedef struct {
    i2c_master_dev_handle_t i2c_dev;
    float resolution; // Lux per bit, depends on gain/integration time
} veml7700_handle_t;

/**
 * @brief Initialize VEML7700 sensor
 * 
 * @param bus_handle Handle to the I2C master bus
 * @param address I2C address (usually 0x10)
 * @param out_handle Pointer to store the sensor handle
 * @return esp_err_t ESP_OK on success
 */
esp_err_t veml7700_init(i2c_master_bus_handle_t bus_handle, uint8_t address, veml7700_handle_t *out_handle);

/**
 * @brief Read lux value from VEML7700
 * 
 * @param handle Sensor handle
 * @param lux Pointer to store the lux value
 * @return esp_err_t ESP_OK on success
 */
esp_err_t veml7700_read_lux(veml7700_handle_t *handle, float *lux);

#ifdef __cplusplus
}
#endif
