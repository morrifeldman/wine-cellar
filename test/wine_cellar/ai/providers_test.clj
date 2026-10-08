(ns wine-cellar.ai.providers-test
  "The providers against a stubbed HTTP client: what each sends, and that
   each one's reply comes back as the same data."
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [org.httpkit.client :as http]
            [wine-cellar.ai.anthropic :as anthropic]
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
