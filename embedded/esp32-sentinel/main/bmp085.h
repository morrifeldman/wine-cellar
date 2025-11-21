#pragma once

#include "driver/i2c_master.h"
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

esp_err_t bmp085_init(i2c_master_bus_handle_t bus_handle);
esp_err_t bmp085_read(float *temperature_c, float *pressure_hpa);

#ifdef __cplusplus
}
#endif
