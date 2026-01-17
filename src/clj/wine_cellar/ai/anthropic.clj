(ns wine-cellar.ai.anthropic
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.common :as common]
            [wine-cellar.config-utils :as config-utils]))

(def api-url "https://api.anthropic.com/v1/messages")

(defstate model
          :start
          (config-utils/get-config "ANTHROPIC_MODEL"
                                   :fallback
                                   "claude-sonnet-4-20250514")) ; Default to current model

(defstate light-model
          :start
          (config-utils/get-config "ANTHROPIC_LIGHT_MODEL"
                                   :fallback
                                   "claude-3-5-haiku-20241022"))

(defstate api-key :start (config-utils/get-config "ANTHROPIC_API_KEY"))

(def drinking-window-tool-name "record_drinking_window")

(def drinking-window-tool
  (let
    [confidence-desc
     "Confidence level for this assessment; must be one of \"high\", \"medium\", or \"low\" per the drinking-window prompt."
     reasoning-desc
     "Brief justification focusing on the wine's peak-quality years and mentioning the broader enjoyable window."]
    {:name drinking-window-tool-name
     :description
     "Structured schema matching wine-cellar.ai.prompts/drinking-window-system-prompt."
     :input_schema
     {:type "object"
      :properties
      {:drink_from_year
       {:type "integer"
        :description
        "Year the wine reaches optimal drinking condition (integer)."}
       :drink_until_year
       {:type "integer"
        :description "Final year the wine remains at peak quality (integer)."}
       :confidence {:type "string" :description confidence-desc}
       :reasoning {:type "string" :description reasoning-desc}}
      :required [:drink_from_year :drink_until_year :confidence :reasoning]
      :additionalProperties false}}))

(def label-analysis-tool-name "record_wine_label")

(def label-analysis-tool
  (let [style-options (str/join ", " (sort common/wine-styles))
        designation-options (str/join ", " (sort common/wine-designations))
        format-options (str/join ", " common/bottle-formats)
        null-note
        "Return null when the label does not provide this information."]
    {:name label-analysis-tool-name
     :description
     "Structured schema matching wine-cellar.ai.prompts/label-analysis-system-prompt."
     :input_schema
     {:type "object"
      :properties
      {:producer {:type ["string" "null"]
                  :description (str "Producer or winery name. " null-note)}
       :name {:type ["string" "null"]
              :description (str
                            "Specific wine name if distinct from the producer. "
                            null-note)}
       :vintage {:type ["integer" "null"]
                 :description
                 (str "Vintage year as an integer, or null for non-vintage. "
                      null-note)}
       :country {:type ["string" "null"]
                 :description (str "Country of origin printed on the label. "
                                   null-note)}
       :region {:type ["string" "null"]
                :description (str "Region or sub-region within the country. "
                                  null-note)}
       :appellation {:type ["string" "null"]
                     :description (str
                                   "Appellation / controlled designation text. "
                                   null-note)}
       :vineyard {:type ["string" "null"]
                  :description (str "Specific vineyard name if mentioned. "
                                    null-note)}
       :classification {:type ["string" "null"]
                        :description
                        (str "Quality classification or other designation. "
                             null-note)}
       :style {:type ["string" "null"]
               :description (str "Wine style wording; align with: "
                                 style-options
                                 ". " null-note)}
       :designation
       {:type ["string" "null"]
        :description
        (str
         "Production tier or quality/aging designation (e.g. "
         designation-options
         "), often indicating longer maturation or specially selected lots. "
         null-note)}
       :bottle_format {:type ["string" "null"]
                       :description (str "Bottle format/size. Must be one of: "
                                         format-options
                                         ". " null-note)}
       :alcohol_percentage {:type ["number" "null"]
                            :description
                            (str "Alcohol percentage as a number (e.g. 12.5). "
                                 null-note)}}
      :required [:producer :name :vintage :country :region :appellation
                 :vineyard :classification :style :designation :bottle_format
                 :alcohol_percentage]
      :additionalProperties false}}))

(defn- build-request-body
  [{:keys [system messages tools tool_choice max_tokens temperature metadata
           stop_sequences]} model-override]
  (-> {:model model-override :max_tokens (or max_tokens 1000)}
      (cond-> system (assoc :system system))
      (assoc :messages messages)
      (cond-> (seq tools) (assoc :tools tools))
      (cond-> tool_choice (assoc :tool_choice tool_choice))
      (cond-> temperature (assoc :temperature temperature))
      (cond-> metadata (assoc :metadata metadata))
      (cond-> (seq stop_sequences) (assoc :stop_sequences stop_sequences))))

