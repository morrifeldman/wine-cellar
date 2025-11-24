idf_component_register(
    SRCS "cellar_light.c"
    INCLUDE_DIRS "include"
    REQUIRES esp_adc
    PRIV_REQUIRES main
)
