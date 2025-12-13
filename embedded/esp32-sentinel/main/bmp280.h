#pragma once

#include "driver/i2c_master.h"
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

// I2C Addresses
#define BMP280_I2C_ADDRESS_0 0x76 // SDO -> GND
#define BMP280_I2C_ADDRESS_1 0x77 // SDO -> VCC

typedef struct {
    i2c_master_dev_handle_t i2c_dev;
    // Calibration data
    uint16_t dig_T1;
    int16_t  dig_T2;
    int16_t  dig_T3;
    uint16_t dig_P1;
    int16_t  dig_P2;
    int16_t  dig_P3;
    int16_t  dig_P4;
    int16_t  dig_P5;
    int16_t  dig_P6;
    int16_t  dig_P7;
    int16_t  dig_P8;
    int16_t  dig_P9;
    int32_t  t_fine;
} bmp280_handle_t;

/**
 * @brief Initialize the BMP280 sensor.
 * 
 * @param bus_handle The I2C master bus handle.
 * @param address I2C address (BMP280_I2C_ADDRESS_0 or _1).
 * @param[out] out_handle Pointer to the handle structure to be initialized.
 * @return ESP_OK on success.
 */
esp_err_t bmp280_init(i2c_master_bus_handle_t bus_handle, uint8_t address, bmp280_handle_t *out_handle);

/**
 * @brief Read temperature and pressure.
 * 
 * @param handle Pointer to the initialized handle.
 * @param[out] temperature_c Temperature in Celsius.
 * @param[out] pressure_hpa Pressure in hPa.
 * @return ESP_OK on success.
 */
esp_err_t bmp280_read_float(bmp280_handle_t *handle, float *temperature_c, float *pressure_hpa);

#ifdef __cplusplus
}
#endif
