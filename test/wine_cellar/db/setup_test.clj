(ns wine-cellar.db.setup-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc]
            [wine-cellar.db.api :as db]
            [wine-cellar.db.connection]
            [wine-cellar.db.setup :as setup]
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

(defn- public-tables
  []
  (->>
    (next.jdbc/execute!
     wine-cellar.db.connection/ds
     ["SELECT table_name FROM information_schema.tables
          WHERE table_schema = 'public'"])
    (map :tables/table_name)
    set))

(deftest drop-tables-removes-every-table
  (let [before (public-tables)]
    (is (contains? before "cocktail_recipes"))
    (setup/drop-tables)
    (is (empty? (public-tables)))
    ;; Leave the database as the other tests expect it.
    (setup/initialize-db)
    (is (= before (public-tables)))))
