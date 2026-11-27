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

(defn- wine [id] (db/get-wine id false))

(deftest adjusting-returns-the-wine-and-refuses-negative-stock
  (let [id (new-wine! 1)]
    (is (= 0 (:quantity (db/adjust-quantity id -1 {:reason "drunk"}))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"negative"
                          (db/adjust-quantity id -1 {:reason "drunk"})))
    (is (= 0 (:quantity (wine id))))
    (is (= 1 (count (db/get-inventory-history id))))))

(deftest coravin-pours-open-and-finish-a-bottle
  (let [id (new-wine! 2)]
    (testing "the first pour opens a bottle without using one up"
      (let [w (db/coravin-pour id 5 {:notes "ZZ first"})]
        (is (= 2 (:quantity w)))
        (is (some? (:open_bottle_opened_at w)))
        (is (= 5.0 (double (:open_bottle_oz_poured w))))))
    (testing "pours add up until the bottle (25.4 oz) is empty"
      (db/coravin-pour id 10)
      (let [w (db/coravin-pour id 11)]
        (is (= 1 (:quantity w)))
        (is (nil? (:open_bottle_opened_at w)))
        (is (= ["drunk" "coravin_pour" "coravin_pour" "coravin_pour"]
               (map :reason (db/get-inventory-history id))))))
    (testing "finishing an open bottle by hand"
      (db/coravin-pour id 3)
      (let [w (db/finish-open-bottle id {})]
        (is (= 0 (:quantity w)))
        (is (nil? (:open_bottle_opened_at w))))
      (is (nil? (db/finish-open-bottle id {})) "nothing open any more"))
    (testing "no pouring from an empty cellar"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"No bottles"
                            (db/coravin-pour id 2))))))

(deftest history-records-can-be-corrected-and-deleted
  (let [id (new-wine! 3)
        _ (db/adjust-quantity id -1 {:reason "drunk"})
        row (first (db/get-inventory-history id))]
    (testing "changing the amount moves the wine's quantity by the difference"
      (db/update-inventory-history! (:id row) {:change_amount -2})
      (is (= 1 (:quantity (wine id)))))
    (is (true? (db/delete-inventory-history! (:id row))))
    (is (false? (db/delete-inventory-history! (:id row))) "already gone")))

(deftest bottles-go-out-of-the-cellar-and-back
  (let [id (new-wine! 3)]
    (testing "taking out keeps the bottle in stock"
      (let [w (db/take-out-of-cellar id 2)]
        (is (= 3 (:quantity w)))
        (is (= 2 (:out_quantity w)))))
    (testing "it can go back to the cellar"
      (is (= 1 (:out_quantity (db/take-out-of-cellar id -1)))))
    (testing "drinking takes a bottle already out first"
      (let [w (db/adjust-quantity id -1 {:reason "drunk"})]
        (is (= 2 (:quantity w)))
        (is (= 0 (:out_quantity w)))))
    (testing "moves are logged without changing stock"
      (is (= ["drunk" "put_back" "taken_out"]
             (map :reason (db/get-inventory-history id))))
      (is (every? zero?
                  (map :change_amount (rest (db/get-inventory-history id)))))))
  (testing "can't move more than the cellar holds, or return what isn't there"
    (let [id (new-wine! 1)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"out of the cellar to put back"
                            (db/take-out-of-cellar id -1)))
      (db/take-out-of-cellar id 1)
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"left in the cellar"
                            (db/take-out-of-cellar id 1))))))

(deftest bottles-out-never-exceed-stock
  (let [id (new-wine! 2)]
    (db/take-out-of-cellar id 2)
    (testing "a gift of the last cellar bottle comes from those taken out"
      (db/adjust-quantity id -1 {:reason "gift"})
      (is (= 1 (:out_quantity (wine id)))))
    (testing "opening the only bottle left counts it as no longer out"
      (let [w (db/coravin-pour id 2)]
        (is (= 1 (:quantity w)))
        (is (= 0 (:out_quantity w)))))))

(deftest last-activity-follows-the-latest-stock-change
  (let [id (new-wine! 2)]
    (testing "a new wine's activity is when it was added"
      (is (= (str (:created_at (wine id)))
             (str (:last_activity_at (wine id))))))
    (testing "a drink dated later moves it to that date"
      (db/adjust-quantity id -1 {:reason "drunk" :occurred_at "2031-05-04"})
      (is (re-find #"2031-05-04" (str (:last_activity_at (wine id))))))
    (testing "the list carries it too"
      (is (some #(and (= id (:id %)) (:last_activity_at %))
                (db/get-wines-for-list))))))
