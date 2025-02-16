(ns wine-cellar.db
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def db
  {:dbtype "sqlite"
   :dbname "wine-cellar.db"})

(def ds
  (jdbc/get-datasource db))

(def db-opts
  {:builder-fn rs/as-unqualified-maps})

(def create-table-sql
  "CREATE TABLE IF NOT EXISTS
   wines (
   id INTEGER PRIMARY KEY AUTOINCREMENT,
   name TEXT NOT NULL,
   vintage INTEGER,
   type TEXT CHECK (type IN
   ('red', 'white', 'rose', 'sparkling', 'fortified', 'orange')),
   location TEXT,
   quantity INTEGER NOT NULL DEFAULT 0,
   price DECIMAL(10,2),
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );")

(defn create-wines-table []
  (jdbc/execute-one! ds [create-table-sql] db-opts))

(defn create-wine [wine]
  (jdbc/execute-one! ds
    (sql/format
      {:insert-into :wines
       :columns [:name :vintage :type :location :quantity :price]
       :values [[(:name wine)
                (:vintage wine)
                (:type wine)
                (:location wine)
                (:quantity wine)
                (:price wine)]]})
    db-opts))

(defn get-wine [id]
  (jdbc/execute-one! ds
    (sql/format
      {:select :*
       :from :wines
       :where [:= :id id]})
    db-opts))

(defn get-all-wines []
  (jdbc/execute! ds
    (sql/format
      {:select :*
       :from :wines
       :order-by [[:created_at :desc]]})
    db-opts))

(defn update-wine! [id wine]
  (jdbc/execute-one!
    ds
    (sql/format
      {:update :wines
       :set (assoc wine
                   :updated_at [:raw "CURRENT_TIMESTAMP"])
       :where [:= :id id]})
    db-opts))

(defn update-quantity [id new-quantity]
  (update-wine! id {:quantity new-quantity}))

(defn adjust-quantity [id adjustment]
  (update-quantity id [:+ :quantity adjustment]))

(defn delete-wine! [id]
  (jdbc/execute-one! ds
    (sql/format
      {:delete-from :wines
       :where [:= :id id]})
    db-opts))