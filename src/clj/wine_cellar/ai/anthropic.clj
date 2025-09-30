(ns wine-cellar.ai.anthropic
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.config-utils :as config-utils]))

(def api-url "https://api.anthropic.com/v1/messages")

(defstate model
          :start
          (config-utils/get-config "ANTHROPIC_MODEL"
                                   :fallback "claude-sonnet-4-20250514")) ; Default to current model

(defstate api-key :start (config-utils/get-config "ANTHROPIC_API_KEY"))

(def drinking-window-tool-name "record_drinking_window")

(def drinking-window-tool
  {:name drinking-window-tool-name
   :description "Return the optimal drinking window as structured JSON."
   :input_schema {:type "object"
                  :properties {:drink_from_year {:type "integer"
                                                 :description "Year the wine reaches optimal drinking."}
                               :drink_until_year {:type "integer"
                                                  :description "Last year the wine should be enjoyed."}
                               :confidence {:type "string"
                                            :description "Short description of confidence in the window."}
                               :reasoning {:type "string"
                                           :description "Concise rationale for the suggested window."}}
                  :required [:drink_from_year :drink_until_year :confidence :reasoning]
                  :additionalProperties false}})

(def label-analysis-tool-name "record_wine_label")

(def label-analysis-tool
  {:name label-analysis-tool-name
   :description "Extract structured wine label details as JSON."
   :input_schema {:type "object"
                  :properties {:producer {:type ["string" "null"]
                                          :description "Producer or winery name."}
                               :name {:type ["string" "null"]
                                      :description "Specific wine name."}
                               :vintage {:type ["integer" "null"]
                                         :description "Vintage year or null for NV."}
                               :country {:type ["string" "null"]
                                          :description "Country of origin."}
                               :region {:type ["string" "null"]
                                         :description "Region within the country."}
                               :aoc {:type ["string" "null"]
                                     :description "Appellation or controlled designation."}
                               :vineyard {:type ["string" "null"]
                                          :description "Specific vineyard if stated."}
                               :classification {:type ["string" "null"]
                                                :description "Quality classification or designation."}
                               :style {:type ["string" "null"]
                                       :description "Wine style."}
                               :level {:type ["string" "null"]
                                       :description "Wine level."}
                               :alcohol_percentage {:type ["number" "null"]
                                                    :description "ABV percentage if shown."}}
                  :required [:producer :name :vintage :country :region :aoc :vineyard
                             :classification :style :level :alcohol_percentage]
                  :additionalProperties false}})

(defn- build-request-body
  [{:keys [system messages tools tool_choice max_tokens temperature metadata stop_sequences]}
   model-override]
  (-> {:model model-override
       :max_tokens (or max_tokens 1000)}
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
     (try
       (let [{:keys [status body error] :as response}
             (deref (http/post api-url
                               {:body (json/write-value-as-string request-body)
                                :headers {"x-api-key" api-key
                                          "anthropic-version" "2023-06-01"
                                          "content-type" "application/json"}
                                :as :text
                                :keepalive 60000
                                :timeout 60000}))
             parsed-body (json/read-value body json/keyword-keys-object-mapper)
             content (:content parsed-body)]
         (tap> ["anthropic-response" "status" status "body" body "parsed-body"
                parsed-body "error" error])
         (if (= 200 status)
           (let [text-content (extract-text-content content)]
             (tap> ["anthropic-text-content" text-content])
             (if parse-json?
               (try (let [parsed-response (parse-json-content content)]
                      (when-not parsed-response
                        (throw (ex-info "Anthropic response missing JSON payload"
                                        {:response parsed-body})))
                      (tap> ["anthropic-parsed-response" parsed-response])
                      parsed-response)
                    (catch Exception e
                      (throw (ex-info "Failed to parse AI response as JSON"
                                      {:error (.getMessage e)
                                       :response response}
                                     e))))
               text-content))
           (throw (ex-info "Anthropic API request failed"
                           {:status status :body body}))))
       (catch java.net.SocketTimeoutException e
         (tap> ["anthropic-timeout-error" (.getMessage e)])
         (throw (ex-info "Anthropic API request timed out"
                         {:error (.getMessage e)})))
       (catch java.net.ConnectException e
         (tap> ["anthropic-connection-error" (.getMessage e)])
         (throw (ex-info "Failed to connect to Anthropic API"
                         {:error (.getMessage e)})))
       (catch Exception e
         (tap> ["anthropic-unexpected-error" (.getMessage e) (type e)])
         (throw (ex-info "Unexpected error calling Anthropic API"
                         {:error (.getMessage e)})))))))

(defn suggest-drinking-window
  "Suggests an optimal drinking window for a wine using Anthropic's Claude API.
   Expects a map with :system and :user prompt strings."
  [{:keys [system user]}]
  (assert (string? system) "Drinking-window prompt requires :system text")
  (assert (string? user) "Drinking-window prompt requires :user text")
  (let [request {:system system
                 :messages [{:role "user"
                             :content [{:type "text" :text user}]}]
                 :tools [drinking-window-tool]
                 :tool_choice {:type "tool" :name drinking-window-tool-name}
                 :max_tokens 600}]
    (tap> ["suggest-window-request" request])
    (call-anthropic-api request true)))

(defn analyze-wine-label
  "Analyzes wine label images using Anthropic's Claude API.
   Expects {:system string :user-content vector-of-content}."
  [{:keys [system user-content]}]
  (assert (string? system) "Label analysis prompt requires :system text")
  (assert (vector? user-content) "Label analysis prompt requires :user-content vector")
  (let [request {:system system
                 :messages [{:role "user"
                             :content (vec user-content)}]
                 :tools [label-analysis-tool]
                 :tool_choice {:type "tool" :name label-analysis-tool-name}
                 :max_tokens 900}]
    (tap> ["label-analysis-request" request])
    (call-anthropic-api request true)))

(defn chat-about-wines
  "Chat with AI about wine collection and wine-related topics with conversation history.
   Expects {:system-text ... :context-text ... :messages [...]} prepared by ai.core."
  [{:keys [system-text context-text messages]}]
  (assert (string? system-text) "Chat prompt requires :system-text string")
  (assert (string? context-text) "Chat prompt requires :context-text string")
  (assert (vector? messages) "Chat prompt requires :messages vector")
  (let [request {:system [{:type "text"
                           :text system-text
                           :cache_control {:type "ephemeral"}}
                          {:type "text"
                           :text context-text
                           :cache_control {:type "ephemeral"}}]
                 :messages messages}]
    (tap> ["chat-messages" request])
    (call-anthropic-api request false)))

(defn generate-wine-summary
  "Generates a comprehensive wine summary including taste profile and food pairings using Anthropic's Claude API.
   Expects a map with :system and :user prompt strings."
  [{:keys [system user]}]
  (assert (string? system) "Wine-summary prompt requires :system text")
  (assert (string? user) "Wine-summary prompt requires :user text")
  (let [request {:system system
                 :messages [{:role "user" :content user}]}]
    (tap> ["wine-summary-request" request])
    (call-anthropic-api request false)))
