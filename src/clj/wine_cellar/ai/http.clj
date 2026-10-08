(ns wine-cellar.ai.http
  "The one way the AI providers talk to their APIs: post JSON, decode JSON,
  retry once when the failure is the provider's (rate limit, overload, a
  dropped connection), and turn every other failure into the same
  upstream-error."
  (:require [jsonista.core :as json]
            [org.httpkit.client :as http]
            [wine-cellar.ai.errors :as errors]))

(def timeout-ms
  "Generous because a chat turn with web search or fetch runs tools on the
  provider's side before it answers."
  300000)

(def ^:private retry-delay-ms 1500)

(defn- retryable?
  [{:keys [status error]}]
  (boolean (or error (= 429 status) (and status (<= 500 status 599)))))

(defn- send!
  [url headers body]
  (let [response @(http/post url
                             {:headers
                              (assoc headers "content-type" "application/json")
                              :body (json/write-value-as-string body)
                              :as :text
                              :keepalive 60000
                              :timeout timeout-ms})
        parsed (when-let [b (:body response)]
                 (try (json/read-value b json/keyword-keys-object-mapper)
                      (catch Exception _ b)))]
    (assoc (select-keys response [:status :error]) :parsed parsed)))

(defn post-json!
  "Posts body to url and returns the decoded 200 reply. provider names the
  service in errors. Never returns or throws the raw http-kit response: its
  request options hold the API key."
  [provider url headers body]
  (let [first-try (send! url headers body)
        {:keys [status error parsed] :as response}
        (if (retryable? first-try)
          (do (tap> [(str provider " retrying") (dissoc first-try :parsed)])
              (Thread/sleep retry-delay-ms)
              (send! url headers body))
          first-try)]
    (when error
      (throw
       (errors/upstream-error provider "API request failed" {:cause error})))
    (when (not= 200 status)
      (tap> [(str provider " API call failed") (dissoc response :error)])
      (throw (errors/upstream-error provider
                                    "API request failed"
                                    {:status status :parsed parsed})))
    parsed))

(defn bad-reply
  "The API answered 200 but the reply is unusable (cut short, refused, empty,
  not JSON). The explicit :status keeps a handler from passing that 200 on."
  [message]
  (ex-info message {:status 502 :error message}))
