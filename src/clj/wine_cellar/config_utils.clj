(ns wine-cellar.config-utils
  (:require [clojure.java.shell :as sh]
            [clojure.string :as string]
            [mount.core :refer [defstate]]))

(defstate production? :start (= (System/getenv "CLOJURE_ENV") "production"))

(defn get-password-from-pass
  "Retrieves a password from the pass command line program"
  [password-path]
  (let [result (sh/sh "pass" password-path)]
    (if (= 0 (:exit result))
      (string/trim (:out result))
      (throw (ex-info (str "Failed to retrieve password from pass: "
                           (:err result))
                      {:type :password-retrieval-error :path password-path})))))

(defn env-var-to-pass-path
  "Converts an environment variable name to a pass path"
  [env-var-name]
  (str "wine-cellar/"
       (-> env-var-name
           string/lower-case
           (string/replace "_" "-"))))

(defn get-config
  "Gets a configuration value from environment variables or pass.
   In production, only uses environment variables.
   In development, tries environment variables first, then falls back to pass.
   
   Parameters:
   - env-var-name: The name of the environment variable
   - pass-path: Optional custom pass path (defaults to wine-cellar/xxx pattern)
   
   Returns the configuration value or throws an exception if not found."
  ([env-var-name] (get-config env-var-name (env-var-to-pass-path env-var-name)))
  ([env-var-name pass-path]
   (try (let [env-value (System/getenv env-var-name)]
          (tap> ["get-config production?" production?])
          (if production?
            ;; In production, only use env vars
            (if env-value
              env-value
              (throw (ex-info
                      (str
                       "Missing required environment variable in production: "
                       env-var-name)
                      {:type :config-retrieval-error :env-var env-var-name})))
            ;; In development, try env var first, then fall back to pass
            (or env-value (get-password-from-pass pass-path))))
        (catch Exception e
          (if (instance? clojure.lang.ExceptionInfo e)
            (throw e) ;; Re-throw our custom exceptions
            (throw (ex-info (str "Failed to retrieve configuration for "
                                 env-var-name)
                            {:type :config-retrieval-error
                             :env-var env-var-name
                             :pass-path pass-path
                             :cause (.getMessage e)}
                            e)))))))
