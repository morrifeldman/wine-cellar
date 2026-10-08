(ns wine-cellar.specs-test
  "Every wine column is either settable through the request specs or listed
   here as managed by the server, so a new column can't be silently dropped
   by coercion."
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is use-fixtures]]
            [wine-cellar.db.connection :refer [q-many]]
            [wine-cellar.specs :as specs]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(def server-managed
  "Columns no request body sets directly."
  #{"id" "created_at" "updated_at"
    ;; Set by the Coravin pour and finish-bottle endpoints.
    "open_bottle_opened_at" "open_bottle_oz_poured"})

(deftest every-wine-column-is-covered
  (let
    [columns
     (set
      (map
       :column_name
       (q-many
        {:raw
         "SELECT column_name FROM information_schema.columns WHERE table_name = 'wines'"})))
     spec'd (set (map name specs/wine-fields))]
    (is (= #{} (set (remove (into spec'd server-managed) columns)))
        "columns with no spec: add them to specs/wine-fields or server-managed")
    (is (= #{} (set (remove columns spec'd)))
        "specs/wine-fields names columns the table doesn't have")
    (is (every? s/get-spec specs/wine-fields) "every listed field has a spec")))
