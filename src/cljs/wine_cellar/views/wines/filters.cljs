(ns wine-cellar.views.wines.filters
  (:require [reagent.core :as r]
            [wine-cellar.utils.formatting :refer [unique-countries regions-for-country]]
            [wine-cellar.common :as common]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.material.grid :refer [grid]]))

(def filter-field-style {:width "100%"}) ;; Take full width of grid cell

(defn filter-bar [app-state]
  (let [filters (:filters @app-state)
        classifications (:classifications @app-state)]
    [grid {:container true :spacing 3 :sx {:mb 3 :mt 2}}
     ;; Search field
     [grid {:item true :xs 12 :md 4}
      [text-field 
       {:fullWidth true
        :label "Search wines"
        :variant "outlined"
        :placeholder "Search by name, producer, region..."
        :value (:search filters)
        :onChange #(swap! app-state assoc-in [:filters :search] 
                         (.. % -target -value))}]]
     
     ;; Country dropdown
     [grid {:item true :xs 12 :md 2}
      [form-control
       {:variant "outlined"
        :fullWidth true
        :sx {:mt 0}}
       [input-label "Country"]
       [select
        {:value (or (:country filters) "")
         :label "Country"
         :onChange #(swap! app-state assoc-in [:filters :country]
                          (let [v (.. % -target -value)]
                            (when-not (empty? v) v)))}
        [menu-item {:value ""} "All Countries"]
        (for [country (unique-countries classifications)]
          ^{:key country}
          [menu-item {:value country} country])]]]
     
     ;; Region dropdown
     [grid {:item true :xs 12 :md 2}
      [form-control
       {:variant "outlined"
        :fullWidth true
        :disabled (empty? (:country filters))
        :sx {:mt 0}}
       [input-label "Region"]
       [select
        {:value (or (:region filters) "")
         :label "Region"
         :onChange #(swap! app-state assoc-in [:filters :region]
                          (let [v (.. % -target -value)]
                            (when-not (empty? v) v)))}
        [menu-item {:value ""} "All Regions"]
        (for [region (regions-for-country classifications (:country filters))]
          ^{:key region}
          [menu-item {:value region} region])]]]
     
     ;; Style dropdown
     [grid {:item true :xs 12 :md 2}
      [form-control
       {:variant "outlined"
        :fullWidth true
        :sx {:mt 0}}
       [input-label "Style"]
       [select
        {:value (or (:styles filters) "")
         :label "Style"
         :onChange #(swap! app-state assoc-in [:filters :styles]
                          (let [v (.. % -target -value)]
                            (when-not (empty? v) v)))}
        [menu-item {:value ""} "All Styles"]
        (for [style common/wine-styles]
          ^{:key style}
          [menu-item {:value style} style])]]]
     
     ;; Clear filters button
     [grid {:item true :xs 12 :md 2 :sx {:display "flex" :alignItems "center" :mt 1}}
      [button 
       {:variant "outlined"
        :color "secondary"
        :onClick #(swap! app-state assoc :filters {:search "" :country nil :region nil :styles nil})}
       "Clear Filters"]]]))
