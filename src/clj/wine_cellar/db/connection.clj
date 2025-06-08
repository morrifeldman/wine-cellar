(ns wine-cellar.db.connection
  (:require [mount.core :refer [defstate]]
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
  (if-let [jdbc-url (System/getenv "DATABASE_URL")]
    {:jdbcUrl jdbc-url}
    {:dbtype "postgresql"
     :dbname "wine_cellar"
     :user "wine_cellar"
     :password (config-utils/get-password-from-pass "wine-cellar/db")}))

(defstate ds :start (jdbc/get-datasource (get-db-config)))

(def db-opts {:builder-fn rs/as-unqualified-maps})
