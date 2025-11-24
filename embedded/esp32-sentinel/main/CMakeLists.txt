idf_component_register(
    SRCS "main.c" "bmp085.c"
    INCLUDE_DIRS "."
    REQUIRES cellar_display cellar_http cellar_light esp_driver_i2c esp_event esp_netif esp_wifi nvs_flash driver spi_flash esp_adc
)
