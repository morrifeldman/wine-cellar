idf_component_register(
    SRCS "cellar_display.c"
    INCLUDE_DIRS "include"
    REQUIRES esp_lcd esp_driver_i2c
    PRIV_REQUIRES main
)
