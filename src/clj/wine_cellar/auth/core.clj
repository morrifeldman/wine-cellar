(ns wine-cellar.auth.core
  (:require [buddy.sign.jwt :as jwt]
            [clojure.string :as str]
            [ring.util.response :as response]
            [wine-cellar.auth.config :as config]
            [jsonista.core :as json]
            [org.httpkit.client :as http])
  (:import [java.time Instant]
           [java.time.temporal ChronoUnit]
           [java.util UUID]))

(defn create-oauth-client
  []
  (let [oauth-config (config/get-oauth-config)]
    (when oauth-config
      {:authorization-uri "https://accounts.google.com/o/oauth2/v2/auth"
       :token-uri "https://oauth2.googleapis.com/token"
       :client-id (:client-id oauth-config)
       :client-secret (:client-secret oauth-config)
       :redirect-uri (:redirect-uri oauth-config)
       :scope ["email" "profile"]})))

(defn create-auth-uri
  [session]
  (let [oauth-client (create-oauth-client)]
    (when oauth-client
      (let [state (str (UUID/randomUUID))]
        ;; Store state in the session instead of an atom
        (str (:authorization-uri oauth-client)
             "?client_id="
             (:client-id oauth-client)
             "&redirect_uri="
             (java.net.URLEncoder/encode (:redirect-uri oauth-client) "UTF-8")
             "&response_type=code"
             "&scope=" (java.net.URLEncoder/encode
                        (str/join " " (:scope oauth-client))
                        "UTF-8")
             "&state=" state)))))

