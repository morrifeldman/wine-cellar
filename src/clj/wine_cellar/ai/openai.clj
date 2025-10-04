(ns wine-cellar.ai.openai
  "OpenAI Responses client for wine-related chat interactions."
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.config-utils :as config-utils]))

(def responses-url "https://api.openai.com/v1/responses")

(defstate model
  :start
  (config-utils/get-config "OPENAI_MODEL" :fallback "gpt-5"))

(defstate api-key :start (config-utils/get-config "OPENAI_API_KEY"))

(def ^:private json-mapper json/keyword-keys-object-mapper)

(defn- ensure-api-key!
  []
  (or api-key
      (throw (ex-info "OpenAI provider not configured"
                      {:status 400
                       :error "OpenAI provider is not configured. Set OPENAI_API_KEY to enable ChatGPT."
                       :code :openai/missing-api-key}))))

;; Shared prompt helpers now live in wine-cellar.ai.prompts

(defn- ensure-data-url
  [data media-type]
  (when (and data (string? data))
    (if (str/starts-with? data "data:")
      data
      (let [mt (or media-type "image/png")]
        (format "data:%s;base64,%s" mt data)))))

(defn- message->content
  [{:keys [role content]}]
  (let [input? (= role "user")
        items (cond
                (vector? content) content
                (string? content) [{:type "text" :text content}]
                :else [{:type "text" :text (or (:text content) "")}])]
    {:role role
     :content (mapv (fn [item]
                      (case (:type item)
                        "image" (let [data (get-in item [:source :data])
                                       media-type (get-in item [:source :media_type])
                                       data-url (ensure-data-url data media-type)]
                                   (if data-url
                                     {:type "input_image"
                                      :image_url data-url}
                                     {:type (if input? "input_text" "output_text")
                                      :text ""}))
                        "text" {:type (if input? "input_text" "output_text")
                                 :text (:text item)}
                        {:type (if input? "input_text" "output_text")
                         :text (or (:text item) "")}))
                    items)}))

(defn- conversation->input
  [messages]
  (mapv message->content messages))

(defn- label-analysis-schema
  []
  {:type "object"
   :properties {:producer {:type ["string" "null"]}
                :name {:type ["string" "null"]}
                :vintage {:type ["integer" "null"]}
                :country {:type ["string" "null"]}
                :region {:type ["string" "null"]}
                :aoc {:type ["string" "null"]}
                :vineyard {:type ["string" "null"]}
                :classification {:type ["string" "null"]}
                :style {:type ["string" "null"]}
                :level {:type ["string" "null"]}
                :alcohol_percentage {:type ["number" "null"]}}
   :required [:producer :name :vintage :country :region :aoc :vineyard
              :classification :style :level :alcohol_percentage]
   :additionalProperties false})

(defn- build-request
  [{:keys [system-text context-text messages]}]
  {:pre [(string? system-text) (string? context-text) (vector? messages)]}
  {:input (into [{:role "system"
                  :content [{:type "input_text"
                             :text system-text}]}
                 {:role "system"
                  :content [{:type "input_text"
                             :text context-text}]}]
                (conversation->input messages))})

(defn- output-content
  [response-body]
  (mapcat :content (:output response-body)))

(defn- extract-text
  [response-body]
  (some->> (output-content response-body)
           (filter #(= "output_text" (:type %)))
           (map :text)
           (remove str/blank?)
           (str/join "\n")))

(defn- extract-json-output
  [response-body]
  (let [content (output-content response-body)]
    (cond
      (seq (filter #(= "output_json" (:type %)) content))
      (some (fn [item]
              (when (= "output_json" (:type item)) (:json item)))
            content)

      (seq (filter #(= "output_data" (:type %)) content))
      (some (fn [item]
              (when (= "output_data" (:type item))
                (some-> item :data first :json)))
            content)

      (seq (filter #(= "output_text" (:type %)) content))
      (let [text (extract-text response-body)]
        (when (seq text)
          (json/read-value text json-mapper)))

      :else nil)))

(defn- call-openai-responses
  [request parse-json?]
  (ensure-api-key!)
  (let [payload (-> request
                    (assoc :model (or (:model request) model))
                    (assoc :reasoning {:effort "low"}))]
    (tap> ["openai-request" payload])
    (let [{:keys [status body error] :as response}
          (deref (http/post responses-url
                            {:headers {"authorization" (str "Bearer " api-key)
                                       "content-type" "application/json"}
                             :body (json/write-value-as-string payload)
                             :as :text
                             :timeout 60000}))
          parsed (when body (json/read-value body json-mapper))]
      (tap> ["openai-response" (assoc response :parsed-body parsed)])
      (if (= 200 status)
        (if parse-json?
          (try
            (if-let [json-output (extract-json-output parsed)]
              (do
                (tap> ["parsed openai-response" json-output])
                json-output)
              (throw (ex-info "OpenAI response missing JSON content"
                              {:status status :response parsed})))
            (catch Exception e
              (throw (ex-info "Failed to parse OpenAI JSON response"
                              {:status 500 :response parsed}
                              e))))
          (let [text (extract-text parsed)]
            (if (seq (str text))
              text
              (throw (ex-info "OpenAI response missing assistant text"
                              {:status status :response parsed})))))
        (throw (ex-info "OpenAI Responses API call failed"
                        {:status status :response parsed :error error}))))))

(defn chat-about-wines
  [prompt]
  (call-openai-responses (build-request prompt) false))

(defn suggest-drinking-window
  [{:keys [system user]}]
  (assert (string? system) "Drinking-window prompt requires :system text")
  (assert (string? user) "Drinking-window prompt requires :user text")
  (let [request {:input [{:role "system"
                          :content [{:type "input_text"
                                     :text system}]}
                         {:role "user"
                          :content [{:type "input_text"
                                     :text user}]}]
                 :text {:format {:type "json_schema"
                                 :name "DrinkingWindow"
                                 :strict true
                                 :schema {:type "object"
                                          :properties {:drink_from_year {:type "integer"}
                                                       :drink_until_year {:type "integer"}
                                                       :confidence {:type "string"}
                                                       :reasoning {:type "string"}}
                                          :required [:drink_from_year :drink_until_year :confidence :reasoning]
                                          :additionalProperties false}}}
                 :max_output_tokens 600
                 :reasoning {:effort "low"}}]
    (call-openai-responses request true)))

(defn generate-wine-summary
  [{:keys [system user]}]
  (assert (string? system) "Wine-summary prompt requires :system text")
  (assert (string? user) "Wine-summary prompt requires :user text")
  (let [request {:input [{:role "system"
                          :content [{:type "input_text"
                                     :text system}]}
                         {:role "user"
                          :content [{:type "input_text"
                                     :text user}]}]
                 :max_output_tokens 800}]
    (call-openai-responses request false)))

(defn analyze-wine-label
  [{:keys [system user-content]}]
  (assert (string? system) "Label analysis prompt requires :system text")
  (assert (vector? user-content) "Label analysis prompt requires :user-content vector")
  (let [user-message (message->content {:role "user"
                                        :content user-content})
        request {:input (into [{:role "system"
                                 :content [{:type "input_text"
                                            :text system}]}]
                               [user-message])
                 :text {:format {:type "json_schema"
                                 :name "WineLabelAnalysis"
                                 :strict true
                                 :schema (label-analysis-schema)}}
                 :max_output_tokens 900
                 :reasoning {:effort "low"}}]
    (call-openai-responses request true)))
