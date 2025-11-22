idf_component_register(
    SRCS "main.c" "bmp085.c"
    INCLUDE_DIRS "."
    REQUIRES esp_lcd esp_driver_i2c esp_event esp_netif esp_wifi esp_http_client nvs_flash driver spi_flash
)
