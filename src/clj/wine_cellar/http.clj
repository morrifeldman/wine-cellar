(ns wine-cellar.http
  "Ring responses and the one place exceptions become HTTP errors.

   Handlers return a response or throw. An ex-info whose data carries :status
   becomes that status with its message as :error (AI and domain errors use
   this); a PostgreSQL data or constraint error becomes a 400; anything else
   is a 500."
  (:require [expound.alpha :as expound]
            [reitit.ring.middleware.exception :as exception]
            [ring.util.response :as response])
  (:import [org.postgresql.util PSQLException]))

(defn ok [body] (response/response body))

(defn created [body] {:status 201 :body body})

(defn no-content [] {:status 204 :headers {} :body nil})

(defn bad-request [message] {:status 400 :body {:error message}})

(defn not-found
  [resource]
  {:status 404 :body {:error (str resource " not found")}})

(defn found-or-404
  "200 with `x`, or 404 naming `resource` when `x` is nil."
  [resource x]
  (if (some? x) (ok x) (not-found resource)))

(defn deleted-or-404
  "204 when something was deleted, else 404 naming `resource`."
  [resource deleted?]
  (if deleted? (no-content) (not-found resource)))

(defn throw-status
  "Throws an ex-info that the exception middleware turns into `status` with
   `message` as :error."
  ([status message] (throw-status status message {}))
  ([status message data] (throw (ex-info message (assoc data :status status)))))

;; Exception handlers

(defn- server-error
  [^Exception e _request]
  (tap> e)
  {:status 500 :body {:error "Internal server error" :details (.getMessage e)}})

(defn- ex-info-error
  "An ex-info's message is meant for the user (AI and domain errors throw
   these), so it becomes :error, with :status from its data or else 500."
  [^clojure.lang.ExceptionInfo e _request]
  (let [data (ex-data e)
        status (or (:status data) 500)
        message (.getMessage e)
        ;; The Anthropic API puts the human-readable reason (e.g. "Your
        ;; credit balance is too low…") in the parsed error body.
        error
        (or (:error data) (get-in data [:parsed :error :message]) message)]
    (when (>= status 500) (tap> e))
    {:status status
     :body (cond-> {:error error}
             (not= error message) (assoc :details message)
             (:code data) (assoc :code (:code data))
             (:response data) (assoc :response (:response data)))}))

(defn- sql-error
  [^PSQLException e request]
  ;; SQLSTATE class 22 is bad data, 23 a violated constraint: the request's
  ;; fault. Anything else (connection, syntax) is ours.
  (if (#{"22" "23"}
       (some-> (.getSQLState e)
               (subs 0 2)))
    {:status 400 :body {:error "Invalid data" :details (.getMessage e)}}
    (server-error e request)))

(defn- coercion-error
  [status]
  (fn [e _request]
    (let [data (ex-data e)
          explanation (expound/expound-str (:spec data) (:value data))]
      (tap> ["Validation error:" explanation])
      ;; No :error key, so the app shows its own "Failed to ..." message
      ;; rather than a multi-line spec report.
      {:status status :body {:details explanation}})))

(def exception-middleware
  (exception/create-exception-middleware
   (merge exception/default-handlers
          {:reitit.coercion/request-coercion (coercion-error 400)
           :reitit.coercion/response-coercion (coercion-error 500)
           clojure.lang.ExceptionInfo ex-info-error
           PSQLException sql-error
           ::exception/default server-error})))
