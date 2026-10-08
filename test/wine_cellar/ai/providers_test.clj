(ns wine-cellar.ai.providers-test
  "The providers against a stubbed HTTP client: what each sends, and that
   each one's reply comes back as the same data."
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [org.httpkit.client :as http]
            [wine-cellar.ai.anthropic :as anthropic]
            [wine-cellar.ai.core]
            [wine-cellar.ai.gemini :as gemini]
            [wine-cellar.ai.openai :as openai]))

(def ^:private reply-json {:drink_from_year 2026 :drink_until_year 2034})

(defn- canned
  "The body each provider's API answers with, wrapping text."
  [provider text]
  (case provider
    :anthropic {:content [{:type "text" :text text}] :stop_reason "end_turn"}
    :openai {:status "completed"
             :output [{:content [{:type "output_text" :text text}]}]}
    :gemini {:candidates [{:content {:parts [{:text text}]}
                           :finishReason "STOP"}]}))

(defn- call
  "Runs f against a stub answering with the provider's canned reply. Returns
   [result sent-body]."
  ([provider f]
   (call provider
         f
         {:status 200
          :body (canned provider (json/write-value-as-string reply-json))}))
  ([provider f response]
   (let [sent (atom nil)]
     (with-redefs [http/post (fn [_url opts]
                               (reset! sent (json/read-value
                                             (:body opts)
                                             json/keyword-keys-object-mapper))
                               (doto (promise)
                                 (deliver (update response
                                                  :body
                                                  json/write-value-as-string))))
                   anthropic/api-key "k"
                   anthropic/model "claude-test"
                   anthropic/small-model "claude-small"
                   anthropic/default-effort "low"
                   openai/api-key "k"
                   openai/model "gpt-test"
                   openai/small-model "gpt-small"
                   gemini/api-key "k"
                   gemini/model "gemini-test"
                   gemini/small-model "gemini-small"]
       [(f) @sent]))))

(def ^:private window-prompt {:system "s" :user "u"})
(def ^:private label-prompt
  {:system "s" :user-content [{:type "text" :text "label"}]})

(deftest drinking-window-parses-the-same-everywhere
  (doseq [[provider f] [[:anthropic anthropic/suggest-drinking-window]
                        [:openai openai/suggest-drinking-window]
                        [:gemini gemini/suggest-drinking-window]]]
    (testing (name provider)
      (is (= reply-json (first (call provider #(f window-prompt))))))))

(deftest one-schema-in-each-dialect
  (testing "Anthropic: strict JSON Schema, nullable fields typed [t null]"
    (let [schema (get-in (second (call :anthropic
                                       #(anthropic/analyze-spirit-label
                                         label-prompt)))
                         [:output_config :format :schema])]
      (is (= ["string" "null"] (get-in schema [:properties :category :type])))
      (is (some #{"vermouth"} (get-in schema [:properties :category :enum])))
      (is (false? (:additionalProperties schema)))))
  (testing "OpenAI: the same schema, named, strict"
    (let [fmt (get-in (second (call :openai
                                    #(openai/analyze-wine-label label-prompt)))
                      [:text :format])]
      (is (= "WineLabelAnalysis" (:name fmt)))
      (is (true? (:strict fmt)))
      (is (string? (get-in fmt [:schema :properties :region :description])))))
  (testing "Gemini: upper-case types with a nullable flag"
    (let [schema (get-in (second (call :gemini
                                       #(gemini/analyze-spirit-label
                                         label-prompt)))
                         [:generationConfig :response_schema])]
      (is (= "STRING" (get-in schema [:properties :category :type])))
      (is (true? (get-in schema [:properties :category :nullable])))
      (is (some #{"vermouth"} (get-in schema [:properties :category :enum]))))))

(deftest dispatch-by-provider
  (testing "an unknown provider is the caller's mistake"
    (let [e (try (wine-cellar.ai.core/generate-report-commentary :nope {})
                 nil
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (= 400 (:status (ex-data e))))))
  (testing "no provider means the default one"
    (with-redefs [wine-cellar.ai.core/default-provider :gemini
                  gemini/generate-report-commentary (constantly "from gemini")]
      (is (= "from gemini"
             (wine-cellar.ai.core/generate-report-commentary nil {}))))))

(defn- call-seq
  "Like call, but the stub answers with each response in turn. Returns
   [result-or-exception number-of-requests]."
  [f responses]
  (let [remaining (atom responses)
        sent (atom 0)]
    (with-redefs [http/post
                  (fn [_url _opts]
                    (swap! sent inc)
                    (let [r (first @remaining)]
                      (swap! remaining rest)
                      (doto (promise)
                        (deliver (update r :body json/write-value-as-string)))))
                  wine-cellar.ai.http/retry-delay-ms 0
                  anthropic/api-key "k"
                  anthropic/model "claude-test"
                  anthropic/default-effort "low"
                  openai/api-key "k"
                  openai/model "gpt-test"
                  gemini/api-key "k"
                  gemini/model "gemini-test"]
      [(try (f) (catch clojure.lang.ExceptionInfo e e)) @sent])))

(def ^:private ok-window (json/write-value-as-string reply-json))

(deftest one-retry-on-a-provider-side-failure
  (testing "a 429 then a success"
    (let [[result n] (call-seq
                      #(openai/suggest-drinking-window window-prompt)
                      [{:status 429 :body {:error {:message "slow down"}}}
                       {:status 200 :body (canned :openai ok-window)}])]
      (is (= reply-json result))
      (is (= 2 n))))
  (testing "two 5xx in a row give up as a 502"
    (let [[e n] (call-seq #(gemini/suggest-drinking-window window-prompt)
                          [{:status 503 :body {:error {:message "overloaded"}}}
                           {:status 503
                            :body {:error {:message "overloaded"}}}])]
      (is (= 502 (:status (ex-data e))))
      (is (= "Gemini: overloaded" (ex-message e)))
      (is (= 2 n))))
  (testing "a 400 is not retried"
    (let [[e n] (call-seq #(anthropic/suggest-drinking-window window-prompt)
                          [{:status 400
                            :body {:error {:message "bad request"}}}])]
      (is (= 502 (:status (ex-data e))))
      (is (= 1 n)))))

(deftest a-cut-short-reply-is-not-an-answer
  (testing "OpenAI incomplete"
    (let [[e] (call-seq #(openai/generate-wine-summary window-prompt)
                        [{:status 200
                          :body {:status "incomplete"
                                 :incomplete_details {:reason
                                                      "max_output_tokens"}
                                 :output [{:content [{:type "output_text"
                                                      :text "Half a"}]}]}}])]
      (is (= 502 (:status (ex-data e))))
      (is (re-find #"cut short" (ex-message e)))))
  (testing "Gemini MAX_TOKENS"
    (let [[e] (call-seq #(gemini/generate-wine-summary window-prompt)
                        [{:status 200
                          :body {:candidates [{:content {:parts [{:text
                                                                  "Half a"}]}
                                               :finishReason "MAX_TOKENS"}]}}])]
      (is (= 502 (:status (ex-data e))))
      (is (re-find #"ran out of room" (ex-message e))))))
