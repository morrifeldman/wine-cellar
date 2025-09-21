(ns wine-cellar.ai.anthropic
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.ai.prompts :as prompts]
            [wine-cellar.common :as common]
            [wine-cellar.config-utils :as config-utils]))

(def api-url "https://api.anthropic.com/v1/messages")

(defstate model
          :start
          (config-utils/get-config "ANTHROPIC_MODEL"
                                   :fallback "claude-sonnet-4-20250514")) ; Default to current model

(defstate api-key :start (config-utils/get-config "ANTHROPIC_API_KEY"))

(defn- create-wine-label-system-prompt
  "Creates the system prompt for wine label analysis"
  []
  (let [style-options (str/join ", " (sort common/wine-styles))
        level-options (str/join ", " (sort common/wine-levels))]
    (str
     "You are a wine expert tasked with extracting information from wine label images. "
     "Analyze the provided wine label images and extract the following information in JSON format:\n\n"
     "- producer: The wine producer/winery name\n"
     "- name: The specific name of the wine (if different from producer)\n"
     "- vintage: The year the wine was produced (numeric, or null for non-vintage)\n"
     "- country: The country of origin\n"
     "- region: The wine region within the country\n"
     "- aoc: The appellation or controlled designation of origin (if applicable)\n"
     "- vineyard: The specific vineyard name (if mentioned)\n"
     "- classification: Any classification or quality designation\n"
     "- style: The wine style. Must be one of: "
     style-options
     "\n"
     "- level: The wine level. Must be one of: "
     level-options
     "\n"
     "- alcohol_percentage: The percentage of alcohol if it is visible" "\n\n"
     "Return ONLY a valid parseable JSON object with these fields. "
     "If you cannot determine a value, use null for that field. "
     "Do not nest the result in a markdown code block. "
     "Do not include any explanatory text outside the JSON object.")))

