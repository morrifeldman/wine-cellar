(ns wine-cellar.config)

(defn get-api-base-url
  []
  (let [host (.. js/window -location -hostname)
        protocol (.. js/window -location -protocol)]
    (if (= host "localhost")
      (str protocol "//" host ":3000") ;; Local development - backend on port 3000
      (str protocol "//" host)))) ;; ngrok or production - same host
