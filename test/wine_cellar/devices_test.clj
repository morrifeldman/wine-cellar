(ns wine-cellar.devices-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [wine-cellar.auth.core :as auth]
            [wine-cellar.devices :as devices]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(deftest device-access-tokens-expire-after-their-ttl
  (let [{:keys [token expires]} (devices/issue-access-token "sensor-1")
        claims (auth/verify-token token)
        now (quot (System/currentTimeMillis) 1000)
        ttl (* 60 devices/access-token-ttl-minutes)]
    (is (= "sensor-1" (:device_id claims)))
    (is (= (.getEpochSecond expires) (:exp claims)))
    (is (<= (- (:exp claims) now ttl) 5))))