(defn- extract-text-content
  [content]
  (->> content
       (keep #(when (= "text" (:type %)) (:text %)))
       (remove str/blank?)
       (str/join "\n\n")))

(defn- parse-json-content
  [content]
  (if-let [tool-use (some #(when (= "tool_use" (:type %)) %) content)]
    (:input tool-use)
    (when-let [text (extract-text-content content)]
      (json/read-value text json/keyword-keys-object-mapper))))

(defn call-anthropic-api
  "Makes a request to the Anthropic API with messages array and optional JSON parsing"
  ([request] (call-anthropic-api request false))
  ([request parse-json?] (call-anthropic-api request parse-json? model))
  ([request parse-json? model-override]
   (let [request-body (build-request-body request model-override)]
     (tap> ["anthropic-request-body" request-body])
     (let [{:keys [status body error] :as response}
           (deref (http/post api-url
                             {:body (json/write-value-as-string request-body)
                              :headers {"x-api-key" api-key
                                        "anthropic-version" "2023-06-01"
                                        "content-type" "application/json"}
                              :as :text
                              :keepalive 60000
                              :timeout 60000}))
           parsed (when body
                    (json/read-value body json/keyword-keys-object-mapper))
           response-with-parsed (assoc response :parsed parsed)
           content (:content parsed)]
       (when error
         (tap> ["anthropic-request-error" error])
         (throw
          (ex-info "Anthropic API request failed" response-with-parsed error)))
       (when (not= 200 status)
         (tap> ["anthropic-request-non-200" error])
         (throw (ex-info "Anthropic API request failed" response-with-parsed)))
       (if parse-json?
         (try (if-let [parsed-response (parse-json-content content)]
                (do (tap> ["anthropic-parsed-response" parsed-response])
                    parsed-response)
                (throw (ex-info "Anthropic response missing JSON payload"
                                response-with-parsed)))
              (catch Exception e
                (throw (ex-info "Failed to parse AI response as JSON"
                                response-with-parsed
                                e))))
         (let [text-content (extract-text-content content)]
           (if (seq (str text-content))
             text-content
             (throw (ex-info "Anthropic response missing assistant text"
                             response-with-parsed)))))))))

(defn suggest-drinking-window
  "Suggests an optimal drinking window for a wine using Anthropic's Claude API.
   Expects a map with :system and :user prompt strings."
  [{:keys [system user]}]
  (assert (string? system) "Drinking-window prompt requires :system text")
  (assert (string? user) "Drinking-window prompt requires :user text")
  (let [request {:system system
                 :messages [{:role "user" :content [{:type "text" :text user}]}]
                 :tools [drinking-window-tool]
                 :tool_choice {:type "tool" :name drinking-window-tool-name}
                 :max_tokens 600}]
    (call-anthropic-api request true)))

(defn analyze-wine-label
  "Analyzes wine label images using Anthropic's Claude API.
   Expects {:system string :user-content vector-of-content}."
  [{:keys [system user-content]}]
  (assert (string? system) "Label analysis prompt requires :system text")
  (assert (vector? user-content)
          "Label analysis prompt requires :user-content vector")
  (let [request {:system system
                 :messages [{:role "user" :content (vec user-content)}]
                 :tools [label-analysis-tool]
                 :tool_choice {:type "tool" :name label-analysis-tool-name}
                 :max_tokens 900}]
    (call-anthropic-api request true)))

(defn chat-about-wines
  "Chat with AI about wine collection and wine-related topics with conversation history.
   Expects {:system-text ... :context-text ... :messages [...]} prepared by ai.core."
  [{:keys [system-text context-text messages]}]
  (assert (string? system-text) "Chat prompt requires :system-text string")
  (assert (string? context-text) "Chat prompt requires :context-text string")
  (assert (vector? messages) "Chat prompt requires :messages vector")
  (let [request
        {:system
         [{:type "text" :text system-text :cache_control {:type "ephemeral"}}
          {:type "text" :text context-text :cache_control {:type "ephemeral"}}]
         :messages messages}]
    (call-anthropic-api request false)))

(defn generate-wine-summary
  "Generates a comprehensive wine summary including taste profile and food pairings using Anthropic's Claude API.
   Expects a map with :system and :user prompt strings."
  [{:keys [system user]}]
  (assert (string? system) "Wine-summary prompt requires :system text")
  (assert (string? user) "Wine-summary prompt requires :user text")
  (let [request {:system system :messages [{:role "user" :content user}]}]
    (call-anthropic-api request false)))

(defn generate-conversation-title
  "Create a concise conversation title using Anthropic's lightweight model."
  [{:keys [system user]}]
  {:pre [(string? system) (string? user)]}
  (let [request {:system system
                 :messages [{:role "user" :content [{:type "text" :text user}]}]
                 :max_tokens 40
                 :temperature 0.2}]
    (call-anthropic-api request false light-model)))

(defn generate-report-commentary
  "Generates a report commentary using Anthropic's Claude API."
  [{:keys [system user]}]
  (assert (string? system) "Report prompt requires :system text")
  (assert (string? user) "Report prompt requires :user text")
  (let [request {:system system
                 :messages [{:role "user" :content [{:type "text" :text user}]}]
                 :max_tokens 1000
                 :temperature 0.7}]
    (call-anthropic-api request false)))
