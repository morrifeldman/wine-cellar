(ns wine-cellar.auth.core-test
  (:require [buddy.sign.jwt :as jwt]
            [clojure.test :refer [deftest is use-fixtures]]
            [wine-cellar.auth.config :as config]
            [wine-cellar.auth.core :as auth]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(defn- now-seconds [] (quot (System/currentTimeMillis) 1000))

(deftest user-tokens-expire-in-a-week
  (let [claims (auth/verify-token (auth/create-jwt-token {:email "a@b.c"}))
        week (* 7 24 60 60)]
    (is (= "a@b.c" (:email claims)))
    (is (<= (- (:exp claims) (now-seconds) week) 5))))

(deftest expired-tokens-are-rejected
  (let [past (- (now-seconds) 60)
        token (jwt/sign {:email "a@b.c" :iat (- past 60) :exp past}
                        (config/get-jwt-secret)
                        {:alg :hs256})]
    (is (nil? (auth/verify-token token)))))
