(ns wine-cellar.auth.config
  (:require [jsonista.core :as json]
            [wine-cellar.config-utils :as config-utils]))

(defn get-google-credentials-from-pass
  "Retrieves Google OAuth credentials from pass command line program"
  []
  (try
    (let [result (config-utils/get-password-from-pass
                  "wine-cellar/google-oath-json")
          web-credentials (-> result
                              (json/read-value json/keyword-keys-object-mapper)
                              :web)]
      {:client-id (:client_id web-credentials)
       :client-secret (:client_secret web-credentials)
       :redirect-uris (:redirect_uris web-credentials)
       :javascript-origins (:javascript_origins web-credentials)})
    (catch Exception e
      (println "Error retrieving Google credentials from pass:" (.getMessage e))
      nil)))

(defn get-oauth-config
  "Returns the OAuth configuration, either from environment variables or pass"
  []
  (if (config-utils/production?)
    {:client-id (System/getenv "GOOGLE_CLIENT_ID")
     :client-secret (System/getenv "GOOGLE_CLIENT_SECRET")
     :redirect-uri (System/getenv "OAUTH_REDIRECT_URI")}
    (let [{:keys [redirect-uris] :as creds} (get-google-credentials-from-pass)]
      (assoc creds :redirect-uri (first redirect-uris)))))

(defn get-jwt-secret
  "Gets the JWT secret for signing tokens, either from environment or a default"
  []
  (or (System/getenv "JWT_SECRET")
      (config-utils/get-password-from-pass "wine-cellar/jwt-secret")))

(defn get-cookie-store-key
  []
  (or (System/getenv "COOKIE_STORE_KEY")
      (config-utils/get-password-from-pass "wine-cellar/cookie-store-key")))

(defn get-admin-email
  "Gets the admin email address from environment or pass"
  []
  (or (System/getenv "ADMIN_EMAIL")
      (config-utils/get-password-from-pass "wine-cellar/admin-email")))
