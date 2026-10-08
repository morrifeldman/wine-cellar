(ns wine-cellar.logging-test
  (:require [clojure.test :refer [deftest is]]
            [wine-cellar.logging :as logging]))

(deftest long-strings-are-shortened-anywhere-in-a-value
  (let [image (str "data:image/jpeg;base64," (apply str (repeat 5000 "A")))
        logged (logging/shorten-for-log
                ["anthropic-request-body"
                 {:messages [{:content [{:type "text" :text "hi"}
                                        {:type "image" :data image}]}]}])
        shortened (get-in logged [1 :messages 0 :content 1 :data])]
    (is (< (count shortened) 120))
    (is (re-find #"<5023 chars>$" shortened))
    (is (= "hi" (get-in logged [1 :messages 0 :content 0 :text])))))
