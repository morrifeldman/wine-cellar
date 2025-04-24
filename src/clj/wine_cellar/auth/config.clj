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
  (if config-utils/production?
    {:client-id (config-utils/get-config "GOOGLE_CLIENT_ID")
     :client-secret (config-utils/get-config "GOOGLE_CLIENT_SECRET")
     :redirect-uri (config-utils/get-config "OAUTH_REDIRECT_URI")}
    (let [{:keys [redirect-uris] :as creds} (get-google-credentials-from-pass)]
      (assoc creds :redirect-uri (first redirect-uris)))))

(defn get-jwt-secret
  "Gets the JWT secret for signing tokens, either from environment or a default"
  []
  (config-utils/get-config "JWT_SECRET"))

(defn get-cookie-store-key [] (config-utils/get-config "COOKIE_STORE_KEY"))

(defn get-admin-email
  "Gets the admin email address from environment or pass"
  []
  (config-utils/get-config "ADMIN_EMAIL"))
