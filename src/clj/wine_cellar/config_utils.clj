(ns wine-cellar.config-utils
  (:require [clojure.java.shell :as sh]
            [clojure.string :as string]
            [mount.core :refer [defstate]]))

(defstate backend-port
          :start
          (if-let [port-str (System/getenv "PORT")]
            (Integer/parseInt port-str)
            3000))

(defstate production? :start (= (System/getenv "CLOJURE_ENV") "production"))

(defstate frontend :start (if production? "/" "http://localhost:8080"))

(defn get-password-from-pass
  "Retrieves a password from the pass command line program"
  [password-path]
  (let [result (sh/sh "pass" password-path)]
    (when (= 0 (:exit result)) (string/trim (:out result)))))

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
  [env-var-name & {:keys [pass-path fallback]}]
  (try (let [env-value (System/getenv env-var-name)]
         (when production?
           (when-not env-value
             (throw (ex-info
                     (str
                      "Missing required environment variable in production: "
                      env-var-name)
                     {:type :config-retrieval-error :env-var env-var-name}))))
         (or env-value
             (get-password-from-pass (or pass-path
                                         (env-var-to-pass-path env-var-name)))
             fallback))
       (catch Exception e
         (if (instance? clojure.lang.ExceptionInfo e)
           (throw e) ;; Re-throw our custom exceptions
           (throw (ex-info (str "Failed to retrieve configuration for "
                                env-var-name)
                           {:type :config-retrieval-error
                            :env-var env-var-name
                            :pass-path pass-path
                            :cause (.getMessage e)}
                           e))))))
