(ns wine-cellar.api.range-rules-test
  "The numeric ranges in common are enforced by the request specs, the same
   ones the frontend forms check before sending."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.common :as common]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(deftest shared-rules
  (testing "vintage"
    (is (nil? (common/vintage-error nil)) "NV")
    (is (nil? (common/vintage-error 2015)))
    (is (some? (common/vintage-error 1700)))
    (is (some? (common/vintage-error (inc (common/current-year)))))
    (is (some? (common/vintage-error 2015.5))))
  (testing "drinking window"
    (is (nil? (common/tasting-window-error nil 2040)) "open start")
    (is (nil? (common/tasting-window-error 2020 2030)))
    (is (some? (common/tasting-window-error 2030 2020)) "reversed")
    (is (some? (common/tasting-window-error 2020 9999)) "out of range")))

(defn- new-wine!
  []
  (:id (db/create-wine {:producer "ZZ Range Test"
                        :country "France"
                        :region "Loire"
                        :style "White"
                        :quantity 3})))

(deftest wine-updates-respect-the-ranges
  (let [url (str "/api/wines/by-id/" (new-wine!))
        status #(:status (ts/request :put url %))]
    (doseq [[label body] [["future vintage" {:vintage 3000}]
                          ["ancient vintage" {:vintage 1700}]
                          ["negative quantity" {:quantity -1}]
                          ["negative laid down" {:original_quantity -2}]
                          ["negative price" {:price -5}]
                          ["drink-from out of range" {:drink_from_year 3000}]
                          ["ABV over 100" {:alcohol_percentage 120}]
                          ["dosage over 200" {:dosage 250}]
                          ["future disgorgement" {:disgorgement_year 3000}]]]
      (testing label (is (= 400 (status body)))))
    (doseq [[label body] [["NV" {:vintage nil}]
                          ["a real vintage" {:vintage 2015}]
                          ["no stock" {:quantity 0}]
                          ["a drinking window"
                           {:drink_from_year 2025 :drink_until_year 2035}]
                          ["ABV" {:alcohol_percentage 13.5}]]]
      (testing label (is (= 200 (status body)))))))

(deftest tasting-note-ratings-respect-the-range
  (let [url (str "/api/wines/by-id/" (new-wine!) "/tasting-notes")
        status #(:status (ts/request :post url {:notes "ZZ" :rating %}))]
    (is (= 400 (status 0)))
    (is (= 400 (status 101)))
    (is (= 201 (status 92)))))
