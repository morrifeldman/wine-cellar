(ns wine-cellar.reports.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [jsonista.core :as json]
            [wine-cellar.ai.core :as ai]
            [wine-cellar.db.api :as db]
            [wine-cellar.reports.core :as reports]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(deftest generates-saves-and-reuses-the-weekly-report
  (let [calls (atom 0)]
    (with-redefs [ai/generate-report-commentary
                  (fn [_ _] (swap! calls inc) "ZZ commentary")]
      ;; Forced, since another test may have made this week's report
      ;; already
      (let [report (reports/generate-report! {:force? true})]
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

(deftest the-commentary-sees-every-recent-event
  (let [id (:id (db/create-wine {:producer "ZZ Report Activity"
                                 :country "France"
                                 :region "Bordeaux"
                                 :style "Red"
                                 :quantity 12
                                 :original_quantity 12}))
        sent (atom nil)]
    (dotimes [_ 8] (db/adjust-quantity id -1 {:reason "drunk"}))
    (with-redefs [ai/generate-report-commentary
                  (fn [_ data-json] (reset! sent data-json) "ZZ commentary")]
      (reports/generate-report! {:force? true})
      (let [events (->> (:recent-activity (json/read-value
                                           @sent
                                           json/keyword-keys-object-mapper))
                        (filter #(= id (:wine_id %))))]
        (is (= 8 (count events)))))))

(deftest the-commentary-sees-the-users-own-recent-tasting-notes
  (let [id (:id (db/create-wine {:producer "ZZ Report Tasting"
                                 :country "Italy"
                                 :region "Piedmont"
                                 :style "Red"
                                 :quantity 3
                                 :original_quantity 3}))
        sent (atom nil)]
    (db/create-tasting-note {:wine_id id
                             :tasting_date "2026-11-20"
                             :rating 94
                             :notes "ZZ tar and roses"})
    (db/create-tasting-note {:wine_id id
                             :rating 90
                             :notes "ZZ critic note"
                             :is_external true
                             :source "ZZ Critic"})
    (with-redefs [ai/generate-report-commentary
                  (fn [_ data-json] (reset! sent data-json) "ZZ commentary")]
      (reports/generate-report! {:force? true})
      (let [notes (->> (:recent-tasting-notes
                        (json/read-value @sent json/keyword-keys-object-mapper))
                       (filter #(= id (:wine_id %))))]
        (is (= ["ZZ tar and roses"] (map :notes notes)))
        (is (= 94 (:rating (first notes))))))))
