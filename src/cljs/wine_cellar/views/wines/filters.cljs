(ns wine-cellar.views.wines.filters
  (:require [wine-cellar.utils.formatting :refer
             [unique-countries regions-for-country unique-varieties]]
            [wine-cellar.common :as common]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.material.slider :refer [slider]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.collapse :refer [collapse]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.expand-more :refer [expand-more]]
            [reagent-mui.icons.expand-less :refer [expand-less]]
            [wine-cellar.utils.vintage :refer [tasting-window-label]]))

(defn filter-bar
  [app-state]
  (let [filters (:filters @app-state)
        classifications (:classifications @app-state)]
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
        (if (:show-filters? @app-state)
          [expand-less {:sx {:color "text.secondary"}}]
          [expand-more {:sx {:color "text.secondary"}}])]]
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
                      :price-range nil
                      :tasting-window nil
                      :verification nil})} "Clear Filters"]]]
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
           :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
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
           :sx {"& .MuiSelect-icon" {:color (if (empty? (:country filters))
                                              "text.disabled"
                                              "text.secondary")}}
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
           :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
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
           :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
           :onChange #(swap! app-state assoc-in
                        [:filters :variety]
                        (let [v (.. % -target -value)]
                          (when-not (empty? v) v)))}
          [menu-item {:value ""} "All Varieties"]
          (for [variety (unique-varieties (:wines @app-state))]
            ^{:key variety} [menu-item {:value variety} variety])]]]
       ;; Price Range Slider
       [grid {:item true :xs 12 :md 2}
        [box {:sx {:px 2}}
         [typography {:variant "subtitle2" :sx {:mb 1}} "Price Range"]
         [slider
          {:range "true"
           :value (or (:price-range filters) [0 100])
           :min 0
           :max 100
           :step 1
           :marks [{:value 0 :label "$0"} {:value 50 :label "$50"}
                   {:value 100 :label "$100"}]
           :valueLabelDisplay "auto"
           :valueLabelFormat #(str "$" %)
           :size "small"
           :onChange #(let [new-range (vec %2)]
                        (swap! app-state assoc-in
                          [:filters :price-range]
                          (when-not (= new-range [0 100]) new-range)))}]]]
       ;; Tasting Window dropdown - increased width
       [grid {:item true :xs 12 :md 2}
        [form-control
         {:variant "outlined" :fullWidth true :size "small" :sx {:mt 0}}
         [input-label {:sx {:lineHeight 1.2}} "Tasting\nWindow"]
         [select
          {:value (or (:tasting-window filters) "")
           :label "Tasting Window"
           :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
           :onChange #(swap! app-state assoc-in
                        [:filters :tasting-window]
                        (let [v (.. % -target -value)]
                          (when-not (empty? v) (keyword v))))}
          [menu-item {:value ""} "All Wines"]
          [menu-item {:value "ready"} (tasting-window-label :ready)]
          [menu-item {:value "too-young"} (tasting-window-label :too-young)]
          [menu-item {:value "too-old"} (tasting-window-label :too-old)]]]]]
      ;; Verification Status dropdown
      [grid {:item true :xs 12 :md 2}
       [form-control
        {:variant "outlined" :fullWidth true :size "small" :sx {:mt 0}}
        [input-label "Verification"]
        [select
         {:value (or (:verification filters) "")
          :label "Verification"
          :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
          :onChange #(swap! app-state assoc-in
                       [:filters :verification]
                       (let [v (.. % -target -value)]
                         (when-not (empty? v) (keyword v))))}
         [menu-item {:value ""} "All Wines"]
         [menu-item {:value "verified-only"} "Verified Only"]
         [menu-item {:value "unverified-only"} "Unverified Only"]]]]]]))

