(ns wine-cellar.recipe-links-test
  (:require [clojure.test :refer [deftest is testing]]
            [wine-cellar.recipe-links :as links]))

(def ingredients
  [{:name "gin" :amount "2 oz" :spirit {:category "gin" :spirit_id 9}}
   {:name "lemon juice" :amount "1 oz" :inventory_item_ids [3]}
   {:name "lemon twist" :garnish true} {:name "angostura" :amount "2 dashes"}])

(deftest links-are-rebuilt-by-index
  (let [merged (links/merge-links
                ingredients
                {:ingredient_links [{:index 1 :inventory_item_ids [4 5]}
                                    {:index 3 :garnish false}]
                 :spirit_links [{:ingredient_index 0
                                 :category "gin"
                                 :subcategory "london dry"
                                 :preferred_spirit_ids [1 2]}
                                {:ingredient_index 3 :category "other"}]})]
    (testing "a fresh spirit spec replaces the old one, old spirit_id and all"
      (is (= {:category "gin"
              :subcategory "london dry"
              :preferred_spirit_ids [1 2]}
             (:spirit (merged 0)))))
    (testing "inventory links come from the result"
      (is (= [4 5] (:inventory_item_ids (merged 1)))))
    (testing "garnish stays true once either side says so"
      (is (true? (:garnish (merged 2)))))
    (testing "an unsatisfiable grab-bag spec is dropped"
      (is (not (contains? (merged 3) :spirit))))
    (testing "links the result no longer makes are cleared"
      (let [unlinked (links/merge-links ingredients {})]
        (is (= {:name "gin" :amount "2 oz"} (unlinked 0)))
        (is (= {:name "lemon juice" :amount "1 oz"} (unlinked 1)))
        (is (true? (:garnish (unlinked 2))))))))
