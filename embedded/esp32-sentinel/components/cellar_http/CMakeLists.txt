idf_component_register(
    SRCS "cellar_http.c"
    INCLUDE_DIRS "include"
    REQUIRES esp_http_client
    PRIV_REQUIRES main
)
