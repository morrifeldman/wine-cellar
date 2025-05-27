(ns wine-cellar.server
  (:require [mount.core :as mount :refer [defstate]]
            [org.httpkit.server :as http-kit]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [wine-cellar.auth.config :as auth-config]
            [wine-cellar.config-utils :refer [backend-port]]
            [wine-cellar.db.setup :as db-setup]
            [wine-cellar.routes :refer [app]]))

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



(defstate server :start (start-server! backend-port) :stop (server))

(defn -main [& _] (mount/start))

#_(mount/start)
#_(mount/stop)
