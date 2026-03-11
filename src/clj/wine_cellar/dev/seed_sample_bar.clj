(ns wine-cellar.dev.seed-sample-bar
  "Populate the database with sample bar data for local testing."
  (:require [mount.core :as mount]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]
            [wine-cellar.db.api :as db]
            [wine-cellar.db.connection :refer [ds db-opts]]))

(def sample-spirits
  [{:name "Hendrick's Gin"
    :category "gin"
    :distillery "William Grant & Sons"
    :country "Scotland"
    :proof 83
    :quantity 1
    :price 38.00M}
   {:name "Campari"
    :category "liqueur"
    :distillery "Campari Group"
    :country "Italy"
    :proof 48
    :quantity 1
    :price 32.00M}
   {:name "Carpano Antica Formula"
    :category "other"
    :distillery "Fratelli Branca"
    :country "Italy"
    :proof 33
    :quantity 1
    :price 28.00M}])

(def negroni-recipe
  {:name "Negroni"
   :description "Classic equal-parts aperitivo cocktail"
   :ingredients [{:name "Gin" :amount "1" :unit "oz"}
                 {:name "Campari" :amount "1" :unit "oz"}
                 {:name "Sweet Vermouth" :amount "1" :unit "oz"}]
   :instructions
   "Stir all ingredients with ice for 20-30 seconds. Strain into a rocks glass over a large ice cube. Garnish with an orange peel."
   :tags ["classic" "aperitivo" "stirred"]
   :source "Seed Data"})

(defn- spirit-exists?
  [name]
  (jdbc/execute-one! ds
                     (sql/format
                      {:select [:id] :from :spirits :where [:= :name name]})
                     db-opts))

(defn- recipe-exists?
  [name]
  (jdbc/execute-one!
   ds
   (sql/format {:select [:id] :from :cocktail_recipes :where [:= :name name]})
   db-opts))

(defn seed!
  []
  (doseq [spirit sample-spirits]
    (if (spirit-exists? (:name spirit))
      (println "Skipping existing spirit:" (:name spirit))
      (do (db/create-spirit! spirit)
          (println "Created spirit:" (:name spirit)))))
  (if (recipe-exists? (:name negroni-recipe))
    (println "Skipping existing recipe:" (:name negroni-recipe))
    (do (db/create-cocktail-recipe! negroni-recipe)
        (println "Created recipe:" (:name negroni-recipe)))))

#_(seed!)

(defn -main
  [& _]
  (mount/start #'wine-cellar.db.connection/ds)
  (try (seed!) (finally (mount/stop #'wine-cellar.db.connection/ds))))
