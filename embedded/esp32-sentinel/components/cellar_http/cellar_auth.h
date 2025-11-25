#pragma once

#include "esp_err.h"

// Initialize NVS-backed token storage (call after nvs_flash_init and Wi-Fi up).
void cellar_auth_init(void);

// Ensure a valid access token is available. Will attempt refresh first, then
// claim/poll using CLAIM_CODE if missing/expired. Returns ESP_OK on success.
esp_err_t cellar_auth_ensure_access_token(void);

// Get the currently cached access token (NULL if unavailable).
const char *cellar_auth_access_token(void);

// Return the claim code being used (from config or generated/stored).
const char *cellar_auth_claim_code(void);

// Clear stored tokens (forces re-claim on next ensure).
void cellar_auth_clear(void);

// Clear stored claim code (forces regenerate or reuse CLAIM_CODE).
void cellar_auth_clear_claim_code(void);

// Debug helper to log current token expiry.
void cellar_auth_log_status(void);
