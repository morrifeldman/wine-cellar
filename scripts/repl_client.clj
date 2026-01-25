#!/usr/bin/env bb

(require '[babashka.deps :as deps])

;; Use the exact working dependency we found
(deps/add-deps '{:deps {babashka/nrepl-client
                        {:git/url "https://github.com/babashka/nrepl-client"
                         :git/sha "19fbef2525e47d80b9278c49a545de58f48ee7cf"}}})

(require '[babashka.nrepl-client :as nrepl] '[clojure.string :as str])

(defn -main
  []
  (let [port-file (or (System/getenv "REPL_PORT_FILE") ".nrepl-port")
        cljs-build (System/getenv "REPL_CLJS_BUILD")
        port (try (Integer/parseInt (str/trim (slurp port-file)))
                  (catch Exception _
                    (binding [*out* *err*]
                      (println "Error: Port file not found or invalid:"
                               port-file))
                    (System/exit 1)))
        expr (first *command-line-args*)]
    (if-not expr
      (do
        (println
         "Usage: [REPL_PORT_FILE=...] [REPL_CLJS_BUILD=...] bb repl_client.clj '<expr>'")
        (System/exit 1))
      (let [;; If CLJS, we wrap the expression in shadow/cljs-eval
            final-expr
            (if cljs-build
              (str "(do (require '[shadow.cljs.devtools.api :as shadow]) "
                   "(shadow/cljs-eval :"
                   cljs-build
                   " "
                   (pr-str expr)
                   " {}))")
              expr)
            res (nrepl/eval-expr {:port port :expr final-expr})]
        ;; Surface stdout/stderr if any (cljs-eval returns them in the map)
        (when-not (empty? (:out res)) (print (:out res)))
        (when-not (empty? (:err res))
          (binding [*out* *err*] (print (:err res))))
        ;; Print values (the result of evaluation)
        (run! println (:vals res))
        ;; Also handle the nested :results key from cljs-eval if it exists
        (when-let [cljs-results (some-> res
                                        :vals
                                        first
                                        :results)]
          (run! println cljs-results))
        (System/exit 0)))))

(-main)