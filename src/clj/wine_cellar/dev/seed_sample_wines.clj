(ns wine-cellar.dev.seed-sample-wines
  "Populate the database with a diverse set of wines for local testing."
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [wine-cellar.db.api :as db]
            [wine-cellar.db.connection :refer [ds db-opts]]))

(def seed-note-source "Seed Sample Data")

(def sample-wines
  [{:wine {:producer "Clos des Papes"
           :name "Châteauneuf-du-Pape"
           :vintage 2020
           :country "France"
           :region "Rhône Valley"
           :style "Red"
           :closure_type "Natural cork"
           :price 95.00M
           :quantity 6
           :original_quantity 12
           :purchase_date "2021-11-05"
           :drink_from_year 2024
           :drink_until_year 2034
           :location "A1"
           :purveyor "K&L Wines"
           :tasting_window_commentary "Brimming with garrigue and spice; already approachable."}
    :varieties [{:name "Grenache" :percentage 60.0M}
                {:name "Syrah" :percentage 20.0M}
                {:name "Mourvèdre" :percentage 20.0M}]
    :tasting-notes [{:tasting_date "2024-03-18"
                     :rating 97
                     :notes "Silky red fruit, dried herbs and a long mineral finish."}
                    {:tasting_date "2025-07-18"
                     :rating 96
                     :notes "WSET grid practice note highlighting the wine's layered Grenache-led profile."
                     :wset_data {:note_type "wset_level_3"
                                 :version "1.0"
                                 :wset_wine_style "RED"
                                 :appearance {:clarity "CLEAR"
                                              :colour :medium-garnet
                                              :intensity :medium
                                              :other_observations "Clear core with a faint brick rim."}
                                 :nose {:condition "CLEAN"
                                        :intensity "PRONOUNCED"
                                        :development "DEVELOPING"
                                        :aroma-characteristics {:primary {:red-fruit ["RED CHERRY" "RED PLUM"]
                                                                         :black-fruit ["BLACKBERRY"]
                                                                         :herbal ["LAVENDER"]
                                                                         :pungent-spice ["BLACK/WHITE PEPPER"]}
                                                                :secondary {:oak ["CEDAR" "SMOKE"]}
                                                                :tertiary {:bottle-age ["LEATHER" "FOREST FLOOR"]}}
                                        :other_observations "Hints of dried lavender lift the aromatics."}
                                 :palate {:sweetness "DRY"
                                          :acidity "MEDIUM+"
                                          :tannin "MEDIUM"
                                          :alcohol "MEDIUM+"
                                          :body "MEDIUM+"
                                          :flavor-intensity "PRONOUNCED"
                                          :finish "LONG"
                                          :flavor-characteristics {:primary {:red-fruit ["RED PLUM" "RED CHERRY"]
                                                                             :black-fruit ["BLACKBERRY"]
                                                                             :herbal ["LAVENDER"]}
                                                                   :secondary {:oak ["CEDAR" "SMOKE"]}
                                                                   :tertiary {:bottle-age ["LEATHER" "FOREST FLOOR"]}}
                                          :other_observations "Supple tannins with a warm garrigue finish."}
                                 :conclusions {:quality-level "OUTSTANDING"
                                               :readiness "DRINK OR HOLD"
                                               :final_comments "Ideal with lamb roasted with Provençal herbs."}}}]}
   {:wine {:producer "Ridge"
           :name "Monte Bello"
           :vintage 2015
           :country "USA"
           :region "Santa Cruz Mountains"
           :style "Red"
           :closure_type "Colmated cork"
           :price 285.00M
           :quantity 2
           :original_quantity 6
           :purchase_date "2020-10-12"
           :drink_from_year 2023
           :drink_until_year 2040
           :location "A2"
           :purveyor "Winestone"
           :tasting_window_commentary "Decant for layered cassis, cedar, and graphite."}
    :varieties [{:name "Cabernet Sauvignon" :percentage 72.0M}
                {:name "Merlot" :percentage 16.0M}
                {:name "Petit Verdot" :percentage 8.0M}
                {:name "Cabernet Franc" :percentage 4.0M}]
    :tasting-notes [{:tasting_date "2024-02-11"
                     :rating 96
                     :notes "Classic mountain cab; dense blackberry, cedar and violets."}]}
   {:wine {:producer "Cloudy Bay"
           :name "Sauvignon Blanc"
           :vintage 2023
           :country "New Zealand"
           :region "Marlborough"
           :style "White"
           :closure_type "Screw cap"
           :price 34.00M
           :quantity 5
           :original_quantity 6
           :purchase_date "2024-01-08"
           :drink_from_year 2023
           :drink_until_year 2026
           :location "B1"
           :purveyor "Total Wine"
           :tasting_window_commentary "Vibrant and zesty; best slightly chilled."}
    :varieties [{:name "Sauvignon Blanc" :percentage 100.0M}]
    :tasting-notes [{:tasting_date "2024-04-02"
                     :rating 92
                     :notes "Passionfruit, lime zest and a crunchy saline finish."}
                    {:tasting_date "2025-07-12"
                     :rating 93
                     :notes "Structured WSET tasting grid note capturing the wine's citrus-herbal drive."
                     :wset_data {:note_type "wset_level_3"
                                 :version "1.0"
                                 :wset_wine_style "WHITE"
                                 :appearance {:clarity "CLEAR"
                                              :colour :medium-straw
                                              :intensity :medium
                                              :other_observations "Fine legs; no deposit."}
                                 :nose {:condition "CLEAN"
                                        :intensity "MEDIUM+"
                                        :development "YOUTHFUL"
                                        :aroma-characteristics {:primary {:citrus-fruit ["GRAPEFRUIT" "LEMON"]
                                                                         :tropical-fruit ["PASSION FRUIT"]
                                                                         :herbaceous ["BLACKCURRANT LEAF" "GRASS"]}
                                                                :secondary {:yeast ["BREAD DOUGH"]}}
                                        :other_observations "Subtle wet-stone accent."}
                                 :palate {:sweetness "DRY"
                                          :acidity "HIGH"
                                          :alcohol "MEDIUM"
                                          :body "MEDIUM"
                                          :flavor-intensity "MEDIUM+"
                                          :finish "MEDIUM+"
                                          :flavor-characteristics {:primary {:citrus-fruit ["GRAPEFRUIT" "LIME (JUICE OR ZEST?)"]
                                                                             :tropical-fruit ["PASSION FRUIT"]
                                                                             :herbaceous ["BLACKCURRANT LEAF"]}
                                                                   :secondary {:yeast ["BREAD"]}}
                                          :other_observations "Piercing acidity; saline snap."}
                                 :conclusions {:quality-level "VERY GOOD"
                                               :readiness "DRINK OR HOLD"
                                               :final_comments "Perfect with goat cheese and citrus-dressed greens."}}}]}
   {:wine {:producer "Billecart-Salmon"
           :name "Brut Rosé"
           :vintage nil
           :country "France"
           :region "Champagne"
           :style "Rosé Sparkling"
           :closure_type "Crown cap"
           :price 80.00M
           :quantity 3
           :original_quantity 3
           :purchase_date "2022-12-01"
           :drink_from_year 2022
           :drink_until_year 2028
           :disgorgement_year 2021
           :location "B2"
           :purveyor "Envoyer"
           :tasting_window_commentary "Fresh strawberries, chalk, and a creamy mousse."}
    :varieties [{:name "Chardonnay" :percentage 40.0M}
                {:name "Pinot Noir" :percentage 30.0M}
                {:name "Pinot Meunier" :percentage 30.0M}]
   :tasting-notes [{:tasting_date "2024-02-14"
                    :rating 94
                    :notes "Wild berries, citrus peel and brioche. Ultra-fine bubbles."}]}
   {:wine {:producer "Jacquesson"
           :name "Cuvée 746"
           :vintage nil
           :country "France"
           :region "Champagne"
           :style "Sparkling"
           :closure_type "Natural cork"
           :price 78.00M
           :quantity 4
           :original_quantity 6
           :purchase_date "2024-02-18"
           :drink_from_year 2024
           :drink_until_year 2027
           :location "B3"
           :purveyor "Flatiron"
           :disgorgement_year 2023
           :tasting_window_commentary "Disgorged May 2023; tense citrus, chalk and toasted brioche."}
    :varieties [{:name "Chardonnay" :percentage 57.0M}
                {:name "Pinot Noir" :percentage 28.0M}
                {:name "Pinot Meunier" :percentage 15.0M}]
    :tasting-notes [{:tasting_date "2024-05-18"
                     :rating 93
                     :notes "Disgorged 2023-05-12; laser-cut citrus, toasted almond and saline finish."}]}
   {:wine {:producer "Penfolds"
           :name "Bin 389 Cabernet Shiraz"
           :vintage 2019
           :country "Australia"
           :region "South Australia"
           :style "Red"
           :closure_type "Synthetic cork (extruded)"
           :price 65.00M
           :quantity 8
           :original_quantity 12
           :purchase_date "2022-05-20"
           :drink_from_year 2025
           :drink_until_year 2035
           :location "C1"
           :purveyor "Benchmark"
           :tasting_window_commentary "Plush dark fruit with savory spice; share at BBQs."}
    :varieties [{:name "Shiraz" :percentage 51.0M}
                {:name "Cabernet Sauvignon" :percentage 49.0M}]
    :tasting-notes [{:tasting_date "2024-05-01"
                     :rating 93
                     :notes "Black plum, eucalyptus, and mocha carried by bright acids."}]}
   {:wine {:producer "Lapostolle"
           :name "Clos Apalta"
           :vintage 2018
           :country "Chile"
           :region "Colchagua Valley"
           :style "Red"
           :closure_type "Micro-agglomerated cork"
           :price 130.00M
           :quantity 4
           :original_quantity 6
           :purchase_date "2023-03-18"
           :drink_from_year 2024
           :drink_until_year 2038
           :location "C2"
           :purveyor "Flatiron"
           :tasting_window_commentary "Luxurious Carmenère-led blend with velvety tannins."}
    :varieties [{:name "Carmenère" :percentage 64.0M}
                {:name "Cabernet Sauvignon" :percentage 18.0M}
                {:name "Merlot" :percentage 11.0M}
                {:name "Petit Verdot" :percentage 7.0M}]
    :tasting-notes [{:tasting_date "2024-03-22"
                     :rating 95
                     :notes "Cassis, dark chocolate and peppercorn with plush texture."}]}
   {:wine {:producer "Dr. Loosen"
           :name "Ürziger Würzgarten Riesling Kabinett"
           :vintage 2021
           :country "Germany"
           :region "Mosel"
           :style "White"
           :closure_type "Synthetic cork (molded)"
           :price 22.00M
           :quantity 12
           :original_quantity 12
           :purchase_date "2023-07-04"
           :drink_from_year 2023
           :drink_until_year 2030
           :location "D1"
           :purveyor "Skurnik"
           :tasting_window_commentary "Ginger, white peach and slate; off-dry and electric."}
    :varieties [{:name "Riesling" :percentage 100.0M}]
    :tasting-notes [{:tasting_date "2024-04-20"
                     :rating 91
                     :notes "Peach, lime and honeycomb balanced by Mosel lift."}]}
   {:wine {:producer "Taylor Fladgate"
           :name "Vintage Port"
           :vintage 1994
           :country "Portugal"
           :region "Douro Valley"
           :style "Fortified"
           :closure_type "T-top"
           :price 180.00M
           :quantity 1
           :original_quantity 6
           :purchase_date "2012-09-30"
           :drink_from_year 2010
           :drink_until_year 2045
           :location "D2"
           :purveyor "Rare Wine Co."
           :tasting_window_commentary "Decadent dried fig, cocoa and walnut richness."}
    :varieties [{:name "Touriga Nacional" :percentage 45.0M}
                {:name "Touriga Franca" :percentage 30.0M}
                {:name "Tinta Roriz" :percentage 25.0M}]
    :tasting-notes [{:tasting_date "2024-01-12"
                     :rating 98
                     :notes "Profound fig, espresso and spice. Still incredibly youthful."}]}
   {:wine {:producer "Château d'Yquem"
           :name "Sauternes"
           :vintage 2001
           :country "France"
           :region "Sauternes"
           :style "Dessert"
           :closure_type "Natural cork"
           :price 350.00M
           :quantity 1
           :original_quantity 3
           :purchase_date "2019-11-19"
           :drink_from_year 2010
           :drink_until_year 2045
           :location "E1"
           :purveyor "The Wine Club"
           :tasting_window_commentary "Botrytis-laden apricot, saffron and crème brûlée."}
    :varieties [{:name "Sémillon" :percentage 85.0M}
                {:name "Sauvignon Blanc" :percentage 15.0M}]
    :tasting-notes [{:tasting_date "2024-02-02"
                     :rating 99
                     :notes "Layers of apricot, honey, saffron and endless finish."}]}
   {:wine {:producer "Castello di Ama"
           :name "Chianti Classico Gran Selezione San Lorenzo"
           :vintage 2020
           :country "Italy"
           :region "Tuscany"
           :style "Red"
           :closure_type "Agglomerated cork"
           :price 55.00M
           :quantity 9
           :original_quantity 12
           :purchase_date "2023-06-10"
           :drink_from_year 2025
           :drink_until_year 2035
           :location "E2"
           :purveyor "Astor Wines"
           :tasting_window_commentary "Sour cherry, tobacco and savory herbs; vibrant acidity."}
    :varieties [{:name "Sangiovese" :percentage 80.0M}
                {:name "Merlot" :percentage 13.0M}
                {:name "Malvasia Nera" :percentage 7.0M}]
    :tasting-notes [{:tasting_date "2024-05-05"
                     :rating 94
                     :notes "Bright cherry, leather and balsamic depth with fine tannins."}]}
  {:wine {:producer "La Vieille Ferme"
          :name "Blanc"
          :vintage 2022
          :country "France"
          :region "Ventoux"
          :style "White"
          :closure_type "Zork"
          :price 12.00M
          :quantity 15
          :original_quantity 24
          :purchase_date "2023-02-14"
          :drink_from_year 2022
          :drink_until_year 2025
          :location "F1"
          :purveyor "Trader Joe's"
          :tasting_window_commentary "Easy citrus-and-stone-fruit weeknight white."}
   :varieties [{:name "Grenache Blanc" :percentage 40.0M}
               {:name "Bourboulenc" :percentage 30.0M}
               {:name "Ugni Blanc" :percentage 20.0M}
               {:name "Roussanne" :percentage 10.0M}]
   :tasting-notes [{:tasting_date "2024-03-01"
                    :rating 88
                    :notes "Crisp lemon, white flowers and a hint of almond."}]}
   {:wine {:producer "Dominus Estate"
           :name "Proprietary Red"
           :vintage 2021
           :country "USA"
           :region "Napa Valley"
           :style "Red"
           :closure_type "Natural cork"
           :price 320.00M
           :quantity 6
           :original_quantity 6
           :purchase_date "2024-09-15"
           :drink_from_year 2030
           :drink_until_year 2045
           :location "G1"
           :purveyor "Benchmark"
           :tasting_window_commentary "Powerful but tightly wound; cellar for true fireworks."}
    :varieties [{:name "Cabernet Sauvignon" :percentage 85.0M}
                {:name "Cabernet Franc" :percentage 10.0M}
                {:name "Petit Verdot" :percentage 5.0M}]
    :tasting-notes [{:tasting_date "2024-06-10"
                     :rating 97
                     :notes "Inky cassis, pencil shavings and crushed rock—firm structure, needs years."}]}
   {:wine {:producer "Chateau Musar"
           :name "Red"
           :vintage 1998
           :country "Lebanon"
           :region "Bekaa Valley"
           :style "Red"
           :closure_type "Other/Unknown"
           :price 75.00M
           :quantity 2
           :original_quantity 12
           :purchase_date "2008-04-11"
           :drink_from_year 2005
           :drink_until_year 2018
           :location "G2"
           :purveyor "Rare Wine Co."
           :tasting_window_commentary "Aromatically wild and savory; edging past peak but still compelling."}
    :varieties [{:name "Cabernet Sauvignon" :percentage 40.0M}
                {:name "Cinsault" :percentage 30.0M}
                {:name "Carignan" :percentage 30.0M}]
   :tasting-notes [{:tasting_date "2024-05-22"
                     :rating 90
                     :notes "Leather, dried cherry and soy; elegant but fading—enjoy before it slips."}]}
   {:wine {:producer "Weingut Keller"
           :name "Kirchspiel Riesling Grosses Gewächs"
           :vintage 2022
           :country "Germany"
           :region "Rheinhessen"
           :style "White"
           :closure_type "Glass stopper"
           :price 120.00M
           :quantity 6
           :original_quantity 6
           :purchase_date "2024-10-03"
           :drink_from_year 2027
           :drink_until_year 2040
           :location "F2"
           :purveyor "Lyle Fass"
           :tasting_window_commentary "Slate, citrus oil and white tea—holds tight now, patience rewarded."}
    :varieties [{:name "Riesling" :percentage 100.0M}]
    :tasting-notes [{:tasting_date "2024-11-18"
                     :rating 95
                     :notes "Laser-focused lime, crushed stone and hint of smoke; currently coiled."}]}
   {:wine {:producer "Scar of the Sea"
           :name "Chardonnay"
           :vintage 2022
           :country "USA"
           :region "Santa Maria Valley"
           :style "White"
           :closure_type "Technical cork"
           :price nil
           :quantity 7
           :original_quantity 9
           :purchase_date "2024-03-15"
           :drink_from_year 2023
           :drink_until_year 2028
           :location "H1"
           :purveyor "Chambers Street"
           :tasting_window_commentary "Saline, citrus-driven chardonnay; beautifully transparent."}
    :varieties []
    :tasting-notes []}
   {:wine {:producer "Ar.Pe.Pe."
           :name "Rosso di Valtellina"
           :vintage 2020
           :country "Italy"
           :region "Lombardy"
           :style "Red"
           :closure_type "Natural cork"
           :price 32.00M
           :quantity 10
           :original_quantity 12
           :purchase_date "2023-09-02"
           :location "H2"
           :purveyor "Flatiron"
           :tasting_window_commentary "Mountain Nebbiolo with rose petals and alpine herbs."}
    :varieties [{:name "Nebbiolo" :percentage 100.0M}]
    :tasting-notes [{:tasting_date "2024-07-08"
                     :rating 92
                     :notes "Rose petal, sour cherry and savory spice with vibrant tension."}]}])

