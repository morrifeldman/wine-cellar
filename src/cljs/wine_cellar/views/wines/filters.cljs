(ns wine-cellar.views.wines.filters
  (:require [wine-cellar.utils.formatting :refer
             [unique-countries regions-for-country]]
            [wine-cellar.common :as common]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.collapse :refer [collapse]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.expand-more :refer [expand-more]]
            [reagent-mui.icons.expand-less :refer [expand-less]]
            [wine-cellar.utils.vintage :refer [tasting-window-label]]))

;; TODO: split filter-bar into functions / remove repetition

(defn filter-bar
  [app-state]
  (let [filters (:filters @app-state)
        classifications (:classifications @app-state)
        varieties
        (sort (distinct (mapcat (fn [wine] (map :name (get wine :varieties [])))
                         (:wines @app-state))))]
    [paper
     {:elevation 1 :sx {:p 3 :mb 3 :borderRadius 2 :bgcolor "background.paper"}}
     ;; Header row with buttons aligned to the right
     [box
      {:sx {:display "flex"
            :justifyContent "space-between"
            :alignItems "center"
            :mb 2}}
      [box {:sx {:display "flex" :alignItems "center" :gap 1}}
       [typography {:variant "subtitle1" :sx {:fontWeight "medium"}}
        "Filter Wines"]
       [icon-button
        {:onClick #(swap! app-state update :show-filters? not) :size "small"}
        (if (:show-filters? @app-state) [expand-less] [expand-more])]]
      ;; Button container
      [box {:sx {:display "flex" :gap 1}}
       ;; In Cellar/All History button
       [button
        {:variant "outlined"
         :size "small"
         :color (if (:show-out-of-stock? @app-state) "secondary" "primary")
         :onClick #(swap! app-state update :show-out-of-stock? not)}
        (if (:show-out-of-stock? @app-state) "In Cellar Only" "All History")]
       ;; Clear Filters button
       [button
        {:variant "outlined"
         :size "small"
         :color "secondary"
         :onClick #(swap! app-state assoc
                     :filters
                     {:search ""
                      :country nil
                      :region nil
                      :style nil
                      :variety nil
                      :tasting-window nil})} "Clear Filters"]]]
     [collapse {:in (:show-filters? @app-state) :timeout "auto"}
      [grid {:container true :spacing 3}
       ;; Search field - increased width
       [grid {:item true :xs 12 :md 4}
        [text-field
         {:fullWidth true
          :label "Search"
          :variant "outlined"
          :size "small"
          :placeholder "Name, producer, region..."
          :value (:search filters)
          :onChange #(swap! app-state assoc-in
                       [:filters :search]
                       (.. % -target -value))}]]
       ;; Country dropdown - increased width
       [grid {:item true :xs 12 :md 2}
        [form-control
         {:variant "outlined" :fullWidth true :size "small" :sx {:mt 0}}
         [input-label "Country"]
         [select
          {:value (or (:country filters) "")
           :label "Country"
           :onChange #(swap! app-state assoc-in
                        [:filters :country]
                        (let [v (.. % -target -value)]
                          (when-not (empty? v) v)))}
          [menu-item {:value ""} "All Countries"]
          (for [country (unique-countries classifications)]
            ^{:key country} [menu-item {:value country} country])]]]
       ;; Region dropdown - increased width
       [grid {:item true :xs 12 :md 2}
        [form-control
         {:variant "outlined"
          :fullWidth true
          :size "small"
          :disabled (empty? (:country filters))
          :sx {:mt 0}} [input-label "Region"]
         [select
          {:value (or (:region filters) "")
           :label "Region"
           :onChange #(swap! app-state assoc-in
                        [:filters :region]
                        (let [v (.. % -target -value)]
                          (when-not (empty? v) v)))}
          [menu-item {:value ""} "All Regions"]
          (for [region (regions-for-country classifications (:country filters))]
            ^{:key region} [menu-item {:value region} region])]]]
       ;; Style dropdown - increased width
       [grid {:item true :xs 12 :md 2}
        [form-control
         {:variant "outlined" :fullWidth true :size "small" :sx {:mt 0}}
         [input-label "Style"]
         [select
          {:value (or (:style filters) "")
           :label "Style"
           :onChange #(swap! app-state assoc-in
                        [:filters :style]
                        (let [v (.. % -target -value)]
                          (when-not (empty? v) v)))}
          [menu-item {:value ""} "All Styles"]
          (for [style common/wine-styles]
            ^{:key style} [menu-item {:value style} style])]]]
       ;; Variety dropdown
       [grid {:item true :xs 12 :md 2}
        [form-control
         {:variant "outlined" :fullWidth true :size "small" :sx {:mt 0}}
         [input-label "Variety"]
         [select
          {:value (or (:variety filters) "")
           :label "Variety"
           :onChange #(swap! app-state assoc-in
                        [:filters :variety]
                        (let [v (.. % -target -value)]
                          (when-not (empty? v) v)))}
          [menu-item {:value ""} "All Varieties"]
          (for [variety varieties]
            ^{:key variety} [menu-item {:value variety} variety])]]]
       ;; Tasting Window dropdown - increased width
       [grid {:item true :xs 12 :md 2}
        [form-control
         {:variant "outlined" :fullWidth true :size "small" :sx {:mt 0}}
         [input-label {:sx {:lineHeight 1.2}} "Tasting\nWindow"]
         [select
          {:value (or (:tasting-window filters) "")
           :label "Tasting Window"
           :onChange #(swap! app-state assoc-in
                        [:filters :tasting-window]
                        (let [v (.. % -target -value)]
                          (when-not (empty? v) (keyword v))))}
          [menu-item {:value ""} "All Wines"]
          [menu-item {:value "ready"} (tasting-window-label :ready)]
          [menu-item {:value "too-young"} (tasting-window-label :too-young)]
          [menu-item {:value "too-old"} (tasting-window-label :too-old)]]]]]]]))

