(ns wine-cellar.db.migrations-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc :as jdbc]
            [wine-cellar.db.connection :refer [ds q-many q-one]]
            [wine-cellar.db.migrations :as migrations]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(deftest ids-are-unique
  (let [ids (map :id migrations/migrations)]
    (is (= (count ids) (count (set ids))))))

(deftest setup-records-every-migration
  (is (= (set (map :id migrations/migrations))
         (set (map :id (q-many {:select [:id] :from [:schema_migrations]}))))))

(deftest a-migration-runs-once
  (let [extra {:id "9999-test-only"
               :sql {:raw ["CREATE TABLE zz_migration_runs (n integer)"]}}
        migrate #(jdbc/with-transaction
                  [tx ds]
                  (migrations/migrate! tx (conj migrations/migrations extra)))]
    (try (migrate)
         ;; A second run would fail on CREATE TABLE if it applied it again.
         (migrate)
         (is (some? (q-one {:select [:id]
                            :from [:schema_migrations]
                            :where [:= :id "9999-test-only"]})))
         (finally
          (jdbc/execute! ds ["DROP TABLE IF EXISTS zz_migration_runs"])
          (jdbc/execute!
           ds
           ["DELETE FROM schema_migrations WHERE id = '9999-test-only'"])))))
