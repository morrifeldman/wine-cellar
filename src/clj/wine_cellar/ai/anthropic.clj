(ns wine-cellar.ai.anthropic
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [wine-cellar.config-utils :as config-utils]
            [wine-cellar.common :as common]))

(def ^:private api-url "https://api.anthropic.com/v1/messages")

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
     "- classification: Any classification or quality designation\n"
     "- style: The wine style. Must be one of: "
     style-options
     "\n"
     "- level: The wine level. Must be one of: "
     level-options
     "\n\n"
     "Return ONLY a valid JSON object with these fields. If you cannot determine a value, use null for that field. "
     "Do not include any explanatory text outside the JSON object.")))

#_(create-prompt 1 2)

(defn analyze-wine-label
  "Analyzes wine label images using Anthropic's Claude API.
   Returns a map of extracted wine information."
  [label-image back-label-image]
  (if-let [api-key (config-utils/get-anthropic-api-key)]
    (let [_ (tap> ["anthropic-api-key-exists" (boolean api-key)])
          images (filterv
                  some?
                  [(when label-image
                     {:type "image"
                      :source
                      {:type "base64"
                       :media_type "image/jpeg"
                       :data (-> label-image
                                 (str/replace #"^data:image/jpeg;base64," "")
                                 (str/replace #"^data:image/png;base64," ""))}})
                   (when back-label-image
                     {:type "image"
                      :source {:type "base64"
                               :media_type "image/jpeg"
                               :data
                               (-> back-label-image
                                   (str/replace #"^data:image/jpeg;base64," "")
                                   (str/replace #"^data:image/png;base64,"
                                                ""))}})])
          prompt (create-prompt label-image back-label-image)
          request-body {:model "claude-3-opus-20240229"
                        :max_tokens 1000
                        :messages [{:role "user"
                                    :content (into [{:type "text" :text prompt}]
                                                   images)}]}]
      (tap> ["anthropic-request-body-structure" (keys request-body)])
      (try (let [response (http/post api-url
                                     {:body (json/generate-string request-body)
                                      :headers {"x-api-key" api-key
                                                "anthropic-version" "2023-06-01"
                                                "content-type"
                                                "application/json"}
                                      :as :json
                                      :throw-exceptions true
                                      :socket-timeout 30000
                                      :conn-timeout 30000})]
             (tap> ["anthropic-response-status" (:status response)])
             (if (= 200 (:status response))
               (let [content (get-in response [:body :content 0 :text])
                     ;; Extract JSON from the response
                     json-str (or (second (re-find #"(?s)```json\s*(.*?)\s*```"
                                                   content))
                                  (second (re-find #"(?s)\{(.*)\}" content))
                                  content)]
                 (try
                   ;; Parse the JSON response
                   (json/parse-string (if (str/starts-with? json-str "{")
                                        json-str
                                        (str "{" json-str "}"))
                                      true)
                   (catch Exception e
                     (throw (ex-info "Failed to parse AI response as JSON"
                                     {:error (.getMessage e)
                                      :response content})))))
               (throw (ex-info "Anthropic API request failed"
                               {:status (:status response)
                                :body (:body response)}))))
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
                             {:error (.getMessage e)})))))
    (throw (ex-info "ANTHROPIC_API_KEY environment variable not set" {}))))
