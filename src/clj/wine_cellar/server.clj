(ns wine-cellar.server
  (:require [org.httpkit.server :as http-kit]
            [wine-cellar.routes :refer [app]]
            [wine-cellar.db :as db]))

(defonce server (atom nil))

(defn stop-server! []
  (when @server
    (@server)
    (reset! server nil)))

(defn start-server! [port]
  (stop-server!)
  (db/initialize-db)
  (reset! server (http-kit/run-server app {:port port}))
  (println (str "Server running on port " port)))

(defn -main [& args]
  (let [port (or (some-> args first parse-long) 3000)]
    (start-server! port)))

(comment
  (start-server! 3000)
  (stop-server!))
