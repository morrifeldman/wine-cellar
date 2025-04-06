(ns wine-cellar.auth.config
  (:require [jsonista.core :as json]
            [wine-cellar.config-utils :as config-utils]))

(defn get-google-credentials
  "Retrieves Google OAuth credentials from pass command line program"
  []
  (try
    (let [result (config-utils/get-password-from-pass
                  "wine-cellar/google-oath-json")
          web-credentials (-> result
                              (json/read-value
                               json/keyword-keys-object-mapper)
                              :web)]
      {:client-id (:client_id web-credentials)
       :client-secret (:client_secret web-credentials)
       :redirect-uris (:redirect_uris web-credentials)
       :javascript-origins (:javascript_origins web-credentials)})
    (catch Exception e
      (println "Error retrieving Google credentials from pass:"
               (.getMessage e))
      nil)))

(defn get-oauth-config
  "Returns the OAuth configuration, either from environment variables or pass"
  []
  (let [{:keys [client-id client-secret redirect-uris javascript-origins]}
        (get-google-credentials)]
    {:client-id (or (System/getenv "GOOGLE_CLIENT_ID")
                    client-id)
     :client-secret (or (System/getenv "GOOGLE_CLIENT_SECRET")
                        client-secret)
     :redirect-uri (or (System/getenv "OAUTH_REDIRECT_URI")
                       (first redirect-uris))}))

(defn get-jwt-secret
  "Gets the JWT secret for signing tokens, either from environment or a default"
  []
  (or (System/getenv "JWT_SECRET")
      ;; In production, you should always set JWT_SECRET environment variable
      ;; This default is only for development
      "default-jwt-secret-please-change-in-production"))
