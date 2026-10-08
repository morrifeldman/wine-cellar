(ns wine-cellar.db.sensor-series-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(deftest readings-are-bucketed-with-avg-min-max
  (doseq [[at _temp hum co2] [["2026-01-01T10:05:00Z" 12.0 60.0 400.0]
                              ["2026-01-01T10:35:00Z" 14.0 70.0 600.0]
                              ["2026-01-01T11:10:00Z" 13.0 65.0 500.0]]]
    (db/create-sensor-reading! {:device_id "zz-series"
                                :measured_at at
                                :temperatures {:probe 1}
                                :humidity_pct hum
                                :co2_ppm co2
                                :battery_mv 3000}))
  (let [rows (db/sensor-reading-series {:device_id "zz-series" :bucket "1h"})
        first-hour (first rows)]
    (is (= ["2026-01-01T10:00:00Z" "2026-01-01T11:00:00Z"]
           (map :bucket_start rows)))
    (is (= [65.0 60.0 70.0]
           (map #(double (% first-hour))
                [:avg_humidity_pct :min_humidity_pct :max_humidity_pct])))
    (is (= [500.0 400.0 600.0]
           (map #(double (% first-hour))
                [:avg_co2_ppm :min_co2_ppm :max_co2_ppm])))
    (is (= 3000.0 (double (:avg_battery_mv first-hour))))
    (is (nil? (:avg_pressure_hpa first-hour)))
    (is (= {:probe 1.0} (update-vals (:avg_temperatures first-hour) double)))
    (is (= "zz-series" (:device_id first-hour)))
    (is (= 20 (count first-hour)) "device, bucket and every statistic")))
