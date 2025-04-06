(ns wine-cellar.server
  (:require [org.httpkit.server :as http-kit]
            [wine-cellar.routes :refer [app]]
            [wine-cellar.db.setup :as db-setup]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]))

(defonce server (atom nil))

(defn stop-server! []
  (when @server
    (@server)
    (reset! server nil)))

(defn start-server! [port]
  (stop-server!)
  (db-setup/initialize-db)
  (let [wrapped-app (-> app
                        wrap-cookies
                        wrap-params)]
    (reset! server (http-kit/run-server wrapped-app {:port port}))
    (println (str "Server running on port " port))))

(defn get-port []
  (if-let [port-str (System/getenv "PORT")]
    (Integer/parseInt port-str)
    3000))

(defn -main [& _]
  (let [port (get-port)]
    (start-server! port)))

(comment
  (start-server! 3000)
  (stop-server!))

