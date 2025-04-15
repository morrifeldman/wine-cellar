(ns format-zprint
  (:require [clojure.java.io :as io]
            [zprint.core :as zp]))

(def default-options
  {:style :community
   :map {:comma? false}
   :width 80})

(defn clojure-file?
  "Check if a file is a Clojure or ClojureScript file."
  [file]
  (let [name (.getName file)]
    (or (.endsWith name ".clj")
        (.endsWith name ".cljc")
        (.endsWith name ".cljs"))))

(defn find-clojure-files
  "Find all Clojure files in the given directory and its subdirectories."
  [dir]
  (->> (file-seq (io/file dir))
       (filter #(.isFile %))
       (filter clojure-file?)))

(defn format-file!
  "Format a single file using zprint."
  [file options]
  (let [path (.getPath file)]
    (try
      (println "Formatting" path)
      (zp/zprint-file path path path options)
      (println "✓ Successfully formatted" path)
      true
      (catch Exception e
        (println "✗ Error formatting" path ":" (.getMessage e))
        false))))

(defn format-directory!
  "Format all Clojure files in the given directory and its subdirectories."
  [dir options]
  (let [files (find-clojure-files dir)
        results (map #(format-file! % options) files)
        success-count (count (filter identity results))
        total-count (count results)]
    (println)
    (println (format "Formatted %d/%d files successfully." success-count total-count))
    (when (< success-count total-count)
      (println (format "Failed to format %d files." (- total-count success-count))))))

(defn -main
  "Format all Clojure files in the project."
  [& _]
  (let [options default-options
        src-dirs ["src/clj" "src/cljc" "src/cljs" "dev"]]
    (println "Starting formatting with zprint...")
    (doseq [dir src-dirs]
      (println (str "\nProcessing directory: " dir))
      (format-directory! dir options))
    (println "\nFormatting complete!")
    (System/exit 0)))