(defn find-wine-id
  [{:keys [producer name vintage]}]
  (let [vintage-clause (if (some? vintage)
                         [:= :vintage vintage]
                         [:is :vintage nil])
        where [:and [:= :producer producer]
               [:= :name name]
               vintage-clause]]
    (some-> (jdbc/execute-one! ds (sql/format {:select [:id]
                                               :from :wines
                                               :where where})
                               db-opts)
            :id)))

(defn ensure-variety!
  [name]
  (if-let [existing (jdbc/execute-one! ds (sql/format {:select [:id]
                                                        :from :grape_varieties
                                                        :where [:= :name name]})
                                      db-opts)]
    (:id existing)
    (:id (db/create-grape-variety name))))

(defn upsert-wine!
  [{:keys [wine varieties tasting-notes]}]
  (let [prepared (-> wine
                     (update :style identity)
                     (update :level identity)
                     (assoc :verified true))
        wine-id (if-let [existing-id (find-wine-id prepared)]
                  (do
                    (jdbc/execute-one!
                     ds
                     (sql/format {:update :wines
                                  :set (assoc (db/wine->db-wine prepared)
                                              :updated_at [:now])
                                  :where [:= :id existing-id]
                                  :returning :*})
                     db-opts)
                    existing-id)
                  (:id (jdbc/execute-one!
                        ds
                        (sql/format {:insert-into :wines
                                     :values [(db/wine->db-wine prepared)]
                                     :returning :*})
                        db-opts)))]
    ;; Reset grape variety links for deterministic seed data
    (jdbc/execute! ds (sql/format {:delete-from :wine_grape_varieties
                                   :where [:= :wine_id wine-id]}) db-opts)
    (doseq [{:keys [name percentage]} varieties]
      (let [variety-id (ensure-variety! name)]
        (db/associate-grape-variety-with-wine wine-id variety-id percentage)))
    ;; Replace seed tasting notes
    (jdbc/execute! ds (sql/format {:delete-from :tasting_notes
                                   :where [:and [:= :wine_id wine-id]
                                           [:= :source seed-note-source]]}) db-opts)
    (doseq [{:keys [tasting_date rating notes]} tasting-notes]
      (db/create-tasting-note {:wine_id wine-id
                               :tasting_date tasting_date
                               :rating rating
                               :notes notes
                               :is_external false
                               :source seed-note-source}))
    wine-id))

(defn seed!
  []
  (let [ids (map upsert-wine! sample-wines)
        wines (jdbc/execute!
               ds
               (sql/format {:select [:id :producer :name :vintage :quantity :original_quantity]
                            :from :wines
                            :where [:in :id ids]
                            :order-by [[:producer :asc]]})
               db-opts)]
    (println "Seeded" (count ids) "wines:")
    (doseq [row wines]
      (println " •"
               (:producer row)
               (str (:name row)
                    (when-let [v (:vintage row)]
                      (str " " v))
                    ":" )
               (str (:quantity row) "/" (:original_quantity row) " bottles")))
    (println)
    (println "Run stats via summary.cljc -> collection-stats / condensed-summary to explore the data.")))

(defn -main
  [& _]
  (seed!))
