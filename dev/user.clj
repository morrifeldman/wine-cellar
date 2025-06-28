(ns user
  (:require [clojure.tools.namespace.repl :as tnsrepl]
            [mount.core :as mount]
            [wine-cellar.server]
            [portal.api :as p]))

(tnsrepl/disable-reload!)

;; Start the web server automatically when REPL loads
(defonce start-server (mount/start))

;; Backend server management
(defn restart-server [] (mount/stop) (mount/start))
(defn stop-server [] (mount/stop))
(defonce portal (p/open))
(add-tap #'p/submit)
(tap> {:hello "from clojure backend"})

(comment
  (restart-server)
  (stop-server))
