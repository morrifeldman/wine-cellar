(ns wine-cellar.db.conversation-search-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(def email "search-test@example.com")

(defn- conversation-with!
  [& contents]
  (let [{:keys [id]} (db/create-conversation! {:user_email email
                                               :provider :anthropic})]
    (doseq [c contents]
      (db/append-conversation-message!
       {:conversation_id id :is_user true :content c}))
    id))

(defn- search [text] (db/list-conversations-for-user email text))

(deftest search-counts-literal-matches
  (let [id (conversation-with! "O'Neill's (big) red? Yes."
                               "Another O'Neill's (big) red? bottle")]
    (testing "quotes and regex characters are matched literally"
      (let [hit (first (filter #(= id (:id %))
                               (search "O'Neill's (big) red?")))]
        (is hit)
        (is (= 2 (:match_count hit)))))
    (testing "text that would break out of a string literal is just text"
      (is (empty? (filter #(= id (:id %))
                          (search "'); DROP TABLE wines; --")))))))
