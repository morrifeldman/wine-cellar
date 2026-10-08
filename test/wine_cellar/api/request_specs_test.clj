(ns wine-cellar.api.request-specs-test
  "Request bodies are validated against their specs. These specs were once
   referenced without being defined, so only the keys' presence was checked."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(defn- new-wine!
  []
  (:id (db/create-wine {:producer "ZZ Spec Test"
                        :country "France"
                        :region "Loire"
                        :style "White"
                        :quantity 3})))

(deftest adjust-quantity-body
  (let [url (str "/api/wines/by-id/" (new-wine!) "/adjust-quantity")]
    (testing "a numeric adjustment is accepted"
      (is (= 200 (:status (ts/request :post url {:adjustment -1})))))
    (testing "a non-numeric adjustment is rejected with an explanation"
      (let [{:keys [status body]} (ts/request :post url {:adjustment "two"})]
        (is (= 400 status))
        (is (re-find #"adjustment" (:details body)))))))

(deftest conversation-message-body
  (let [{conversation :body}
        (ts/request :post "/api/conversations" {:provider "anthropic"})
        url (str "/api/conversations/" (:id conversation) "/messages")]
    ;; An AI message, so the handler doesn't ask a model for a title.
    (testing "a well-formed message is accepted"
      (is (= 201
             (:status (ts/request :post url {:is_user false :content "Hi"})))))
    (testing "is_user must be a boolean"
      (is (= 400
             (:status (ts/request :post url {:is_user "yes" :content "Hi"})))))
    (testing "content must be a string"
      (is (= 400
             (:status (ts/request :post url {:is_user true :content 7})))))))
