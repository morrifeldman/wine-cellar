(ns wine-cellar.db.setup-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(deftest fresh-database-is-initialized-and-seeded
  ;; The seed file carries keys with no column (:designations); seeding an
  ;; empty database used to fail on them.
  (is (seq (db/get-classifications))))

(deftest api-smoke
  (let [{:keys [status body]} (ts/request :get "/api/classifications")]
    (is (= 200 status))
    (is (seq body))))
