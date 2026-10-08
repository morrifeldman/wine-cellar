(ns wine-cellar.ai.gemini
  (:require [clojure.string :as str]
            [wine-cellar.ai.errors :as errors]
            [wine-cellar.ai.schemas :as schemas]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.config-utils :as config-utils]))

(def base-url "https://generativelanguage.googleapis.com/v1beta/models")

(defstate api-key :start (config-utils/get-config "GEMINI_API_KEY"))

(defstate model :start (config-utils/ai-model :gemini :model))

(defstate small-model :start (config-utils/ai-model :gemini :small-model))

(def ^:private json-mapper json/keyword-keys-object-mapper)

(defn- ensure-api-key!
  []
  (or api-key
      (throw (ex-info "Gemini provider not configured"
                      {:status 400
                       :error
                       "Gemini provider is not configured. Set GEMINI_API_KEY."
                       :code :gemini/missing-api-key}))))

(defn- transform-part
  [part]
  (cond (string? part) {:text part}
        (:text part) {:text (:text part)}
        (= "image" (:type part))
        (let [data (get-in part [:source :data])
              mime-type (get-in part [:source :media_type])]
          {:inline_data {:mime_type mime-type :data data}})
        :else {:text (str part)}))

(defn- transform-message
  [{:keys [role content]}]
  (let [parts (if (vector? content)
                (mapv transform-part content)
                [{:text (str content)}])]
    {:role (if (= role "assistant") "model" "user") :parts parts}))

(def ^:private google-search-tool
  "Gemini's own grounded search, so chat can answer questions that depend on
   something current without us fetching anything ourselves."
  {:google_search {}})

(defn- build-request-body
  [{:keys [system messages response-schema max-tokens temperature tools]}]
  (let [contents (mapv transform-message messages)
        system-instruction (when system {:parts [{:text system}]})
        generation-config (cond-> {}
                            response-schema
                            (assoc :response_mime_type "application/json"
                                   :response_schema response-schema)
                            max-tokens (assoc :maxOutputTokens max-tokens)
                            temperature (assoc :temperature temperature))]
    (cond-> {:contents contents}
      system-instruction (assoc :system_instruction system-instruction)
      (seq tools) (assoc :tools tools)
      (seq generation-config) (assoc :generationConfig generation-config))))

(defn- call-gemini-api
  [request & {:keys [model-override parse-json?]}]
  (ensure-api-key!)
  (let [request-body (build-request-body request)
        target-model (or model-override model)
        ;; Key in a header, not the URL, so it stays out of any request
        ;; log.
        url (str base-url "/" target-model ":generateContent")]
    (tap> ["gemini-request" request-body])
    (let [{:keys [status body error]}
          @(http/post url
                      {:body (json/write-value-as-string request-body)
                       :headers {"Content-Type" "application/json"
                                 "x-goog-api-key" api-key}
                       :as :text
                       :timeout 180000})]
      (when error
        (throw
         (errors/upstream-error "Gemini" "API network error" {:cause error})))
      (let [parsed (try (json/read-value body json-mapper)
                        (catch Exception _ body))]
        (when (not= 200 status)
          (tap> ["gemini-error" parsed])
          (throw (errors/upstream-error "Gemini"
                                        "API returned an error"
                                        {:status status :parsed parsed})))
        (let [candidate (first (:candidates parsed))
              parts (get-in candidate [:content :parts])
              ;; A grounded answer comes back split across several parts,
              ;; so take every one of them rather than just the first.
              text-response (->> parts
                                 (keep :text)
                                 (remove str/blank?)
                                 (str/join))]
          (when (str/blank? text-response)
            (tap> ["gemini-no-text" parsed])
            (throw (ex-info "Gemini response contained no text"
                            {:status 500
                             :error "Gemini response contained no text"
                             :response parsed})))
          (if parse-json?
            (try (let [parsed-json (json/read-value text-response json-mapper)]
                   ;; Gemini schemas can't express nullable fields, so the
                   ;; model sometimes returns the literal string "null" —
                   ;; strip those.
                   (if (map? parsed-json)
                     (into {} (remove (fn [[_ v]] (= v "null"))) parsed-json)
                     parsed-json))
                 (catch Exception e
                   (tap> ["gemini-json-parse-error" text-response])
                   (throw (ex-info "Failed to parse Gemini response as JSON"
                                   {:status 500
                                    :error
                                    "Failed to parse Gemini response as JSON"
                                    :details (.getMessage e)
                                    :response text-response}
                                   e))))
            text-response))))))

;; Feature Implementations

(defn chat-about-wines
  "Chat about wines using Gemini."
  [{:keys [system-text context-text messages]}]
  (let [full-system (str system-text "\n\n" context-text)
        request
        {:system full-system :messages messages :tools [google-search-tool]}]
    (call-gemini-api request)))

(defn suggest-drinking-window
  [{:keys [system user]}]
  (let [request {:system system
                 :messages [{:role "user" :content user}]
                 :response-schema (schemas/->gemini-schema
                                   schemas/drinking-window)
                 :max-tokens 10000}]
    (call-gemini-api request :parse-json? true)))

(defn analyze-wine-label
  [{:keys [system user-content]}]
  (let [request {:system system
                 :messages [{:role "user" :content user-content}]
                 :response-schema (schemas/->gemini-schema schemas/wine-label)
                 :max-tokens 20000}]
    (call-gemini-api request :parse-json? true)))

(defn analyze-spirit-label
  [{:keys [system user-content]}]
  (let [request {:system system
                 :messages [{:role "user" :content user-content}]
                 :response-schema (schemas/->gemini-schema schemas/spirit-label)
                 :max-tokens 10000}]
    (call-gemini-api request :parse-json? true)))

(defn generate-wine-summary
  [{:keys [system user]}]
  (let [request {:system system
                 :messages [{:role "user" :content user}]
                 :max-tokens 10000}]
    (call-gemini-api request)))

(defn generate-conversation-title
  [{:keys [system user]}]
  (let [request {:system system
                 :messages [{:role "user" :content user}]
                 :max-tokens 1000
                 :temperature 0.2}]
    (call-gemini-api request :model-override small-model)))

(defn generate-report-commentary
  "Generates a report commentary using Gemini."
  [{:keys [system user]}]
  (let [request {:system system
                 :messages [{:role "user" :content user}]
                 :max-tokens 20000
                 :temperature 0.7}]
    (call-gemini-api request)))
