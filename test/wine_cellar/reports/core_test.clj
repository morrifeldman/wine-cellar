(ns wine-cellar.reports.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.ai.core :as ai]
            [wine-cellar.reports.core :as reports]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(deftest generates-saves-and-reuses-the-weekly-report
  (let [calls (atom 0)]
    (with-redefs [ai/generate-report-commentary
                  (fn [_ _] (swap! calls inc) "ZZ commentary")]
      (let [report (reports/generate-report!)]
        (is (= "ZZ commentary" (:ai_commentary report)))
        (is (contains? (:summary_data report) :drink-now-ids))
        (testing "listed and fetchable by id"
          (is (some #(= (:id report) (:id %)) (reports/list-reports)))
          (is (= (:id report) (:id (reports/get-report-by-id (:id report))))))
        (testing "the same week's report is reused unless forced"
          (is (= (:id report) (:id (reports/generate-report!))))
          (is (= 1 @calls))
          (reports/generate-report! {:force? true})
          (is (= 2 @calls)))))))
