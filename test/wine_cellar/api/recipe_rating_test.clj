(ns wine-cellar.api.recipe-rating-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(defn- create-recipe
  [rating]
  (ts/request :post
              "/api/cocktail-recipes"
              {:name "ZZ Test Sour"
               :ingredients [{:name "lemon juice" :amount "1 oz"}]
               :rating rating}))

(deftest recipe-rating-is-one-to-ten
  (testing "the database allows 1-10, so the API should too"
    (is (= 201 (:status (create-recipe 8))))
    (is (= 400 (:status (create-recipe 50))))
    (is (= 400 (:status (create-recipe 0))))))
