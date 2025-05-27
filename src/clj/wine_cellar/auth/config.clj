(ns wine-cellar.auth.config
  (:require [wine-cellar.config-utils :as config-utils]))

(defn get-oauth-config
  "Returns the OAuth configuration, either from environment variables or pass"
  []
  {:client-id (config-utils/get-config "GOOGLE_CLIENT_ID")
   :client-secret (config-utils/get-config "GOOGLE_CLIENT_SECRET")
   :redirect-uri (config-utils/get-config "OAUTH_REDIRECT_URI"
                                          {:fallback
                                           (str "http://localhost:"
                                                config-utils/backend-port
                                                "/auth/google/callback")})})

(defn get-jwt-secret
  "Gets the JWT secret for signing tokens, either from environment or a default"
  []
  (config-utils/get-config "JWT_SECRET"))

(defn get-cookie-store-key [] (config-utils/get-config "COOKIE_STORE_KEY"))

(defn get-admin-email
  "Gets the admin email address from environment or pass"
  []
  (config-utils/get-config "ADMIN_EMAIL"))
