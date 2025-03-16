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
    [paper {:elevation 2
            :sx {:p 4
                 :mb 3
                 :borderRadius 2
                 :position "relative"
                 :overflow "hidden"
                 :backgroundImage "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))"}}
     ;; Wine title and basic info
     [box {:sx {:mb 3 :pb 2 :borderBottom "1px solid rgba(0,0,0,0.08)"}}
      [typography {:variant "h4"
                   :component "h2"
                   :sx {:mb 1
                        :color "primary.main"}}
       (str (:producer wine) (when-let [name (:name wine)] (str " - " name)))]

      [typography {:variant "subtitle1" :color "text.secondary"}
       (str (:vintage wine) " • " (:region wine)
            (when-let [aoc (:aoc wine)] (str " • " aoc)))]]

     [grid {:container true :spacing 3 :sx {:mb 4}}
      ;; Classification
      (when-let [classification (:classification wine)]
        [grid {:item true :xs 12 :md 6}
         [paper {:elevation 0
                 :sx {:p 2
                      :bgcolor "rgba(0,0,0,0.02)"
                      :borderRadius 1}}
          [typography {:variant "body2" :color "text.secondary"} "Classification"]
          [typography {:variant "body1"} classification]]])

      ;; Styles
      [grid {:item true :xs 12 :md 6}
       [paper {:elevation 0
               :sx {:p 2
                    :bgcolor "rgba(0,0,0,0.02)"
                    :borderRadius 1}}
        [typography {:variant "body2" :color "text.secondary"} "Styles"]
        [typography {:variant "body1"} (str/join ", " (:styles wine))]]]

      ;; Location
      [grid {:item true :xs 12 :md 6}
       [paper {:elevation 0
               :sx {:p 2
                    :bgcolor "rgba(0,0,0,0.02)"
                    :borderRadius 1}}
        [typography {:variant "body2" :color "text.secondary"} "Location"]
        [typography {:variant "body1"} (:location wine)]]]

      ;; Quantity
      [grid {:item true :xs 12 :md 6}
       [paper {:elevation 0
               :sx {:p 2
                    :bgcolor "rgba(0,0,0,0.02)"
                    :borderRadius 1}}
        [typography {:variant "body2" :color "text.secondary"} "Quantity"]
        [box {:display "flex" :alignItems "center" :mt 1}
         [quantity-control app-state (:id wine) (:quantity wine)]]]]

      ;; Price
      (when (:price wine)
        [grid {:item true :xs 12 :md 6}
         [paper {:elevation 0
                 :sx {:p 2
                      :bgcolor "rgba(0,0,0,0.02)"
                      :borderRadius 1}}
          [typography {:variant "body2" :color "text.secondary"} "Price"]
          [typography {:variant "body1" :fontWeight "medium"}
           (gstring/format "$%.2f" (:price wine))]]])]

     ;; Tasting notes section
     [box {:sx {:mt 4}}
      [typography {:variant "h5"
                   :component "h3"
                   :sx {:mb 3
                        :pb 1
                        :borderBottom "1px solid rgba(0,0,0,0.08)"
                        :color "primary.main"}}
       "Tasting Notes"]
      [tasting-notes-list app-state wine-id]
      [tasting-note-form app-state wine-id]]]))

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
                     (swap! app-state assoc :new-tasting-note {})
                     (api/fetch-wines app-state))}
        "Back to List"]])))
