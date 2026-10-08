(ns wine-cellar.api.devices-test
  "A sensor's life: claim, approval, token, readings."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(def device-id "zz-sensor-1")
(def claim-code "ZZCODE")

(defn- reading
  [token body]
  (ts/request :post "/api/sensor-readings" body {:token token}))

(deftest a-sensor-is-claimed-approved-and-reports
  (testing "claiming leaves it pending until an admin approves"
    (is (= 202
           (:status (ts/request :post
                                "/api/device-claim"
                                {:device_id device-id
                                 :claim_code claim-code}))))
    (is (= "pending"
           (get-in (ts/request :post
                               "/api/device-claim/poll"
                               {:device_id device-id :claim_code claim-code})
                   [:body :status]))))
  (testing "a wrong code doesn't approve it"
    (is (= 422
           (:status (ts/request :post
                                (str "/api/admin/devices/" device-id "/approve")
                                {:claim_code "WRONG"})))))
  (is (= 200
         (:status (ts/request :post
                              (str "/api/admin/devices/" device-id "/approve")
                              {:claim_code claim-code}))))
  (let [{:keys [access_token]} (:body (ts/request :post
                                                  "/api/device-claim/poll"
                                                  {:device_id device-id
                                                   :claim_code claim-code}))]
    (is access_token)
    (testing "its readings are stored and new probes get a config slot"
      (is (= 201
             (:status (reading access_token
                               {:device_id device-id
                                :temperatures {:probe-a 13.5}}))))
      (is (contains? (:sensor_config (db/get-device device-id)) :probe-a)))
    (testing "it can't report for another device"
      (is (= 403
             (:status (reading access_token
                               {:device_id "someone-else"
                                :temperatures {:probe-a 1}})))))
    (testing "a reading needs a measurement"
      (is (= 400 (:status (reading access_token {:device_id device-id})))))
    (testing "a blocked device is turned away"
      (ts/request :post (str "/api/admin/devices/" device-id "/block"))
      (is (= 403
             (:status (reading access_token
                               {:device_id device-id
                                :temperatures {:probe-a 1}})))))))
