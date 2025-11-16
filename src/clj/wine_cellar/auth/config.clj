(ns wine-cellar.auth.config
  (:require [wine-cellar.config-utils :as config-utils]))

(defn get-oauth-config
  "Returns the OAuth configuration, either from environment variables or pass"
  ([] (get-oauth-config nil))
  ([request]
   (let [;; Dynamically determine redirect URI based on request
         redirect-uri
         (if request
           ;; Extract the base URL from the request
           (let [scheme (if (= "https"
                               (get-in request [:headers "x-forwarded-proto"]))
                          "https"
                          (name (:scheme request)))
                 host (get-in request [:headers "host"])]
             ;; Use the host header which already includes port if needed
             (str scheme "://" host "/auth/google/callback"))
           ;; Fallback when no request context
           (str "http://localhost:"
                config-utils/backend-port
                "/auth/google/callback"))]
     {:client-id (config-utils/get-config "GOOGLE_CLIENT_ID")
      :client-secret (config-utils/get-config "GOOGLE_CLIENT_SECRET")
      :redirect-uri redirect-uri})))

(defn get-jwt-secret
  "Gets the JWT secret for signing tokens, either from environment or a default"
  []
  (config-utils/get-config "JWT_SECRET"))

(defn get-cookie-store-key [] (config-utils/get-config "COOKIE_STORE_KEY"))

(defn get-admin-email
  "Gets the admin email address from environment or pass"
  []
  (config-utils/get-config "ADMIN_EMAIL"))
