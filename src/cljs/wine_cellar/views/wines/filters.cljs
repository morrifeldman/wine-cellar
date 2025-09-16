(ns wine-cellar.views.wines.filters
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [wine-cellar.utils.formatting :refer
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
            [reagent-mui.material.checkbox :refer [checkbox]]
            [reagent-mui.material.form-control-label :refer
             [form-control-label]]
            [reagent-mui.material.form-group :refer [form-group]]
            [reagent-mui.icons.arrow-upward :refer [arrow-upward]]
            [reagent-mui.icons.arrow-downward :refer [arrow-downward]]
            [reagent-mui.material.list-item-text :refer [list-item-text]]
            [wine-cellar.utils.vintage :refer [tasting-window-label]]))

(defn search-field
  [app-state]
  (let [filters (:filters @app-state)]
    [grid {:item true :xs 12 :md 4}
     [text-field
      {:fullWidth true
       :label "Search"
       :variant "outlined"
       :size "small"
       :placeholder "Search all wine properties..."
       :value (:search filters)
       :onChange
       #(swap! app-state assoc-in [:filters :search] (.. % -target -value))}]]))

(defn country-filter
  [app-state classifications]
  (let [filters (:filters @app-state)]
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
                     (let [v (.. % -target -value)] (when-not (empty? v) v)))}
       [menu-item {:value ""} "All Countries"]
       (for [country (unique-countries classifications)]
         ^{:key country} [menu-item {:value country} country])]]]))

(defn region-filter
  [app-state classifications]
  (let [filters (:filters @app-state)]
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
                     (let [v (.. % -target -value)] (when-not (empty? v) v)))}
       [menu-item {:value ""} "All Regions"]
       (for [region (regions-for-country classifications (:country filters))]
         ^{:key region} [menu-item {:value region} region])]]]))

(defn style-filter
  [app-state]
  (let [filters (:filters @app-state)
        selected-styles (let [raw (:styles filters)
                               legacy (:style filters)]
                           (cond
                             (sequential? raw) (vec raw)
                             (some? legacy) [(str legacy)]
                             :else []))
        selected-set (set selected-styles)
        normalize-selection (fn [value]
                              (let [converted (cond
                                                (js/Array.isArray value) (js->clj value)
                                                (sequential? value) value
                                                (nil? value) []
                                                :else value)]
                                (cond
                                  (vector? converted) converted
                                  (sequential? converted) (vec converted)
                                  (nil? converted) []
                                  :else [(str converted)])))
        clear-marker? (fn [value]
                        (or (= value "__clear__")
                            (= value {"__clear__" true})))
        label-id "style-filter-label"
        select-id "style-filter-select"]
    [grid {:item true :xs 12 :md 2}
     [form-control
      {:variant "outlined" :fullWidth true :size "small" :sx {:mt 0}}
      [input-label {:id label-id :shrink true} "Style"]
      [select
       {:multiple true
        :displayEmpty true
        :labelId label-id
        :id select-id
        :value (clj->js selected-styles)
        :label "Style"
        :renderValue
        (fn [selected]
          (let [values (normalize-selection selected)
                cleaned (->> values (remove clear-marker?) (into []))]
            (if (seq cleaned) (str/join ", " cleaned) "All Styles")))
        :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
        :onChange
        (fn [event]
          (let [raw (.. event -target -value)
                values (normalize-selection raw)
                cleaned (if (some clear-marker? values)
                          []
                          (->> values (remove clear-marker?) (into [])))]
            (swap! app-state assoc-in [:filters :styles]
                   (if (seq cleaned) cleaned []))))}
       [menu-item {:value "__clear__"}
        [typography {:variant "body2" :sx {:fontStyle "italic"}}
         "All Styles"]]
       (for [style common/wine-styles]
         ^{:key style}
         [menu-item {:value style}
          [checkbox {:checked (contains? selected-set style)
                     :size "small"
                     :sx {:mr 1}}]
          [list-item-text {:primary style}]])]]]))

(defn variety-filter
  [app-state]
  (let [filters (:filters @app-state)
        selected-varieties (let [raw (:varieties filters)
                                  legacy (:variety filters)]
                              (cond
                                (sequential? raw) (vec raw)
                                (some? legacy) [(str legacy)]
                                :else []))
        selected-set (set selected-varieties)
        normalize-selection (fn [value]
                              (let [converted (cond
                                                (js/Array.isArray value) (js->clj value)
                                                (sequential? value) value
                                                (nil? value) []
                                                :else value)]
                                (cond
                                  (vector? converted) converted
                                  (sequential? converted) (vec converted)
                                  (nil? converted) []
                                  :else [(str converted)])))
        clear-marker? (fn [value]
                        (or (= value "__clear__")
                            (= value {"__clear__" true})))
        label-id "variety-filter-label"
        select-id "variety-filter-select"
        varieties (sort (unique-varieties (:wines @app-state)))]
    [grid {:item true :xs 12 :md 2}
     [form-control
      {:variant "outlined" :fullWidth true :size "small" :sx {:mt 0}}
      [input-label {:id label-id :shrink true} "Variety"]
      [select
       {:multiple true
        :displayEmpty true
        :labelId label-id
        :id select-id
        :value (clj->js selected-varieties)
        :label "Variety"
        :renderValue
        (fn [selected]
          (let [values (normalize-selection selected)
                cleaned (->> values (remove clear-marker?) (into []))]
            (if (seq cleaned) (str/join ", " cleaned) "All Varieties")))
        :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
        :onChange
        (fn [event]
          (let [raw (.. event -target -value)
                values (normalize-selection raw)
                cleaned (if (some clear-marker? values)
                          []
                          (->> values (remove clear-marker?) (into [])))]
            (swap! app-state
                   (fn [state]
                     (-> state
                         (assoc-in [:filters :varieties] (if (seq cleaned) cleaned []))
                         (assoc-in [:filters :variety] nil))))))}
       [menu-item {:value "__clear__"}
        [typography {:variant "body2" :sx {:fontStyle "italic"}}
         "All Varieties"]]
       (for [variety varieties]
         ^{:key variety}
         [menu-item {:value variety}
          [checkbox {:checked (contains? selected-set variety)
                     :size "small"
                     :sx {:mr 1}}]
          [list-item-text {:primary variety}]])]]]))

