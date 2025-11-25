(ns wine-cellar.devices
  (:require [buddy.sign.jwt :as jwt]
            [wine-cellar.auth.config :as auth-config]
            [wine-cellar.db.api :as db-api])
  (:import [java.security MessageDigest SecureRandom]
           [java.time Instant]
           [java.time.temporal ChronoUnit]
           [java.util Base64]))

(def access-token-ttl-minutes 30)

(defn hash-string
  [s]
  (when s
    (let [digest (MessageDigest/getInstance "SHA-256")]
      (->> (.digest digest (.getBytes ^String s "UTF-8"))
           (map (fn [b] (format "%02x" (bit-and b 0xff))))
           (apply str)))))

(defn random-token
  []
  (let [bytes (byte-array 32)
        rng (SecureRandom.)]
    (.nextBytes rng bytes)
    (.replace (.encodeToString (Base64/getUrlEncoder) bytes) "=" "")))

(defn issue-access-token
  [device-id]
  (let [now (Instant/now)
        exp (.plus now access-token-ttl-minutes ChronoUnit/MINUTES)
        claims {:sub device-id
                :device_id device-id
                :scope ["device:ingest"]
                :type "device"
                :aud "device"
                :iat (inst-ms now)
                :exp (inst-ms exp)}]
    {:token (jwt/sign claims (auth-config/get-jwt-secret) {:alg :hs256})
     :expires exp}))

(defn issue-refresh-token
  []
  (let [token (random-token)] {:token token :hash (hash-string token)}))

(defn issue-token-pair
  [device-id]
  (let [{access-token :token expires :expires} (issue-access-token device-id)
        {refresh-token :token refresh-hash :hash} (issue-refresh-token)]
    {:access_token access-token
     :access_expires_at expires
     :refresh_token refresh-token
     :refresh_token_hash refresh-hash}))

(defn issue-and-store-token-pair!
  [device-id]
  (let [pair (issue-token-pair device-id)
        {:keys [access_expires_at refresh_token_hash]} pair
        updated (db-api/set-device-refresh-token!
                 {:device_id device-id
                  :refresh_token_hash refresh_token_hash
                  :token_expires_at access_expires_at})]
    {:device updated
     :tokens (-> pair
                 (dissoc :refresh_token_hash)
                 (update :access_expires_at str))}))

(defn claim!
  [{:keys [claim_code] :as claim}]
  (db-api/upsert-device-claim! (-> claim
                                   (assoc :claim_code_hash
                                          (hash-string claim_code))
                                   (dissoc :claim_code))))

(defn refresh-with-token!
  [device refresh-token]
  (when (and refresh-token
             (= (:refresh_token_hash device) (hash-string refresh-token)))
    (issue-and-store-token-pair! (:device_id device))))

(defn public-device-view
  [device]
  (dissoc device :claim_code_hash :refresh_token_hash))
