(ns wine-cellar.db.inventory-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(defn- new-wine!
  [quantity]
  (:id (db/create-wine {:producer "ZZ Test Producer"
                        :country "France"
                        :region "Bordeaux"
                        :style "Red"
                        :quantity quantity
                        :original_quantity quantity})))

(deftest restock-raises-original-quantity
  (let [id (new-wine! 2)]
    (db/adjust-quantity id 3 {:reason "restock"})
    (let [wine (db/get-wine id false)]
      (is (= 5 (:quantity wine)))
      (is (= 5 (:original_quantity wine))))
    (testing "the history row records the new original quantity"
      (is (= 5 (:original_quantity (first (db/get-inventory-history id))))))))

(deftest drinking-leaves-original-quantity-alone
  (let [id (new-wine! 4)]
    (db/adjust-quantity id -1 {:reason "drunk"})
    (let [wine (db/get-wine id false)]
      (is (= 3 (:quantity wine)))
      (is (= 4 (:original_quantity wine))))))
