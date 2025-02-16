(ns wine-cellar.db
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]))

(def db
  {:dbtype "sqlite"
   :dbname "wine-cellar.db"})

(def ds
  (jdbc/get-datasource db))

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
  (jdbc/execute-one! ds [create-table-sql]))

#_(jdbc/execute-one!
    ds
    (sql/format {:drop-table [:if-exists table]}))

#_(create-wines-table)
#_(jdbc/execute! ds (-> {:select [:*] :from :wines} sql/format))
#_(jdbc/execute! ds (-> {:insert-into [:wines]
                         :values [{:name "a-wine"
                                   :type "rose"
                                   :vintage 2019
                                   :location "H2"
                                   :price 25.30
                                   :quantity 3}]}
                        sql/format))

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
                (:price wine)]]})))

(defn get-wine [id]
  (jdbc/execute-one! ds
    (sql/format
      {:select :*
       :from :wines
       :where [:= :id id]})))

(defn get-all-wines []
  (jdbc/execute! ds
    (sql/format
      {:select :*
       :from :wines
       :order-by [[:created_at :desc]]})))

(defn update-wine! [id wine]
  (jdbc/execute-one!
    ds
    (sql/format
      {:update :wines
       :set (assoc wine
                   :updated_at [:raw "CURRENT_TIMESTAMP"])
       :where [:= :id id]})))

(defn update-quantity [id new-quantity]
  (update-wine! id {:quantity new-quantity}))

(defn adjust-quantity [id adjustment]
  (update-quantity id [:+ :quantity adjustment]))

(defn delete-wine! [id]
  (jdbc/execute-one! ds
    (sql/format
      {:delete-from :wines
       :where [:= :id id]})))
