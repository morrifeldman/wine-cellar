(ns wine-cellar.views.wines.filters
  (:require [wine-cellar.utils.formatting :refer [unique-countries regions-for-country]]
            [wine-cellar.common :as common]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]))

(defn filter-bar [app-state]
  (let [filters (:filters @app-state)
        classifications (:classifications @app-state)]
    [paper {:elevation 1
            :sx {:p 3
                 :mb 3
                 :borderRadius 2
                 :bgcolor "background.paper"}}
     [typography {:variant "subtitle1"
                  :sx {:mb 2
                       :fontWeight "medium"}}
      "Filter Wines"]
     [grid {:container true :spacing 3}
      ;; Search field
      [grid {:item true :xs 12 :md 4}
       [text-field
        {:fullWidth true
         :label "Search wines"
         :variant "outlined"
         :size "small"
         :placeholder "Search by name, producer, region..."
         :value (:search filters)
         :onChange #(swap! app-state assoc-in [:filters :search]
                           (.. % -target -value))}]]

      ;; Country dropdown
      [grid {:item true :xs 12 :md 2}
       [form-control
        {:variant "outlined"
         :fullWidth true
         :size "small"
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
         :size "small"
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
         :size "small"
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

      [grid {:item true :xs 12 :md 2 :sx {:display "flex" :alignItems "center"}}
       [button
        {:variant "outlined"
         :size "small"
         :color (if (:show-out-of-stock? @app-state) "secondary" "primary")
         :onClick #(swap! app-state update :show-out-of-stock? not)}
        (if (:show-out-of-stock? @app-state)
          "In Cellar Only"
          "All History")]]

      ;; Clear filters button
      [grid {:item true :xs 12 :md 2 :sx {:display "flex" :alignItems "center"}}
       [button
        {:variant "outlined"
         :size "small"
         :color "secondary"
         :onClick #(swap! app-state assoc :filters {:search "" :country nil :region nil :styles nil})}
        "Clear Filters"]]]]))
