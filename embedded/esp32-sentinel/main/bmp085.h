#pragma once

#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

esp_err_t bmp085_init(void);
esp_err_t bmp085_read(float *temperature_c, float *pressure_hpa);

#ifdef __cplusplus
}
#endif
