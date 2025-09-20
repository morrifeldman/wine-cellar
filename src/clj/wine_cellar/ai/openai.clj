(ns wine-cellar.ai.openai
  "OpenAI Responses client for wine-related chat interactions."
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.ai.prompts :as prompts]
            [wine-cellar.config-utils :as config-utils]))

(def api-url "https://api.openai.com/v1/responses")

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

(defn- message->content
  [{:keys [is-user text]}]
  (let [role (if is-user "user" "assistant")
        type (if is-user "input_text" "output_text")]
    {:role role
     :content [{:type type
                :text (or text "")}]}))

(defn- conversation->input
  [conversation-history image]
  (let [messages (map message->content conversation-history)]
    (if (and image (seq messages))
      (let [last-msg (last messages)]
        (if (= "user" (:role last-msg))
          (let [prefix (butlast messages)
                image-content {:type "input_image"
                               :image_base64 (-> image
                                                 (str/replace #"^data:image/[^;]+;base64," ""))}]
            (concat prefix
                    [(update last-msg :content conj image-content)]))
          messages))
      messages)))

(defn- build-request
  [wines conversation-history image]
  {:model model
   :input (concat
           [{:role "system"
             :content [{:type "input_text"
                        :text (prompts/wine-system-instructions)}]}
            {:role "system"
             :content [{:type "input_text"
                        :text (prompts/wine-collection-context wines)}]}]
           (conversation->input conversation-history image))
   :max_output_tokens 1000})

(defn- extract-text
  [response-body]
  (some->> (:output response-body)
           (mapcat :content)
           (filter #(= "output_text" (:type %)))
           (map :text)
           (remove str/blank?)
           (str/join "\n")))

(defn chat-about-wines
  [wines conversation-history image]
  (ensure-api-key!)
  (let [request (build-request wines conversation-history image)]
    (tap> ["openai-request" request])
    (let [{:keys [status body error]} (deref (http/post api-url
                                                       {:headers {"authorization" (str "Bearer " api-key)
                                                                  "content-type" "application/json"}
                                                        :body (json/write-value-as-string request)
                                                        :as :text
                                                        :timeout 60000}))
          parsed (when body (json/read-value body json-mapper))]
      (tap> ["openai-response" {:status status :error error :body parsed}])
      (if (= 200 status)
        (or (extract-text parsed)
            (throw (ex-info "OpenAI response missing assistant text"
                            {:status status :response parsed})))
        (throw (ex-info "OpenAI Responses API call failed"
                        {:status status :response parsed :error error}))))))
