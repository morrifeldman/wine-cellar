(ns wine-cellar.test-support
  "Fixtures for tests that need a real database or the full HTTP stack.

   Tests run against a throwaway database (TEST_DB_NAME, default
   wine_cellar_test) that is dropped and recreated once per test run, using
   the same connection settings as the app. The app's mount states start with
   that database swapped in; the HTTP server and scheduler stay off."
  (:require [jsonista.core :as json]
            [mount.core :as mount]
            [next.jdbc :as jdbc]
            [wine-cellar.auth.core :as auth]
            [wine-cellar.db.connection :as conn]
            [wine-cellar.db.setup :as db-setup]
            [wine-cellar.routes :as routes]
            [wine-cellar.scheduler :as scheduler]
            [wine-cellar.server :as server])
  (:import [java.io ByteArrayInputStream]))

(def test-db-name (or (System/getenv "TEST_DB_NAME") "wine_cellar_test"))

(defn- recreate-test-db!
  [db-config]
  (let [admin-ds (jdbc/get-datasource (assoc db-config :dbname "postgres"))]
    (jdbc/execute!
     admin-ds
     [(str "DROP DATABASE IF EXISTS " test-db-name " WITH (FORCE)")])
    (jdbc/execute! admin-ds [(str "CREATE DATABASE " test-db-name)])))

(defonce ^:private started? (atom false))

(defn start-system!
  "Recreates the test database and starts the app's states on it. Runs once
   per JVM; later calls are no-ops."
  []
  (when (compare-and-set! started? false true)
    (let [db-config (assoc (conn/get-db-config) :dbname test-db-name)]
      (when (:jdbcUrl db-config)
        (throw (ex-info "Tests need DB_* settings, not a jdbc DATABASE_URL"
                        {})))
      (recreate-test-db! db-config)
      (-> (mount/except [#'server/server #'scheduler/scheduler])
          (mount/swap {#'conn/ds (jdbc/get-datasource db-config)})
          mount/start)
      (db-setup/initialize-db))))

(defn with-system "clojure.test :once fixture." [f] (start-system!) (f))

(def test-email "test@example.com")

(defn request
  "Calls the app's ring handler as a logged-in user. `body` is sent as JSON;
   the response body comes back parsed, with keyword keys."
  ([method uri] (request method uri nil))
  ([method uri body]
   (let [token (auth/create-jwt-token {:email test-email})
         response (routes/app
                   (cond-> {:request-method method
                            :uri uri
                            :headers {"accept" "application/json"
                                      "authorization" (str "Bearer " token)}}
                     body (-> (assoc-in [:headers "content-type"]
                                        "application/json")
                              (assoc :body
                                     (ByteArrayInputStream.
                                      (json/write-value-as-bytes body))))))]
     (update response
             :body
             (fn [b]
               (let [s (if (string? b)
                         b
                         (some-> b
                                 slurp))]
                 (if (seq s)
                   (try (json/read-value s json/keyword-keys-object-mapper)
                        (catch Exception _ s))
                   s)))))))