(defn redirect-to-google
  [request]
  (tap> ["redirect-to-google" (:session request)])
  (if-let [auth-uri (create-auth-uri (:session request))]
    (let [state (last (re-find #"&state=([^&]+)" auth-uri))]
      (tap> ["auth-uri" auth-uri "state" state])
      (-> (response/redirect auth-uri)
          (assoc :session (assoc (:session request) :oauth-state state))))
    (response/bad-request "OAuth configuration is missing")))

(defn exchange-code-for-token
  [code]
  (tap> ["exchange-code-for-token" code])
  (let [oauth-client (create-oauth-client)]
    (when oauth-client
      (try (let [request-params {:form-params
                                 {:code code
                                  :client_id (:client-id oauth-client)
                                  :client_secret (:client-secret oauth-client)
                                  :redirect_uri (:redirect-uri oauth-client)
                                  :grant_type "authorization_code"}
                                 :as :text}
                 _ (tap> ["token-request" request-params])
                 {:keys [status body error]}
                 @(http/post (:token-uri oauth-client) request-params)]
             (tap> ["token-response" {:status status :error error :body body}])
             (if (or error (not= status 200))
               (do (println "Error exchanging code for token:"
                            (or error (str "HTTP " status)))
                   nil)
               (json/read-value body)))
           (catch Exception e
             (println "Exception exchanging code for token:" (.getMessage e))
             (tap> ["token-exception" (.getMessage e)])
             nil)))))

(defn get-user-info
  [access-token]
  (tap> ["get-user-info" access-token])
  (try (let [{:keys [status body error]}
             @(http/get "https://www.googleapis.com/oauth2/v3/userinfo"
                        {:headers {"Authorization" (str "Bearer " access-token)}
                         :as :text})]
         (tap> ["user-info-response" {:status status :error error}])
         (if (or error (not= status 200))
           (do (println "Error getting user info:"
                        (or error (str "HTTP " status)))
               nil)
           (json/read-value body)))
       (catch Exception e
         (println "Exception getting user info:" (.getMessage e))
         (tap> ["user-info-exception" (.getMessage e)])
         nil)))

(defn create-jwt-token
  [user-info]
  (tap> ["create-jwt-token" user-info])
  (let [jwt-secret (config/get-jwt-secret)
        now (Instant/now)
        claims (assoc user-info
                      "iat" (inst-ms now)
                      "exp" (inst-ms (.plus now 7 ChronoUnit/DAYS)))]
    (jwt/sign claims jwt-secret {:alg :hs256})))

(defn handle-google-callback
  [request]
  (tap> ["handle-google-callback" (:query-params request) "session"
         (:session request)])
  (let [code (get-in request [:query-params "code"])
        state (get-in request [:query-params "state"])
        session-state (get-in request [:session :oauth-state])
        error (get-in request [:query-params "error"])]
    ;; Check for error from Google
    (if error
      (do (println "OAuth error from Google:" error)
          (tap> ["oauth-error" error])
          (response/bad-request (str "Authentication error: " error)))
      ;; Verify state to prevent CSRF
      (if (and state (= state session-state))
        (do (tap> ["state-verified" state])
            ;; Exchange code for token
            (if-let [token-response (exchange-code-for-token code)]
              (let [_ (tap> ["token-response" token-response])
                    access-token (get token-response "access_token")
                    _ (tap> ["access-token-received" access-token])
                    user-info (get-user-info access-token)
                    _ (tap> ["user-info-received" user-info])
                    jwt-token (create-jwt-token user-info)]
                (tap> ["jwt-token" jwt-token])
                (-> (response/redirect "/")
                    (assoc-in [:cookies "auth-token"]
                              {:value jwt-token
                               :http-only true
                               :max-age (* 7 24 60 60) ; 7 days
                               :same-site :lax
                               :path "/"})
                    ;; Clear the oauth state from session
                    (assoc :session (dissoc (:session request) :oauth-state))))
              (do (tap> ["token-exchange-failed"])
                  (response/bad-request "Failed to authenticate with Google"))))
        ;; Invalid state - possible CSRF attack
        (do (tap> ["invalid-state"
                   {:received state :session-state session-state}])
            (response/bad-request "Invalid authentication state"))))))

(defn verify-token
  [token]
  (try (let [decoded (jwt/unsign token (config/get-jwt-secret) {:alg :hs256})]
         (tap> ["verify-token" "decoded" decoded])
         decoded)
       (catch Exception e (tap> ["verify-token" "error" (.getMessage e)]) nil)))

(defn wrap-auth
  [handler]
  (fn [request]
    (let [token (get-in request [:cookies "auth-token" :value])
          user-info (when token (verify-token token))
          authenticated-request (assoc request :user user-info)]
      (tap> ["authenticated-request" authenticated-request])
      (handler authenticated-request))))

(defn authenticated? [request] (boolean (:user request)))

(defn admin?
  "Check if the user is an admin based on their email"
  [request]
  (tap> ["admin?" request])
  (let [admin-email (config/get-admin-email)]
    (and (authenticated? request)
         (= (get-in request [:user :email]) admin-email))))

(defn require-authentication
  [handler]
  (fn [request]
    (tap> ["require-authentication" (:uri request)])
    (if (authenticated? request)
      (handler request)
      ;; Check if this is an API request
      (if (str/starts-with? (:uri request) "/api")
        ;; For API requests, return 401 with proper JSON and CORS headers
        (let [json-body (json/write-value-as-string {:error
                                                     "Authentication required"})
              response
              (-> (response/response json-body)
                  (response/status 401)
                  (response/content-type "application/json")
                  (update :headers
                          merge
                          {"Access-Control-Allow-Origin" "http://localhost:8080"
                           "Access-Control-Allow-Credentials" "true"}))]
          response)
        ;; For browser requests, redirect to login
        (response/redirect "/login")))))

(defn require-admin
  "Middleware that ensures the user is an admin"
  [handler]
  (fn [request]
    (tap> ["require-admin" (:uri request)])
    (if (admin? request)
      (handler request)
      ;; Return 403 Forbidden with proper JSON and CORS headers
      (let [json-body (json/write-value-as-string {:error
                                                   "Admin access required"})
            response (-> (response/response json-body)
                         (response/status 403)
                         (response/content-type "application/json")
                         (update :headers
                                 merge
                                 {"Access-Control-Allow-Origin"
                                  "http://localhost:8080"
                                  "Access-Control-Allow-Credentials" "true"}))]
        response))))

(defn logout
  [request]
  (-> (response/redirect "/")
      (assoc-in [:cookies "auth-token"]
                {:value ""
                 :http-only true
                 :max-age 0 ; Expire immediately
                 :same-site :lax
                 :path "/"})))

