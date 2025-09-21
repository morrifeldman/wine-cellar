(ns wine-cellar.ai.anthropic
  (:require [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.config-utils :as config-utils]))

(def api-url "https://api.anthropic.com/v1/messages")

(defstate model
          :start
          (config-utils/get-config "ANTHROPIC_MODEL"
                                   :fallback "claude-sonnet-4-20250514")) ; Default to current model

(defstate api-key :start (config-utils/get-config "ANTHROPIC_API_KEY"))

(defn call-anthropic-api
  "Makes a request to the Anthropic API with messages array and optional JSON parsing"
  ([messages] (call-anthropic-api messages false))
  ([messages parse-json?] (call-anthropic-api messages parse-json? model))
  ([messages parse-json? model-override]
   (let [request-body {:model model-override
                       :max_tokens 1000
                       :system (:system messages)
                       :messages (:messages messages)}]
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
             parsed-body (json/read-value body json/keyword-keys-object-mapper)]
         (tap> ["anthropic-response" "status" status "body" body "parsed-body"
                parsed-body "error" error])
         (if (= 200 status)
           (let [text-content (get-in parsed-body [:content 0 :text])]
             (tap> ["anthropic-text-content" text-content])
             (if parse-json?
               (try (let [parsed-response (json/read-value
                                           text-content
                                           json/keyword-keys-object-mapper)]
                      (tap> ["anthropic-parsed-response" parsed-response])
                      parsed-response)
                    (catch Exception e
                      (throw (ex-info "Failed to parse AI response as JSON"
                                      {:error (.getMessage e)
                                       :response response}))))
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
                 :messages [{:role "user" :content user}]}]
    (tap> ["suggest-window-request" request])
    (call-anthropic-api request true)))

(defn analyze-wine-label
  "Analyzes wine label images using Anthropic's Claude API.
   Expects {:system string :user-content vector-of-content}."
  [{:keys [system user-content]}]
  (assert (string? system) "Label analysis prompt requires :system text")
  (assert (vector? user-content) "Label analysis prompt requires :user-content vector")
  (let [request {:system system
                 :messages [{:role "user" :content user-content}]}]
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