(defn price-range-filter
  [app-state]
  (let [filters (:filters @app-state)]
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
                       (when-not (= new-range [0 100]) new-range)))}]]]))

(defn tasting-window-filter
  [app-state]
  (let [filters (:filters @app-state)]
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
       [menu-item {:value "too-old"} (tasting-window-label :too-old)]]]]))

(defn verification-filter
  [app-state]
  (let [filters (:filters @app-state)]
    (when (:show-verification-checkboxes? @app-state)
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
         [menu-item {:value "unverified-only"} "Unverified Only"]]]])))

(defn column-filter
  [app-state]
  (let [filters (:filters @app-state)
        selected-columns (or (:columns filters) #{})
        columns ["A" "B" "C" "D" "E" "F" "G" "H" "I" "J"]]
    [grid {:item true :xs 12}
     [box {:sx {:px 1}}
      [typography {:variant "subtitle2" :sx {:mb 1}} "Cellar Columns"]
      [form-group {:row true :sx {:gap 1}}
       (for [column columns]
         ^{:key column}
         [form-control-label
          {:control (r/as-element [checkbox
                                   {:checked (contains? selected-columns column)
                                    :size "small"
                                    :onChange
                                    #(let [checked (.. % -target -checked)]
                                       (swap! app-state update-in
                                         [:filters :columns]
                                         (fn [cols]
                                           (let [col-set (or cols #{})]
                                             (if checked
                                               (conj col-set column)
                                               (disj col-set column))))))}])
           :label column
           :sx {:mr 1 :mb 0}}])]]]))


(defn filter-header
  [app-state]
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
                   :styles []
                   :style nil
                   :varieties []
                   :variety nil
                   :price-range nil
                   :tasting-window nil
                   :verification nil
                   :columns #{}})} "Clear Filters"]]])

(defn filter-bar
  [app-state]
  (let [classifications (:classifications @app-state)]
    [paper {:elevation 1 :sx {:p 3 :mb 3 :borderRadius 2}}
     [filter-header app-state] [box {:sx {:mt 2}} [search-field app-state]]
     [collapse {:in (:show-filters? @app-state) :timeout "auto"}
      [grid {:container true :spacing 3}
       [country-filter app-state classifications]
       [region-filter app-state classifications] [style-filter app-state]
       [variety-filter app-state] [price-range-filter app-state]
       [tasting-window-filter app-state] [verification-filter app-state]
       [column-filter app-state]]]
     ;; Sort controls - always visible
     [box {:sx {:mt 2 :pt 2 :borderTop "1px solid rgba(0,0,0,0.08)"}}
      [box {:sx {:display "flex" :alignItems "center" :gap 2}}
       [typography
        {:variant "subtitle2"
         :sx {:height "40px" :display "flex" :alignItems "center"}} "Sort by:"]
       [form-control {:variant "outlined" :size "small" :sx {:minWidth 180}}
        [select
         {:value (let [sort-state (:sort @app-state)
                       current-field (:field sort-state)]
                   (if current-field (name current-field) "producer"))
          :size "small"
          :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
          :onChange #(swap! app-state update
                       :sort
                       (fn [sort]
                         (let [new-field (keyword (.. % -target -value))]
                           (if (= new-field (:field sort))
                             sort
                             {:field new-field :direction :asc}))))}
         [menu-item {:value "location"} "Location"]
         [menu-item {:value "producer"} "Producer"]
         [menu-item {:value "name"} "Name"]
         [menu-item {:value "vintage"} "Vintage"]
         [menu-item {:value "region"} "Region"]
         [menu-item {:value "latest_internal_rating"} "Internal Rating"]
         [menu-item {:value "average_external_rating"} "External Rating"]
         [menu-item {:value "quantity"} "Quantity"]
         [menu-item {:value "price"} "Price"]
         [menu-item {:value "alcohol_percentage"} "Alcohol Percentage"]
         [menu-item {:value "drink_from_year"} "Drinking Window Open"]
         [menu-item {:value "drink_until_year"} "Drinking Window Close"]
         [menu-item {:value "created_at"} "Date Added"]
         [menu-item {:value "updated_at"} "Last Updated"]]]
       [icon-button
        {:size "small"
         :onClick #(swap! app-state update-in
                     [:sort :direction]
                     (fn [dir] (if (= :asc dir) :desc :asc)))
         :sx {:color "text.secondary"}}
        (let [sort-state (:sort @app-state)
              current-direction (:direction sort-state)]
          (if (= current-direction :asc) [arrow-upward] [arrow-downward]))]]]]))
