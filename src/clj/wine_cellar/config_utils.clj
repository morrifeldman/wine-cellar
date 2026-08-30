(ns wine-cellar.config-utils
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [mount.core :refer [defstate]]))

(defstate backend-port
          :start
          (if-let [port-str (System/getenv "PORT")]
            (Integer/parseInt port-str)
            3000))

(defstate production? :start (= (System/getenv "CLOJURE_ENV") "production"))

(defn frontend-url
  "Determine the correct frontend URL based on the request context"
  ([request]
   (if production?
     "/"
     (let [host (get-in request [:headers "host"])
           is-ngrok? (and host (.contains host "ngrok"))]
       (if is-ngrok?
         ;; For ngrok, frontend is served from the same host
         (str "https://" host)
         ;; For localhost, frontend is on port 8080
         "http://localhost:8080"))))
  ([]
   ;; Fallback when no request context
   (if production? "/" "http://localhost:8080")))

(defstate cors-origins
          :start
          (if production?
            ["/"]
            ;; Only need CORS for frontend dev server on localhost:8080
            [#"http://localhost:8080"]))

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

(defn env-value
  "Value of an environment variable, treating blank as unset."
  [env-var-name]
  (let [raw (System/getenv env-var-name)] (when-not (string/blank? raw) raw)))

(defn get-config
  "Gets a configuration value from environment variables or pass.
   In production, only uses environment variables.
   In development, tries environment variables first, then falls back to pass.
   
   Parameters:
   - env-var-name: The name of the environment variable
   - pass-path: Optional custom pass path (defaults to wine-cellar/xxx pattern)
   
   Returns the configuration value or throws an exception if not found."
  [env-var-name & {:keys [pass-path fallback]}]
  (try (let [env-value (env-value env-var-name)]
         (when (and production? (nil? env-value) (nil? fallback))
           (throw (ex-info
                   (str "Missing required environment variable in production: "
                        env-var-name)
                   {:type :config-retrieval-error :env-var env-var-name})))
         (cond env-value env-value
               production? fallback
               :else (or (get-password-from-pass
                          (or pass-path (env-var-to-pass-path env-var-name)))
                         fallback)))
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

;; AI model defaults live in resources/ai-models.edn so there is one place to
;; change them. Environment variables still win at runtime.
(def ai-models-file "ai-models.edn")

(defstate ai-models
          :start
          (edn/read-string (slurp (or (io/resource ai-models-file)
                                      (io/file (str "resources/"
                                                    ai-models-file))))))

(defn model-env-var
  "Environment variable name for a provider/key pair,
   e.g. :anthropic + :small-model -> \"ANTHROPIC_SMALL_MODEL\""
  [provider k]
  (-> (str (name provider) "-" (name k))
      (string/replace "-" "_")
      string/upper-case))

(defn ai-model
  "Model id for a provider, e.g. (ai-model :anthropic :small-model).
   The matching env var (ANTHROPIC_SMALL_MODEL) wins when set, otherwise the
   value from resources/ai-models.edn is used."
  [provider k]
  (or (env-value (model-env-var provider k)) (get-in ai-models [provider k])))

(defn ai-default-provider
  "Default AI provider keyword. AI_DEFAULT_PROVIDER wins when set, otherwise
   :default-provider from resources/ai-models.edn is used."
  []
  (keyword (or (env-value "AI_DEFAULT_PROVIDER")
               (:default-provider ai-models))))
