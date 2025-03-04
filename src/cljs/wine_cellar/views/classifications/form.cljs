(ns wine-cellar.views.classifications.form
  (:require [reagent.core :as r]
            [wine-cellar.views.components :refer [smart-field multi-select-field form-field-style]]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]))

(defn classification-form [app-state]
  (let [new-class (or (:new-classification @app-state) {})]
    [paper {:elevation 2 :sx {:p 3 :mb 3 :bgcolor "background.paper"}}
     [typography {:variant "h6" :component "h3" :sx {:mb 2}} "Add New Classification"]
     [grid {:container true :spacing 2}
      
      ;; Required fields
      [grid {:item true :xs 12 :md 4}
       [smart-field app-state [:new-classification :country] :required true]]
      
      [grid {:item true :xs 12 :md 4}
       [smart-field app-state [:new-classification :region] :required true]]
      
      ;; Optional fields
      [grid {:item true :xs 12 :md 4}
       [smart-field app-state [:new-classification :aoc] :label "AOC (optional)"]]
      
      [grid {:item true :xs 12 :md 4}
       [smart-field app-state [:new-classification :classification] :label "Classification (optional)"]]
      
      [grid {:item true :xs 12 :md 4}
       [smart-field app-state [:new-classification :vineyard] :label "Vineyard (optional)"]]
      
      ;; Levels multi-select (special case - can't use smart-field)
      [grid {:item true :xs 12 :md 4}
       [multi-select-field
        {:label "Allowed Levels"
         :value (:levels new-class [])
         :required false
         :options common/wine-levels
         :on-change #(swap! app-state assoc-in [:new-classification :levels] %)}]]
      
      ;; Form buttons
      [grid {:item true :xs 12 :sx {:display "flex" :justifyContent "flex-end" :mt 2}}
       [button
        {:variant "outlined"
         :color "secondary"
         :onClick #(swap! app-state assoc :creating-classification? false)
         :sx {:mr 2}}
        "Cancel"]
       [button
        {:variant "contained"
         :color "primary"
         :disabled (or (empty? (:country new-class))
                       (empty? (:region new-class)))
         :onClick #(api/create-classification app-state (:new-classification @app-state))}
        "Create Classification"]]]]))
