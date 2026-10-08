(ns wine-cellar.admin.bulk-operations-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.admin.bulk-operations :as bulk]
            [wine-cellar.ai.core :as ai]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(defn- new-wine!
  [producer]
  (:id (db/create-wine {:producer producer
                        :country "France"
                        :region "Alsace"
                        :style "White"
                        :quantity 1})))

(defn- await-job
  [job-id]
  (loop [tries 100]
    (let [{:keys [status] :as job} (bulk/get-job-status job-id)]
      (if (or (#{:completed :failed} status) (zero? tries))
        job
        (do (Thread/sleep 50) (recur (dec tries)))))))

(deftest drinking-window-job-updates-each-wine
  (let [ids [(new-wine! "ZZ Job A") (new-wine! "ZZ Job B")]]
    (with-redefs [ai/suggest-drinking-window (fn [_ _]
                                               {:drink_from_year 2030
                                                :drink_until_year 2040
                                                :reasoning "ZZ reasons"})]
      (let [job (await-job (bulk/start-drinking-window-job
                            {:wine-ids ids :provider "anthropic"}))]
        (is
         (= {:status :completed :job-type :drinking-window :progress 2 :total 2}
            (select-keys job [:status :job-type :progress :total])))
        (is (empty? (:failed-wines job)))
        (is (every? #(= 2030 (:drink_from_year (db/get-wine % false))) ids))))))

(deftest summary-job-records-failures-and-carries-on
  (let [[good bad] [(new-wine! "ZZ Good") (new-wine! "ZZ Bad")]]
    (with-redefs [ai/generate-wine-summary
                  (fn [_ wine] (if (= bad (:id wine)) "  " "A fine wine."))]
      (let [job (await-job (bulk/start-wine-summary-job
                            {:wine-ids [good bad] :provider "anthropic"}))]
        (is (= :completed (:status job)))
        (is (= 2 (:progress job)))
        (is (= [{:wine-id bad :error "AI summary was blank"}]
               (:failed-wines job)))
        (is (= "A fine wine." (:ai_summary (db/get-wine good true))))))))

(deftest a-job-with-no-wines-fails
  (testing "ids that match nothing"
    (is (= :failed
           (:status (await-job (bulk/start-wine-summary-job
                                {:wine-ids [-1] :provider "anthropic"})))))))
