(ns wine-cellar.db.connection
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [jsonista.core :as json]
            [wine-cellar.config-utils :as config-utils])
  (:import [org.postgresql.jdbc PgArray]
           [org.postgresql.util PGobject]))

;; Protocol extensions for PostgreSQL types
(extend-protocol rs/ReadableColumn
 PgArray
   (read-column-by-label [^PgArray v _] (into [] (.getArray v)))
   (read-column-by-index [^PgArray v _2 _3] (into [] (.getArray v)))
 PGobject
   (read-column-by-label [^PGobject v _]
     (let [type (.getType v)
           value (.getValue v)]
       (case type
         "json" (json/read-value value json/keyword-keys-object-mapper)
         "jsonb" (json/read-value value json/keyword-keys-object-mapper)
         value)))
   (read-column-by-index [^PGobject v _2 _3]
     (let [type (.getType v)
           value (.getValue v)]
       (case type
         "json" (json/read-value value json/keyword-keys-object-mapper)
         "jsonb" (json/read-value value json/keyword-keys-object-mapper)
         value))))

(defn get-db-config
  []
  (cond
    ;; Case 1: Explicit DB_PASSWORD env var (safest for complex chars)
    (System/getenv "DB_PASSWORD")
    {:dbtype "postgresql"
     :dbname (or (System/getenv "DB_NAME") "wine_cellar")
     :host (or (System/getenv "DB_HOST") "localhost")
     :port (if-let [p (System/getenv "DB_PORT")]
             (Integer/parseInt p)
             5432)
     :user (or (System/getenv "DB_USER") "postgres")
     :password (System/getenv "DB_PASSWORD")}
    ;; Case 2: DATABASE_URL parsing
    (System/getenv "DATABASE_URL")
    (let [url (System/getenv "DATABASE_URL")]
      (if (str/starts-with? url "postgres://")
        (let [uri (java.net.URI. url)
              userInfo (.getUserInfo uri)
              [user password]
              (if userInfo (str/split userInfo #":" 2) [nil nil])
              query-params (when-let [q (.getQuery uri)]
                             (into {}
                                   (for [pair (str/split q #"&")]
                                     (let [[k v] (str/split pair #"=" 2)]
                                       [(keyword k) v]))))]
          (merge {:dbtype "postgresql"
                  :dbname (subs (.getPath uri) 1)
                  :host (.getHost uri)
                  :port (if (= -1 (.getPort uri)) 5432 (.getPort uri))
                  :user user
                  :password password}
                 query-params))
        {:jdbcUrl url}))
    ;; Case 3: Local dev defaults
    :else {:dbtype "postgresql"
           :dbname "wine_cellar"
           :user "wine_cellar"
           :password (config-utils/get-password-from-pass "wine-cellar/db")}))

(defstate ds :start (jdbc/get-datasource (get-db-config)))

(def db-opts {:builder-fn rs/as-unqualified-maps})
