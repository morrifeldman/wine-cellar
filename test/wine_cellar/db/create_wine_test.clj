(ns wine-cellar.db.create-wine-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(defn- classification-for
  [region]
  (some #(when (= region (:region %)) %) (db/get-classifications)))

(deftest wine-and-classification-are-created-together
  (testing "a new region is added to the classifications"
    (let [{:keys [status body]} (ts/request :post
                                            "/api/wines"
                                            {:producer "ZZ Atomic"
                                             :country "France"
                                             :region "ZZ Region One"
                                             :style "Red"
                                             :quantity 1
                                             :label_image
                                             "data:image/jpeg;base64,AAEC"})]
      (is (= 201 status))
      (is (classification-for "ZZ Region One"))
      (testing "and the response carries images as data URLs, as reads do"
        (is (= "data:image/jpeg;base64,AAEC" (:label_image body))))))
  (testing "a failed wine insert leaves no classification behind"
    ;; quantity is NOT NULL; the request spec would catch this, so go
    ;; straight to the db function.
    (is (thrown? Exception
                 (db/create-wine-with-classification! {:producer "ZZ Atomic"
                                                       :country "France"
                                                       :region "ZZ Region Two"
                                                       :style "Red"
                                                       :quantity nil})))
    (is (nil? (classification-for "ZZ Region Two")))))
