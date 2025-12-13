idf_component_register(
    SRCS "main.c" "bmp280.c"
    INCLUDE_DIRS "."
    REQUIRES nvs_flash esp_wifi esp_event esp_netif esp_http_client cellar_display cellar_http spi_flash
    EMBED_TXTFILES "server_root_cert.pem"
)
