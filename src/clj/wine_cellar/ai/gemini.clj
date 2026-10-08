(ns wine-cellar.ai.gemini
  (:require [clojure.string :as str]
            [wine-cellar.ai.http :as ai-http]
            [wine-cellar.ai.schemas :as schemas]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
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

(defn- check-finished
  "Gemini reports a blocked prompt, a length cut-off or a safety stop inside
  a 200; say so rather than pass off half an answer."
  [parsed candidate]
  (when-let [blocked (get-in parsed [:promptFeedback :blockReason])]
    (throw (ai-http/bad-reply
            (str "Gemini declined the request (" blocked ")"))))
  (let [reason (:finishReason candidate)]
    (case reason
      (nil "STOP") nil
      "MAX_TOKENS" (throw (ai-http/bad-reply
                           "Gemini's reply ran out of room before it finished"))
      (throw (ai-http/bad-reply (str "Gemini stopped early (" reason ")"))))))

(defn- call-gemini-api
  [request & {:keys [model-override parse-json?]}]
  (ensure-api-key!)
  (let [request-body (build-request-body request)
        target-model (or model-override model)
        ;; Key in a header, not the URL, so it stays out of any request
        ;; log.
        url (str base-url "/" target-model ":generateContent")]
    (tap> ["gemini-request" request-body])
    (let [parsed (ai-http/post-json! "Gemini"
                                     url
                                     {"x-goog-api-key" api-key}
                                     request-body)
          candidate (first (:candidates parsed))
          _ (check-finished parsed candidate)
          ;; A grounded answer comes back split across several parts, so
          ;; take every one of them rather than just the first.
          text-response (->> (get-in candidate [:content :parts])
                             (keep :text)
                             (remove str/blank?)
                             (str/join))]
      (when (str/blank? text-response)
        (tap> ["gemini-no-text" parsed])
        (throw (ai-http/bad-reply "Gemini's reply had no text in it")))
      (if parse-json?
        (try (let [parsed-json (json/read-value text-response json-mapper)]
               ;; Older replies sometimes spelled a missing value as the
               ;; string "null"; the schemas now mark fields nullable, but
               ;; strip any that still slip through.
               (if (map? parsed-json)
                 (into {} (remove (fn [[_ v]] (= v "null"))) parsed-json)
                 parsed-json))
             (catch Exception _
               (tap> ["gemini-json-parse-error" text-response])
               (throw (ai-http/bad-reply "Gemini's reply wasn't valid JSON"))))
        text-response))))

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