(defn- create-wine-label-user-message
  "Creates the user message with images for wine label analysis"
  [front-image back-image]
  (let [has-front (boolean front-image)
        has-back (boolean back-image)
        image-desc (cond (and has-front has-back) "front and back label images"
                         has-front "front label image"
                         has-back "back label image"
                         :else "wine label image")
        images
        (filterv
         some?
         [(when front-image
            {:type "image"
             :source {:type "base64"
                      :media_type "image/jpeg"
                      :data (-> front-image
                                (str/replace #"^data:image/jpeg;base64," "")
                                (str/replace #"^data:image/png;base64," ""))}})
          (when back-image
            {:type "image"
             :source {:type "base64"
                      :media_type "image/jpeg"
                      :data (-> back-image
                                (str/replace #"^data:image/jpeg;base64," "")
                                (str/replace #"^data:image/png;base64,"
                                             ""))}})])]
    (into [{:type "text" :text (str "Please analyze these " image-desc ":")}]
          images)))

(defn- format-wine-summary
  [wine & opts]
  (apply prompts/format-wine-summary wine opts))

(defn- create-drinking-window-system-prompt
  "Creates the system prompt for drinking window suggestions"
  []
  (let [current-year (.getValue (java.time.Year/now))]
    (str
     "You are a wine expert tasked with suggesting the OPTIMAL drinking window for a wine. "
     "Focus on when this wine will be at its absolute peak quality and most enjoyable, "
     "not just when it's acceptable to drink. Based on wine characteristics "
     "including tasting notes and grape varieties, suggest the ideal timeframe when "
     "this wine will express its best characteristics.\n\n"
     "The current year is " current-year
     ".\n\n" "Return your response in JSON format with the following fields:\n"
     "- drink_from_year: (integer) The year when the wine will reach optimal drinking condition\n"
     "- drink_until_year: (integer) The year when the wine will still be at peak quality (not just drinkable)\n"
     "- confidence: (string) \"high\", \"medium\", or \"low\" based on how confident you are in this assessment\n"
     "- reasoning: (string) A brief explanation of your recommendation focusing on peak quality timing, and mention the broader window when the wine remains enjoyable to drink\n\n"
     "Only return a valid parseable JSON object without any additional text. "
     "Do not nest the response in a markdown code block.")))

(defn- create-drinking-window-user-message
  "Creates the user message with wine data for drinking window suggestions"
  [wine]
  (let [wine-summary (format-wine-summary wine
                                          :include-quantity? false
                                          :bullet-prefix ""
                                          :include-drinking-window? false
                                          :include-ai-summary? false)]
    (str "Wine details:\n" wine-summary)))

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
   Returns a map with drink_from_year, drink_until_year, confidence, and reasoning."
  [wine]
  (let [system-prompt (create-drinking-window-system-prompt)
        user-message (create-drinking-window-user-message wine)
        request {:system system-prompt
                 :messages [{:role "user" :content user-message}]}]
    (tap> ["suggest-window-request" request])
    (call-anthropic-api request true)))

(defn analyze-wine-label
  "Analyzes wine label images using Anthropic's Claude API.
   Returns a map of extracted wine information."
  [label-image back-label-image]
  (let [system-prompt (create-wine-label-system-prompt)
        user-message (create-wine-label-user-message label-image
                                                     back-label-image)
        request {:system system-prompt
                 :messages [{:role "user" :content user-message}]}]
    (call-anthropic-api request true)))

(defn- create-wine-context
  "Creates a context string from the user's wine collection including tasting notes"
  [wines]
  (prompts/create-wine-context wines))

(defn- create-conversation-messages
  "Converts conversation history to proper message array for Anthropic API.
   Optionally adds image to the latest user message."
  ([conversation-history]
   (create-conversation-messages conversation-history nil))
  ([conversation-history image]
   (let [messages (map (fn [msg]
                         {:role (if (:is-user msg) "user" "assistant")
                          :content (:text msg)})
                       conversation-history)]
     (if (and image (seq messages) (= "user" (:role (last messages))))
       ;; Add image to the last user message
       (let [last-message (last messages)
             other-messages (butlast messages)
             image-content
             [{:type "text" :text (:content last-message)}
              {:type "image"
               :source {:type "base64"
                        :media_type "image/jpeg"
                        :data (-> image
                                  (str/replace #"^data:image/jpeg;base64," "")
                                  (str/replace #"^data:image/png;base64,"
                                               ""))}}]]
         (concat other-messages [{:role "user" :content image-content}]))
       messages))))

(defn- create-system-instructions
  []
  (prompts/wine-system-instructions))

(defn- create-wine-collection-context
  [wines]
  (prompts/wine-collection-context wines))

(defn- create-chat-messages
  "Creates the properly structured request for Anthropic API with two-part system caching.
   Optionally includes image in the latest user message."
  ([wines conversation-history]
   (create-chat-messages wines conversation-history nil))
  ([wines conversation-history image]
   (let [system-instructions (create-system-instructions)
         wine-collection-context (create-wine-collection-context wines)
         conversation-messages
         (create-conversation-messages conversation-history image)]
     ;; Return properly structured request with two-part
     ;; system: instructions + wine collection
     {:system [{:type "text"
                :text system-instructions
                :cache_control {:type "ephemeral"}}
               {:type "text"
                :text wine-collection-context
                :cache_control {:type "ephemeral"}}]
      :messages conversation-messages})))

(defn chat-about-wines
  "Chat with AI about wine collection and wine-related topics with conversation history.
   Uses prompt caching to reduce token costs - Anthropic automatically handles cache hits/misses.
   Optionally supports image input for wine label or wine list analysis."
  ([wines conversation-history]
   (chat-about-wines wines conversation-history nil))
  ([wines conversation-history image]
   (let [messages (create-chat-messages wines conversation-history image)]
     (tap> ["chat-messages" messages])
     (call-anthropic-api messages false))))

(defn- create-wine-summary-system-prompt
  "Creates the system prompt for wine summary generation"
  []
  (str
   "You are a wine expert tasked with creating a concise wine summary including taste profile and food pairing recommendations. "
   "Based on wine details provided, create an informative but brief summary that would be helpful to someone deciding whether to drink this wine or what to pair it with.\n\n"
   "Please provide a concise summary (2-3 paragraphs maximum) that includes:\n"
   "1. Overall style and key taste characteristics\n"
   "2. Top 3-4 food pairing suggestions\n"
   "3. Basic serving recommendations\n\n"
   "Write in a conversational, informative tone. Keep it concise and practical. "
   "Focus on the most important information for enjoyment and pairing decisions. "
   "Return only the summary text without any formatting or structure markers."))

(defn- create-wine-summary-user-message
  "Creates the user message with wine data for summary generation"
  [wine]
  (let [wine-details (format-wine-summary wine
                                          :include-quantity? false
                                          :bullet-prefix ""
                                          :include-ai-summary? false)]
    (str "Wine details:\n" wine-details)))

(defn generate-wine-summary
  "Generates a comprehensive wine summary including taste profile and food pairings using Anthropic's Claude API"
  [wine]
  (let [system-prompt (create-wine-summary-system-prompt)
        user-message (create-wine-summary-user-message wine)
        request {:system system-prompt
                 :messages [{:role "user" :content user-message}]}]
    (tap> ["wine-summary-request" request])
    (call-anthropic-api request false)))
