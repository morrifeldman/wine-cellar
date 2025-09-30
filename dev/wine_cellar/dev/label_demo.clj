(ns wine-cellar.dev.label-demo
  "Helpers for exercising the wine-label JSON tool across Anthropic and OpenAI with local sample images."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [wine-cellar.ai.anthropic :as anthropic]
            [wine-cellar.ai.openai :as openai]
            [wine-cellar.ai.prompts :as prompts])
  (:import (java.nio.file Files Paths)
           (java.util Base64)))

(def default-front-image "dev/assets/klimt-front.jpg")
(def default-back-image  "dev/assets/klimt-back.jpg")

(defn- absolute-path
  [path]
  (.getAbsolutePath (io/file path)))

(defn- ensure-file!
  [path]
  (let [f (io/file path)]
    (when-not (.exists f)
      (throw (ex-info (str "Sample image not found: " path)
                      {:path path
                       :hint (str "Drop the JPEG from the issue (e.g. Klimt Grüner front/back) into "
                                  (absolute-path "dev/assets/")
                                  " and name it " (.getName (io/file path)))})))
    f))

(defn- mime-type
  [path]
  (let [lower (-> path str/lower-case)]
    (cond
      (str/ends-with? lower ".png") "image/png"
      (or (str/ends-with? lower ".jpg")
          (str/ends-with? lower ".jpeg")) "image/jpeg"
      :else "application/octet-stream")))

(defn file->data-uri
  "Reads the file at `path` and returns a `data:` URI suitable for image prompts."
  [path]
  (let [file (ensure-file! path)
        bytes (Files/readAllBytes (Paths/get (.getAbsolutePath file) (make-array String 0)))
        encoded (.encodeToString (Base64/getEncoder) bytes)]
    (format "data:%s;base64,%s" (mime-type path) encoded)))

(defn sample-label-request
  "Build the shared label-analysis prompt map using optional overrides for front/back images.
  Arguments may be nil to skip (e.g. only front)."
  ([front back]
   (let [front-uri (when front (file->data-uri front))
         back-uri (when back (file->data-uri back))]
     (prompts/label-analysis-prompt front-uri back-uri)))
  ([]
   (sample-label-request default-front-image default-back-image)))

(defn run-anthropic!
  "Calls Anthropic's label analysis tool using the sample (or provided) images.
  Returns the parsed JSON response map."
  ([]
   (run-anthropic! default-front-image default-back-image))
  ([front back]
   (let [request (sample-label-request front back)]
     (anthropic/analyze-wine-label request))))

(defn run-openai!
  "Calls OpenAI's label analysis tool using the sample (or provided) images.
  Returns the parsed JSON response map."
  ([]
   (run-openai! default-front-image default-back-image))
  ([front back]
   (let [prompt (sample-label-request front back)]
     (openai/analyze-wine-label prompt))))

(defn run-all!
  "Execute both providers and return {:anthropic … :openai …} for quick comparison."
  ([]
   (run-all! default-front-image default-back-image))
  ([front back]
   {:anthropic (run-anthropic! front back)
    :openai (run-openai! front back)}))

(comment
  ;; Evaluate forms in this block from a connected REPL to try the workflow.
  ;; 1. Place the supplied Klimt Grüner JPGs into dev/assets/klimt-front.jpg and .../klimt-back.jpg
  ;; 2. Require this namespace: (require 'wine-cellar.dev.anthropic-label-demo)
  ;; 3. Run either provider and inspect the parsed response map:
  (run-anthropic!)
  (run-openai!)

  ;; Compare both providers in one call:
  (run-all!)

  ;; To test with alternate image paths:
  (run-openai! "dev/assets/other-front.jpg" "dev/assets/other-back.jpg")

  ;; To inspect just the constructed prompt data:
  (sample-label-request)
  )
