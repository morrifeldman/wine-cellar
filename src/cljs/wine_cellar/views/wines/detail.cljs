(ns wine-cellar.views.wines.detail
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [wine-cellar.views.components :refer [quantity-control]]
            [wine-cellar.views.tasting-notes.form :refer [tasting-note-form]]
            [wine-cellar.views.tasting-notes.list :refer [tasting-notes-list]]
            [wine-cellar.api :as api]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.icons.arrow-back :refer [arrow-back]]))

(defn wine-detail [app-state wine]
  (let [wine-id (:id wine)]
    [paper {:elevation 2 :sx {:p 3 :mb 3}}
     ;; Wine title and basic info
     [typography {:variant "h4" :component "h2" :sx {:mb 2}}
      (str (:producer wine) (when-let [name (:name wine)] (str " - " name)))]

     [grid {:container true :spacing 2 :sx {:mb 3}}
      ;; Vintage, region, etc.
      [grid {:item true :xs 12 :md 6}
       [typography {:variant "body1"}
        [box {:component "span" :sx {:fontWeight "bold"}} "Vintage: "]
        (:vintage wine)]]

      [grid {:item true :xs 12 :md 6}
       [typography {:variant "body1"}
        [box {:component "span" :sx {:fontWeight "bold"}} "Region: "]
        (:region wine)
        (when-let [aoc (:aoc wine)] (str " - " aoc))
        (when-let [classification (:classification wine)]
          (str " - " classification))]]

      ;; Styles
      [grid {:item true :xs 12 :md 6}
       [typography {:variant "body1"}
        [box {:component "span" :sx {:fontWeight "bold"}} "Styles: "]
        (str/join ", " (:styles wine))]]

      ;; Location and quantity
      [grid {:item true :xs 12 :md 6}
       [box {:display "flex" :alignItems "center"}
        [box {:component "span" :mr 2}
         [typography {:variant "body1" :display "inline"}
          [box {:component "span" :sx {:fontWeight "bold"}} "Location: "]
          (:location wine)]]

        ;; Updated quantity display - all on one line
        [box {:display "flex" :alignItems "center"}
         [typography {:variant "body1" :component "span"}
          [box {:component "span" :sx {:fontWeight "bold"}} "Quantity: "]]
         [quantity-control app-state (:id wine) (:quantity wine)]]]] 
      ]

     ;; Price
     (when (:price wine)
       [box {:sx {:mb 3}}
        [typography {:variant "body1"}
         [box {:component "span" :sx {:fontWeight "bold"}} "Price: "]
         (gstring/format "$%.2f" (:price wine))]])

     ;; Tasting notes section
     [tasting-notes-list app-state wine-id]
     [tasting-note-form app-state wine-id]]))

(defn wine-details-section [app-state]
  (when-let [selected-wine-id (:selected-wine-id @app-state)]
    (when-let [selected-wine (first (filter #(= (:id %) selected-wine-id)
                                           (:wines @app-state)))]
      [box {:sx {:mb 3}}
       [wine-detail app-state selected-wine]
       [button
        {:variant "contained"
         :color "primary"
         :start-icon (r/as-element [arrow-back])
         :sx {:mt 2}
         :onClick #(do
                     (swap! app-state dissoc :selected-wine-id :tasting-notes)
                     (swap! app-state assoc :new-tasting-note {}))}
        "Back to List"]])))
