(ns wine-cellar.api.enum-rules-test
  "Category vocabularies from common are enforced at the API, and wine
   styles added to common reach an existing database's enum."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.common :as common]
            [wine-cellar.db.connection :refer [q-many]]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(defn- status [method uri body] (:status (ts/request method uri body)))

(deftest spirit-categories
  (is (= 201 (status :post "/api/spirits" {:name "ZZ Gin" :category "gin"})))
  (testing "a mixer shelf is not a spirit category"
    (is (= 400
           (status :post "/api/spirits" {:name "ZZ Soda" :category "soda"})))))

(deftest bar-inventory-categories
  (is (=
       201
       (status :post "/api/bar-inventory" {:name "ZZ Lime" :category "fruit"})))
  (testing "a spirit category is not a mixer shelf"
    (is
     (= 400
        (status :post "/api/bar-inventory" {:name "ZZ Rum" :category "rum"})))))

(deftest wine-style-enum-covers-common
  (let
    [labels
     (set
      (map
       :enumlabel
       (q-many
        {:raw
         "SELECT e.enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid WHERE t.typname = 'wine_style'"})))]
    (is (every? labels common/wine-styles))))
