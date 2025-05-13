(ns wine-cellar.ai.anthropic
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.common :as common]
            [wine-cellar.config-utils :as config-utils]))

(def api-url "https://api.anthropic.com/v1/messages")

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
     "- alcohol_percentage: The percentage of alcohol if it is visible"
     "\n\n"
     "Return ONLY a valid JSON object with these fields. If you cannot determine a value, use null for that field. "
     "Do not include any explanatory text outside the JSON object.")))

(defn- create-drinking-window-prompt
  "Creates a prompt for suggesting a drinking window for a wine"
  [wine]
  (let [{:keys [producer name vintage country region style classification
                vineyard aoc level]}
        wine
        current-year (.getValue (java.time.Year/now))]
    (str
     "You are a wine expert tasked with suggesting an optimal drinking window for a wine. "
     "Based on the following characteristics, suggest when this wine will be ready to drink and when it might be past its prime.\n\n"
     "Wine details:\n"
     (when producer (str "- Producer: " producer "\n"))
     (when name (str "- Name: " name "\n"))
     (when vintage (str "- Vintage: " vintage "\n"))
     (when country (str "- Country: " country "\n"))
     (when region (str "- Region: " region "\n"))
     (when aoc (str "- AOC/AVA: " aoc "\n"))
     (when vineyard (str "- Vineyard: " vineyard "\n"))
     (when style (str "- Style: " style "\n"))
     (when classification (str "- Classification: " classification "\n"))
     (when level (str "- Level: " level "\n"))
     "\n"
     "The current year is "
     current-year
     ".\n\n"
     "Return your response in JSON format with the following fields:\n"
     "- drink_from_year: (integer) The year when the wine will start to be enjoyable\n"
     "- drink_until_year: (integer) The year when the wine might be past its prime\n"
     "- confidence: (string) \"high\", \"medium\", or \"low\" based on how confident you are in this assessment\n"
     "- reasoning: (string) A brief explanation of your recommendation\n\n"
     "Only return valid JSON without any additional text.")))

(defn- call-anthropic-api
  "Makes a request to the Anthropic API with the given content.
   Returns the parsed JSON response."
  [content]
  (let [request-body {:model "claude-3-5-haiku-20241022"
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
                               :keepalive 30000
                               :timeout 30000}))
            parsed-body (json/read-value body json/keyword-keys-object-mapper)]
        (tap> ["anthropic-response" "status" status "body" body "parsed-body"
               parsed-body "error" error])
        (if (= 200 status)
          (try (-> (get-in parsed-body [:content 0 :text])
                   (json/read-value json/keyword-keys-object-mapper))
               (catch Exception e
                 (throw (ex-info "Failed to parse AI response as JSON"
                                 {:error (.getMessage e) :response response}))))
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
                        {:error (.getMessage e)}))))))

(defn suggest-drinking-window
  "Suggests an optimal drinking window for a wine using Anthropic's Claude API.
   Returns a map with drink_from_year, drink_until_year, confidence, and reasoning."
  [wine]
  (let [prompt (create-drinking-window-prompt wine)
        content [{:type "text" :text prompt}]]
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
