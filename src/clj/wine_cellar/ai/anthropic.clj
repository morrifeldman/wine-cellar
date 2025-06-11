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
          (or (config-utils/get-config "ANTHROPIC_MODEL")
              "claude-3-7-sonnet-20250219")) ; Default to current model

(defstate api-key :start (config-utils/get-config "ANTHROPIC_API_KEY"))

(defn- create-prompt
  "Creates a prompt for wine label analysis"
  [front-image back-image]
  (let [has-front (boolean front-image)
        has-back (boolean back-image)
        image-desc (cond (and has-front has-back) "front and back label images"
                         has-front "front label image"
                         has-back "back label image"
                         :else "wine label image")
        style-options (str/join ", " (sort common/wine-styles))
        level-options (str/join ", " (sort common/wine-levels))]
    (str
     "You are a wine expert tasked with extracting information from wine label images. "
     "Please analyze the provided "
     image-desc
     " and extract the following information in JSON format:\n\n"
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

(defn- format-wine-summary
  "Creates a formatted summary of a single wine including tasting notes and varieties"
  [wine &
   {:keys [include-quantity? bullet-prefix]
    :or {include-quantity? true bullet-prefix "- "}}]
  (tap> ["format-wine-summary" wine])
  (let [basic-info (str bullet-prefix
                        (:producer wine)
                        (when (:name wine) (str " " (:name wine)))
                        (when (:vintage wine) (str " " (:vintage wine)))
                        " (" (:style wine)
                        ", " (:country wine)
                        ", " (:region wine)
                        ")" (when (and include-quantity? (:quantity wine))
                              (str " - " (:quantity wine) " bottles")))
        classification-info
        (str/join ""
                  [(when (:aoc wine) (str "\n  AOC/AVA: " (:aoc wine)))
                   (when (:vineyard wine)
                     (str "\n  Vineyard: " (:vineyard wine)))
                   (when (:classification wine)
                     (str "\n  Classification: " (:classification wine)))
                   (when (:level wine) (str "\n  Level: " (:level wine)))])
        varieties (:varieties wine)
        varieties-summary
        (when (seq varieties)
          (str "\n  Varieties: "
               (str/join ", "
                         (map (fn [v]
                                (if (:percentage v)
                                  (str (:name v) " (" (:percentage v) "%)")
                                  (:name v)))
                              varieties))))
        tasting-notes (:tasting_notes wine)
        notes-summary (when (seq tasting-notes)
                        (str "\n  Tasting notes: "
                             (str/join "; "
                                       (map (fn [note]
                                              (str (when (:rating note)
                                                     (str (:rating note)
                                                          "/100 - "))
                                                   (:notes note)))
                                            (take 3 tasting-notes)))))]
    (str basic-info classification-info varieties-summary notes-summary)))

(defn- create-drinking-window-prompt
  "Creates a prompt for suggesting a drinking window for a wine"
  [wine]
  (let [current-year (.getValue (java.time.Year/now))
        wine-summary
        (format-wine-summary wine :include-quantity? false :bullet-prefix "")]
    (str
     "You are a wine expert tasked with suggesting an optimal drinking window for a wine. "
     "Based on the following characteristics including tasting notes and grape varieties, "
     "suggest when this wine will be ready to drink and when it might be past its prime.\n\n"
     "Wine details:\n"
     wine-summary
     "\n"
     "The current year is " current-year
     ".\n\n" "Return your response in JSON format with the following fields:\n"
     "- drink_from_year: (integer) The year when the wine will start to be enjoyable\n"
     "- drink_until_year: (integer) The year when the wine might be past its prime\n"
     "- confidence: (string) \"high\", \"medium\", or \"low\" based on how confident you are in this assessment\n"
     "- reasoning: (string) A brief explanation of your recommendation\n\n"
     "Only return a valid parseable JSON object without any additional text. "
     "Do not nest the response in a markdown code block.")))

(defn- call-anthropic-api
  "Makes a request to the Anthropic API with the given content.
   When parse-json? is true, parses response as JSON. Otherwise returns raw text."
  ([content] (call-anthropic-api content true)) ; Default to JSON parsing
                                                ; for backward
                                                ; compatibility
  ([content parse-json?]
   (let [request-body {:model model
                       :max_tokens 1000
                       :messages [{:role "user" :content content}]}]
     (tap> ["anthropic-request-body-structure" (keys request-body)])
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
  (let [prompt (create-drinking-window-prompt wine)
        content [{:type "text" :text prompt}]]
    (tap> ["suggest-window-prompt" prompt])
    (call-anthropic-api content)))

(defn analyze-wine-label
  "Analyzes wine label images using Anthropic's Claude API.
   Returns a map of extracted wine information."
  [label-image back-label-image]
  (let [images (filterv
                some?
                [(when label-image
                   {:type "image"
                    :source {:type "base64"
                             :media_type "image/jpeg"
                             :data
                             (-> label-image
                                 (str/replace #"^data:image/jpeg;base64," "")
                                 (str/replace #"^data:image/png;base64," ""))}})
                 (when back-label-image
                   {:type "image"
                    :source
                    {:type "base64"
                     :media_type "image/jpeg"
                     :data (-> back-label-image
                               (str/replace #"^data:image/jpeg;base64," "")
                               (str/replace #"^data:image/png;base64," ""))}})])
        prompt (create-prompt label-image back-label-image)
        content (into [{:type "text" :text prompt}] images)]
    (call-anthropic-api content)))

(defn- create-wine-context
  "Creates a context string from the user's wine collection including tasting notes"
  [wines]
  (tap> ["create-wine-context-wines" wines])
  (if (empty? wines)
    "The user has no wines in their collection yet."
    (let [wine-count (count wines)
          wine-summaries (take 50 ; Limit to avoid overwhelming the context
                               (map format-wine-summary wines))
          summary (str "The user has "
                       wine-count
                       " wines in their collection:\n"
                       (str/join "\n" wine-summaries)
                       (when (> wine-count 50) "\n... and more wines"))]
      (tap> ["wine cellar summary" summary])
      summary)))

(defn- create-conversation-context
  "Creates a context string from conversation history"
  [conversation-history]
  (if (empty? conversation-history)
    ""
    (let [history-text (str/join
                        "\n"
                        (map (fn [msg]
                               (str (if (:is-user msg) "User: " "Assistant: ")
                                    (:text msg)))
                             conversation-history))]
      (str "\n\nPrevious conversation:\n" history-text "\n\n"))))

(defn- create-chat-prompt
  "Creates a prompt for wine chat conversations with conversation history"
  [message wines conversation-history]
  (let [wine-context (create-wine-context wines)
        conversation-context (create-conversation-context conversation-history)]
    (str
     "You are a knowledgeable wine expert and sommelier helping someone with their wine collection. "
     "Please respond in a conversational, friendly tone. You can discuss wine recommendations, "
     "food pairings, optimal drinking windows, storage advice, or any wine-related questions. "
     "CRITICAL FORMATTING REQUIREMENT: You MUST respond in plain text only. Do NOT use any markdown formatting whatsoever. This means:\n"
     "- NO headers starting with #\n"
     "- NO bold text with **\n"
     "- NO italic text with _ or *\n"
     "- NO code blocks with ```\n"
     "- NO bullet points with -\n"
     "- NO numbered lists\n"
     "Write your response as simple, natural conversational text with normal punctuation only.\n\n"
     "Here is information about the user's wine collection:\n"
     wine-context
     conversation-context
     "Current user question: " message
     "\n\n"
     "Please provide a helpful, informative response. If this is a follow-up question, reference the previous conversation context appropriately.")))

(defn chat-about-wines
  "Chat with AI about wine collection and wine-related topics with conversation history"
  [message wines conversation-history]
  (tap> ["chat-about-wines" wines])
  (let [prompt (create-chat-prompt message wines conversation-history)
        content [{:type "text" :text prompt}]]
    (tap> ["chat-prompt" prompt])
    (call-anthropic-api content false)))
