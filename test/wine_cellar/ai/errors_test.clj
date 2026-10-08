(ns wine-cellar.ai.errors-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [wine-cellar.ai.core :as ai]
            [wine-cellar.ai.errors :as errors]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(deftest provider-failures-are-a-bad-gateway
  (let [e (errors/upstream-error "Anthropic"
                                 "API request failed"
                                 {:status 401
                                  :parsed {:error {:message
                                                   "invalid x-api-key"}}})]
    (is (= "Anthropic: invalid x-api-key" (ex-message e)))
    (is (= {:status 502 :upstream-status 401} (ex-data e)))))

(deftest a-provider-401-does-not-look-like-a-lost-session
  ;; The app sends the user to log in again on any 401.
  (with-redefs [ai/chat-about-wines
                (fn [& _]
                  (throw (errors/upstream-error
                          "Anthropic"
                          "API request failed"
                          {:status 401
                           :parsed {:error {:message "invalid x-api-key"}}})))]
    (let [{:keys [status body]} (ts/request :post
                                            "/api/chat"
                                            {:conversation-history
                                             [{:is_user true :content "Hi"}]
                                             :provider "anthropic"})]
      (is (= 502 status))
      (is (= "Anthropic: invalid x-api-key" (:error body))))))
