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
    :subcategory "London Dry"
    :distillery "William Grant & Sons"
    :country "Scotland"
    :proof 83
    :quantity 1
    :price 38.00M}
   {:name "Campari"
    :category "liqueur"
    :subcategory "Bitter"
    :distillery "Campari Group"
    :country "Italy"
    :proof 48
    :quantity 1
    :price 32.00M}
   {:name "Carpano Antica Formula"
    :category "vermouth"
    :subcategory "Sweet"
    :distillery "Fratelli Branca"
    :country "Italy"
    :proof 33
    :quantity 1
    :price 28.00M}
   {:name "Dolin Dry"
    :category "vermouth"
    :subcategory "Dry"
    :distillery "Dolin"
    :country "France"
    :proof 36
    :quantity 1
    :price 15.00M}
   {:name "Cocchi Americano"
    :category "vermouth"
    :subcategory "Bianco"
    :distillery "Cocchi"
    :country "Italy"
    :proof 33
    :quantity 1
    :price 22.00M}
   {:name "Appleton Estate 12 Year"
    :category "rum"
    :subcategory "Aged"
    :distillery "Appleton Estate"
    :country "Jamaica"
    :proof 86
    :quantity 1
    :price 35.00M}
   {:name "Plantation OFTD"
    :category "rum"
    :subcategory "Overproof"
    :distillery "Plantation"
    :country "Barbados"
    :proof 138
    :quantity 1
    :price 28.00M}
   {:name "Clément VSOP"
    :category "rum"
    :subcategory "Rhum Agricole"
    :distillery "Habitation Clément"
    :country "Martinique"
    :proof 80
    :quantity 1
    :price 40.00M}
   {:name "Pernod Absinthe"
    :category "other"
    :subcategory "Absinthe"
    :distillery "Pernod"
    :country "France"
    :proof 136
    :quantity 1
    :price 45.00M}
   {:name "Nardini Grappa"
    :category "brandy"
    :subcategory "Grappa"
    :distillery "Nardini"
    :country "Italy"
    :proof 100
    :quantity 1
    :price 35.00M}])

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
