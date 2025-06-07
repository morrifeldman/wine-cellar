(ns wine-cellar.dev
  (:require [babashka.process :as p]
            [clojure.java.io :as io]))

(defn make-executable
  [script-path]
  (let [file (io/file script-path)]
    (when (.exists file) (.setExecutable file true))))

(defn start-dev-environment
  []
  (println "Starting Wine Cellar development environment...")
  ;; Make sure our shell script is executable
  (make-executable "scripts/start-dev.sh")
  ;; Alternative approach using Babashka process
  (let [backend (p/process ["clojure" "-M:dev:repl/conjure"] {:inherit true})
        frontend (p/process ["npx" "shadow-cljs" "watch" "app"]
                            {:inherit true})]
    (println "Both processes started. Press Ctrl+C to exit.")
    ;; Add shutdown hook to ensure processes are terminated
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (println "\nShutting down processes...")
                                 (p/destroy-tree backend)
                                 (p/destroy-tree frontend))))
    ;; Wait for processes to complete
    @(p/check backend)
    @(p/check frontend)))

(defn -main [& _args] (start-dev-environment))
