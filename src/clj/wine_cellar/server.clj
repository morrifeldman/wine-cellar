(ns wine-cellar.server
  (:require [org.httpkit.server :as http-kit]
            [wine-cellar.routes :refer [app]]
            [wine-cellar.db.setup :as db-setup]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [wine-cellar.auth.config :as auth-config]
            [mount.core :as mount :refer [defstate]]))

(defn start-server!
  [port]
  (db-setup/initialize-db)
  (let [session-store (cookie-store {:key (.getBytes
                                           (auth-config/get-cookie-store-key))})
        wrapped-app (-> app
                        wrap-cookies
                        wrap-params
                        (wrap-session {:store session-store
                                       :cookie-attrs {:http-only true
                                                      :same-site :lax
                                                      :path "/"}}))
        server (http-kit/run-server wrapped-app {:port port})]
    (println "Started http server on port:" port)
    server))

(defn get-port
  []
  (if-let [port-str (System/getenv "PORT")]
    (Integer/parseInt port-str)
    3000))

(defstate server :start (start-server! (get-port)) :stop (server))

(defn -main [& _] (mount/start))

#_(mount/start)
#_(mount/stop)
