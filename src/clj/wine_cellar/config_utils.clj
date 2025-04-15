(ns wine-cellar.config-utils
  (:require [clojure.java.shell :as sh]
            [clojure.string :as string]))

;; Database connection setup
(defn get-password-from-pass
  [password-path]
  (let [result (sh/sh "pass" password-path)]
    (if (= 0 (:exit result))
      (string/trim (:out result))
      (throw (ex-info (str "Failed to retrieve password from pass: "
                           (:err result))
                      {:type :password-retrieval-error :path password-path})))))

(defn production? [] (= (System/getenv "CLOJURE_ENV") "production"))
