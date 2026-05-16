(ns wine-cellar.config)

(defn get-api-base-url
  []
  (let [loc (.. js/window -location)
        host (.-hostname loc)
        protocol (.-protocol loc)
        port (.-port loc)]
    (if (or (= host "localhost") (seq port))
      (str protocol "//" host ":3000") ;; Local dev (desktop or LAN/phone)
                                       ;; - backend on port 3000
      (str protocol "//" host)))) ;; ngrok or production - same host
