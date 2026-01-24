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
  ;; Check if we should start ngrok
  (let [ngrok-url (System/getenv "NGROK_URL")
        processes
        (if ngrok-url
          (do
            (println "Starting ngrok with URL:" ngrok-url)
            (let
              [ngrok (p/process ["ngrok" "http" (str "--url=" ngrok-url) "3000"
                                 "--log=stdout"]
                                {:inherit true})
               backend (p/process ["clj" "-M:dev:repl/conjure"] {:inherit true})
               frontend
               (p/process
                ["/bin/bash" "-c"
                 "npx shadow-cljs watch app 2>&1 | tee .shadow-cljs/build.log"]
                {:inherit true})]
              [ngrok backend frontend]))
          (do
            (println "No NGROK_URL set, starting without ngrok")
            (let
              [backend (p/process ["clj" "-M:dev:repl/conjure"] {:inherit true})
               frontend
               (p/process
                ["/bin/bash" "-c"
                 "npx shadow-cljs watch app 2>&1 | tee .shadow-cljs/build.log"]
                {:inherit true})]
              [backend frontend])))]
    (if ngrok-url
      (println
       "All processes started:\n- ngrok:"
       ngrok-url
       "\n- backend: http://localhost:3000\n- frontend: http://localhost:8080")
      (println
       "Processes started:\n- backend: http://localhost:3000\n- frontend: http://localhost:8080"))
    (println "Press Ctrl+C to exit.")
    ;; Add shutdown hook to ensure processes are terminated
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (println "\nShutting down processes...")
                                 (doseq [process processes]
                                   (p/destroy-tree process)))))
    ;; Wait for any process to complete
    (doseq [process processes] @(p/check process))))

(defn -main [& _args] (start-dev-environment))
