(ns wine-cellar.auth.core
  (:require [buddy.sign.jwt :as jwt]
            [clj-oauth2.client :as oauth2]
            [ring.util.response :as response]
            [wine-cellar.auth.config :as config])
  (:import [java.time Instant]
           [java.time.temporal ChronoUnit]))

(defn create-oauth-client []
  (let [oauth-config (config/get-oauth-config)]
    (when oauth-config
      {:authorization-uri "https://accounts.google.com/o/oauth2/auth"
       :access-token-uri "https://oauth2.googleapis.com/token"
       :client-id (:client-id oauth-config)
       :client-secret (:client-secret oauth-config)
       :redirect-uri (:redirect-uri oauth-config)
       :scope ["email" "profile"]})))

(defn create-auth-uri []
  (let [oauth-client (create-oauth-client)]
    (when oauth-client
      (oauth2/make-auth-request oauth-client))))

(defn redirect-to-google []
  (if-let [auth-uri (create-auth-uri)]
    (response/redirect auth-uri)
    (response/bad-request "OAuth configuration is missing")))

(defn exchange-code-for-token [code]
  (let [oauth-client (create-oauth-client)]
    (when oauth-client
      (try
        (oauth2/get-access-token oauth-client code (:redirect-uri oauth-client))
        (catch Exception e
          (println "Error exchanging code for token:" (.getMessage e))
          nil)))))

(defn get-user-info [access-token]
  (try
    (let [response (oauth2/get "https://www.googleapis.com/oauth2/v3/userinfo"
                               {:oauth-token access-token})]
      (:body response))
    (catch Exception e
      (println "Error getting user info:" (.getMessage e))
      nil)))

(defn create-jwt-token [user-info]
  (let [jwt-secret (config/get-jwt-secret)
        now (Instant/now)
        claims {:sub (:sub user-info)
                :email (:email user-info)
                :name (:name user-info)
                :picture (:picture user-info)
                :iat (inst-ms now)
                :exp (inst-ms (.plus now 7 ChronoUnit/DAYS))}]
    (jwt/sign claims jwt-secret {:alg :hs256})))

(defn handle-google-callback [request]
  (let [code (get-in request [:query-params "code"])]
    (if-let [token-response (exchange-code-for-token code)]
      (let [access-token (:access-token token-response)
            user-info (get-user-info access-token)
            jwt-token (create-jwt-token user-info)]
        (-> (response/redirect "/")
            (assoc-in [:cookies "auth-token"] {:value jwt-token
                                               :http-only true
                                               :max-age (* 7 24 60 60) ; 7 days
                                               :same-site :lax})))
      (response/bad-request "Failed to authenticate with Google"))))

(defn verify-token [token]
  (try
    (jwt/unsign token (config/get-jwt-secret) {:alg :hs256})
    (catch Exception _
      nil)))

(defn wrap-auth [handler]
  (fn [request]
    (let [token (get-in request [:cookies "auth-token" :value])
          user-info (when token (verify-token token))
          authenticated-request (assoc request :user user-info)]
      (handler authenticated-request))))

(defn authenticated? [request]
  (boolean (:user request)))

(defn require-authentication [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (response/redirect "/login"))))

