(ns wine-cellar.version
  (:require [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def version-cache (r/atom nil))

(defn fetch-version!
  []
  (go (try (let [response (<! (http/get "/version.json"))]
             (if (:success response)
               (reset! version-cache (:body response))
               (reset! version-cache {:version "unknown" :commit "unknown"})))
           (catch :default e
             (reset! version-cache {:version "error" :commit "error"})))))

(defn get-version-info [] @version-cache)

(defn version-string
  []
  (if-let [version-info @version-cache]
    (str "v" (:version version-info) " (" (:commit version-info) ")")
    "Loading..."))