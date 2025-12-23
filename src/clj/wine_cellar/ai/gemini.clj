(ns wine-cellar.ai.gemini
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.common :as common]
            [wine-cellar.config-utils :as config-utils]))

(def base-url "https://generativelanguage.googleapis.com/v1beta/models")

(defstate api-key :start (config-utils/get-config "GEMINI_API_KEY"))

(defstate model
          :start
          (config-utils/get-config "GEMINI_MODEL" :fallback "gemini-3"))

(defstate lite-model
          :start
          (config-utils/get-config "GEMINI_LITE_MODEL"
                                   :fallback
                                   "gemini-2.5-flash-lite"))

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

(defn- build-request-body
  [{:keys [system messages response-schema max-tokens temperature]}]
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
      (seq generation-config) (assoc :generationConfig generation-config))))

(defn- call-gemini-api
  [request & {:keys [model-override parse-json?]}]
  (ensure-api-key!)
  (let [request-body (build-request-body request)
        target-model (or model-override model)
        url (str base-url "/" target-model ":generateContent?key=" api-key)]
    (tap> ["gemini-request" request-body])
    (let [{:keys [status body error]}
          @(http/post url
                      {:body (json/write-value-as-string request-body)
                       :headers {"Content-Type" "application/json"}
                       :as :text
                       :timeout 180000})]
      (when error
        (throw (ex-info "Gemini API network error" {:status 500 :error error})))
      (let [parsed (try (json/read-value body json-mapper)
                        (catch Exception _ body))]
        (when (not= 200 status)
          (tap> ["gemini-error" parsed])
          (throw (ex-info "Gemini API returned error"
                          {:status status :error parsed})))
        (let [candidate (first (:candidates parsed))
              parts (get-in candidate [:content :parts])
              text-response (some-> (first parts)
                                    :text)]
          (when (str/blank? text-response)
            (tap> ["gemini-no-text" parsed])
            (throw (ex-info "Gemini response contained no text"
                            {:status 500
                             :error "Gemini response contained no text"
                             :response parsed})))
          (if parse-json?
            (try (json/read-value text-response json-mapper)
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
        request {:system full-system :messages messages}]
    (call-gemini-api request)))

(def drinking-window-schema
  {:type "OBJECT"
   :properties {:drink_from_year {:type "INTEGER"}
                :drink_until_year {:type "INTEGER"}
                :confidence {:type "STRING" :enum ["high" "medium" "low"]}
                :reasoning {:type "STRING"}}
   :required ["drink_from_year" "drink_until_year" "confidence" "reasoning"]})

(defn suggest-drinking-window
  [{:keys [system user]}]
  (let [request {:system system
                 :messages [{:role "user" :content user}]
                 :response-schema drinking-window-schema
                 :max-tokens 10000}]
    (call-gemini-api request :parse-json? true)))

(def label-analysis-schema
  {:type "OBJECT"
   :properties {:producer {:type "STRING"}
                :name {:type "STRING"}
                :vintage {:type "INTEGER"}
                :country {:type "STRING"}
                :region {:type "STRING"}
                :aoc {:type "STRING"}
                :vineyard {:type "STRING"}
                :classification {:type "STRING"}
                :style {:type "STRING" :enum (vec (sort common/wine-styles))}
                :level {:type "STRING" :enum (vec (sort common/wine-levels))}
                :bottle_format {:type "STRING"
                                :enum (vec common/bottle-formats)}
                :alcohol_percentage {:type "NUMBER"}}})

(defn analyze-wine-label
  [{:keys [system user-content]}]
  (let [request {:system system
                 :messages [{:role "user" :content user-content}]
                 :response-schema label-analysis-schema
                 :max-tokens 20000}]
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
    (call-gemini-api request :model-override lite-model)))

(defn generate-report-commentary
  "Generates a report commentary using Gemini."
  [{:keys [system user]}]
  (let [request {:system system
                 :messages [{:role "user" :content user}]
                 :max-tokens 20000
                 :temperature 0.7}]
    (call-gemini-api request)))
