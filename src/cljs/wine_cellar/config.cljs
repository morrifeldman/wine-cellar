(ns wine-cellar.config)

(defn get-api-base-url
  []
  (let [host (.. js/window -location -hostname)
        port (.. js/window -location -port)
        protocol (.. js/window -location -protocol)]
    (if (= host "localhost")
      "http://localhost:3000" ;; Default for local development
      (str protocol "//" host (when-not (empty? port) (str ":" port))))))
