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
            [reagent-mui.material.tooltip :refer [tooltip]]
            [reagent-mui.icons.expand-more :refer [expand-more]]
            [reagent-mui.icons.expand-less :refer [expand-less]]
            [reagent-mui.icons.history :refer [history]]
            [reagent-mui.icons.restart-alt :refer [restart-alt]]
            [reagent-mui.icons.wine-bar :refer [wine-bar]]
            [reagent-mui.material.checkbox :refer [checkbox]]
            [reagent-mui.material.form-control-label :refer
             [form-control-label]]
            [reagent-mui.material.form-group :refer [form-group]]
            [reagent-mui.icons.sort :refer [sort] :rename {sort sort-icon}]
            [reagent-mui.material.popover :refer [popover]]
            [reagent-mui.material.list-item-text :refer [list-item-text]]
            [wine-cellar.utils.vintage :refer [tasting-window-label]]
            [wine-cellar.state :as app-state-core]))

(defn- fmt-n [v] (.toLocaleString (js/Number. (or v 0)) "en-US"))

(def ^:private sort-labels
  {"location" "Location"
   "producer" "Producer"
   "name" "Name"
   "vintage" "Vintage"
   "region" "Region"
   "latest_internal_rating" "Internal Rating"
   "average_external_rating" "External Rating"
   "quantity" "Quantity"
   "price" "Price"
   "alcohol_percentage" "Alcohol %"
   "drink_from_year" "Win. Open"
   "drink_until_year" "Win. Close"
   "created_at" "Date Added"
   "updated_at" "Last Updated"})

(defn search-field
  [app-state]
  (let [filters (:filters @app-state)]
    [text-field
     {:fullWidth true
      :variant "outlined"
      :size "small"
      :placeholder "Search…"
      :value (:search filters)
      :sx {"& .MuiOutlinedInput-root"
           {"& fieldset" {:borderColor "divider" :opacity 0.8}
            "&:hover fieldset" {:borderColor "text.secondary" :opacity 1}
            "&.Mui-focused fieldset" {:borderColor "primary.main" :opacity 1}}}
      :onChange
      #(swap! app-state assoc-in [:filters :search] (.. % -target -value))}]))

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

(defn style-selector
  [app-state & [{:keys [sx] :as opts}]]
  (let [filters (:filters @app-state)
        selected-styles (let [raw (:styles filters)
                              legacy (:style filters)]
                          (cond (sequential? raw) (vec raw)
                                (some? legacy) [(str legacy)]
                                :else []))
        selected-set (set selected-styles)
        normalize-selection (fn [value]
                              (let [converted (cond (js/Array.isArray value)
                                                    (js->clj value)
                                                    (sequential? value) value
                                                    (nil? value) []
                                                    :else value)]
                                (cond (vector? converted) converted
                                      (sequential? converted) (vec converted)
                                      (nil? converted) []
                                      :else [(str converted)])))
        clear-marker? (fn [value]
                        (or (= value "__clear__") (= value {"__clear__" true})))
        select-id "style-filter-select"]
    [form-control
     {:variant "outlined"
      :size "small"
      :sx (merge {:mt 0 :minWidth 120} sx)
      :fullWidth (:fullWidth opts)}
     [select
      {:multiple true
       :displayEmpty true
       :id select-id
       :value (clj->js selected-styles)
       :renderValue
       (fn [selected]
         (let [values (normalize-selection selected)
               cleaned (->> values
                            (remove clear-marker?)
                            (into []))]
           (if (seq cleaned) (str/join ", " cleaned) "All Styles")))
       :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
       :onChange (fn [event]
                   (let [raw (.. event -target -value)
                         values (normalize-selection raw)
                         cleaned (if (some clear-marker? values)
                                   []
                                   (->> values
                                        (remove clear-marker?)
                                        (into [])))]
                     (swap! app-state assoc-in
                       [:filters :styles]
                       (if (seq cleaned) cleaned []))))}
      [menu-item {:value "__clear__"}
       [typography {:variant "body2" :sx {:fontStyle "italic"}} "All Styles"]]
      (for [style common/wine-styles]
        ^{:key style}
        [menu-item {:value style}
         [checkbox
          {:checked (contains? selected-set style) :size "small" :sx {:mr 1}}]
         [list-item-text {:primary style}]])]]))

(defn variety-filter
  [app-state]
  (let [filters (:filters @app-state)
        selected-varieties (let [raw (:varieties filters)
                                 legacy (:variety filters)]
                             (cond (sequential? raw) (vec raw)
                                   (some? legacy) [(str legacy)]
                                   :else []))
        selected-set (set selected-varieties)
        normalize-selection (fn [value]
                              (let [converted (cond (js/Array.isArray value)
                                                    (js->clj value)
                                                    (sequential? value) value
                                                    (nil? value) []
                                                    :else value)]
                                (cond (vector? converted) converted
                                      (sequential? converted) (vec converted)
                                      (nil? converted) []
                                      :else [(str converted)])))
        clear-marker? (fn [value]
                        (or (= value "__clear__") (= value {"__clear__" true})))
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
                cleaned (->> values
                             (remove clear-marker?)
                             (into []))]
            (if (seq cleaned) (str/join ", " cleaned) "All Varieties")))
        :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
        :onChange
        (fn [event]
          (let [raw (.. event -target -value)
                values (normalize-selection raw)
                cleaned (if (some clear-marker? values)
                          []
                          (->> values
                               (remove clear-marker?)
                               (into [])))]
            (swap! app-state (fn [state]
                               (-> state
                                   (assoc-in [:filters :varieties]
                                             (if (seq cleaned) cleaned []))
                                   (assoc-in [:filters :variety] nil))))))}
       [menu-item {:value "__clear__"}
        [typography {:variant "body2" :sx {:fontStyle "italic"}}
         "All Varieties"]]
       (for [variety varieties]
         ^{:key variety}
         [menu-item {:value variety}
          [checkbox
           {:checked (contains? selected-set variety)
            :size "small"
            :sx {:mr 1}}] [list-item-text {:primary variety}]])]]]))

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
  ([app-state count-info] (filter-header app-state count-info nil))
  ([app-state count-info {:keys [compact?]}]
   (let [state @app-state
         gap (if compact? 0.4 0.75)
         button-gap (if compact? 0.5 {:xs 0.5 :md 1})
         button-color (if (:show-out-of-stock? state) "secondary" "primary")
         selected-count (count (or (:selected-wine-ids state) #{}))
         show-selected? (and (:show-selected-wines? state)
                             (pos? selected-count))
         expand-btn [icon-button
                     {:onClick #(swap! app-state update :show-filters? not)
                      :size "small"
                      :sx {:color "text.secondary" :p 0.5 :alignSelf "center"}}
                     (if (:show-filters? state) [expand-less] [expand-more])]
         history-btn [box {:sx {:alignSelf "center" :display "flex"}}
                      [tooltip
                       {:title (if (:show-out-of-stock? state)
                                 "Show In Cellar Only"
                                 "Show All History")}
                       [icon-button
                        {:size "small"
                         :color button-color
                         :onClick
                         #(swap! app-state update :show-out-of-stock? not)}
                        [history {:fontSize "small"}]]]]
         clear-btn [box {:sx {:alignSelf "center" :display "flex"}}
                    [tooltip {:title "Clear Filters"}
                     [icon-button
                      {:size "small"
                       :color "secondary"
                       :onClick #(swap! app-state assoc
                                   :filters {:search ""
                                             :country nil
                                             :region nil
                                             :styles []
                                             :style nil
                                             :varieties []
                                             :variety nil
                                             :price-range nil
                                             :tasting-window nil
                                             :verification nil
                                             :columns #{}}
                                   :sort {:field :created_at :direction :desc})}
                      [restart-alt {:fontSize "small"}]]]]
         selection-buttons
         (cond-> []
           (pos? selected-count)
           (conj [button
                  {:variant (if show-selected? "contained" "outlined")
                   :size "small"
                   :color (if show-selected? "primary" "secondary")
                   :onClick (fn []
                              (swap! app-state assoc
                                :show-selected-wines?
                                (not show-selected?)))}
                  (str "Selected (" selected-count ")")])
           (pos? selected-count) (conj [button
                                        {:variant "outlined"
                                         :size "small"
                                         :color "secondary"
                                         :onClick
                                         #(app-state-core/clear-selected-wines!
                                           app-state)} "Clear Selection"]))]
     (if compact?
       [box {:sx {:display "flex" :flexDirection "column" :gap gap :mb 0.75}}
        [box
         {:sx {:display "flex"
               :justifyContent "space-between"
               :alignItems "center"
               :flexWrap "wrap"
               :gap 1}}
         [box {:sx {:display "flex" :alignItems "center" :gap gap}} expand-btn]
         [style-selector app-state {:sx {:width 140}}]]
        (when (:show-filters? state)
          [box {:sx {:display "flex" :flexDirection "column" :gap 0.75 :mt 0.5}}
           [box
            {:sx {:display "flex"
                  :gap button-gap
                  :flexWrap "wrap"
                  :alignItems "center"}} history-btn clear-btn]
           (when (seq selection-buttons)
             (into [box
                    {:sx {:display "flex"
                          :gap button-gap
                          :flexWrap "wrap"
                          :alignItems "center"}}]
                   selection-buttons))])]
       [box {:sx {:display "flex" :flexDirection "column" :gap 0.5}}
        [box
         {:sx {:display "flex" :alignItems "center" :gap 0.75 :flexWrap "wrap"}}
         [box {:sx {:flex 1 :minWidth {:xs 80 :sm 160}}}
          [search-field app-state]] history-btn clear-btn
         (when count-info
           (let [{:keys [visible total]} count-info
                 same? (= visible total)
                 tip (if same?
                       (str (fmt-n total) " wines")
                       (str (fmt-n visible) " of " (fmt-n total) " wines"))]
             [tooltip {:title tip}
              [box
               {:sx {:ml "auto"
                     :display "flex"
                     :alignItems "center"
                     :gap 0.5
                     :cursor "default"}}
               [typography
                {:variant "body2"
                 :color "text.secondary"
                 :component "span"
                 :sx {:whiteSpace "nowrap"}}
                (if same?
                  (fmt-n total)
                  (str (fmt-n visible) " / " (fmt-n total)))]
               [wine-bar {:fontSize "small" :sx {:color "text.secondary"}}]]]))
         expand-btn]
        (when (seq selection-buttons)
          (into
           [box
            {:sx
             {:display "flex" :gap 0.75 :flexWrap "wrap" :alignItems "center"}}]
           selection-buttons))]))))

(defn- filter-controls-grid
  [app-state classifications spacing]
  [grid {:container true :spacing spacing}
   [country-filter app-state classifications]
   [region-filter app-state classifications] [variety-filter app-state]
   [price-range-filter app-state] [tasting-window-filter app-state]
   [verification-filter app-state] [column-filter app-state]])

(defn- sort-controls-row
  [app-state _count-info {:keys [compact?]}]
  (let [gap (if compact? 1.5 2)]
    [box {:sx {:display "flex" :alignItems "center" :gap gap :flexWrap "wrap"}}
     [form-control
      {:variant "outlined"
       :size "small"
       :sx {:flex 1
            :minWidth (if compact? 160 {:xs 100 :sm 180})
            :maxWidth {:xs 220 :sm 320}}}
      [select
       {:value (let [sort-state (:sort @app-state)
                     current-field (:field sort-state)]
                 (if current-field (name current-field) "producer"))
        :size "small"
        :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
        :onChange #(swap! app-state update
                     :sort
                     (fn [s]
                       (let [new-field (keyword (.. % -target -value))]
                         (if (= new-field (:field s))
                           s
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
     (let [current-direction (get-in @app-state [:sort :direction])]
       [box {:sx {:alignSelf "center" :display "flex"}}
        [tooltip
         {:title (if (= current-direction :asc) "Ascending" "Descending")}
         [icon-button
          {:size "small"
           :onClick #(swap! app-state update-in
                       [:sort :direction]
                       (fn [dir] (if (= :asc dir) :desc :asc)))
           :sx {:color "text.secondary"}}
          [sort-icon
           {:fontSize "small"
            :sx (when (= current-direction :desc)
                  {:transform "scaleY(-1)"})}]]]])
     (when-not compact?
       [box {:sx {:ml {:sm "auto"}}}
        [style-selector app-state {:sx {:width {:xs 100 :sm 130}}}]])]))

(def ^:private chip-btn-sx
  {:display "flex"
   :alignItems "center"
   :gap 0.5
   :px 1
   :py 0.5
   :background "none"
   :border "none"
   :cursor "pointer"
   :color "text.secondary"
   :fontSize "0.8rem"
   :whiteSpace "nowrap"
   "&:hover" {:backgroundColor "action.hover"}})

(defn- sort-filter-chip
  [app-state]
  (r/with-let
   [sort-anchor (r/atom nil) style-anchor (r/atom nil)]
   (let [state @app-state
         sort-state (:sort state)
         field (or (some-> (:field sort-state)
                           name)
                   "created_at")
         direction (or (:direction sort-state) :asc)
         styles (let [raw (:styles (:filters state))]
                  (if (seq raw) (str/join ", " raw) "All Styles"))]
     [box {:sx {:display "flex" :alignItems "center"}}
      [box
       {:sx {:display "flex"
             :alignItems "center"
             :border "1px solid"
             :borderColor "divider"
             :borderRadius 1
             :overflow "hidden"}}
       [box
        {:component "button"
         :onClick #(reset! sort-anchor (.-currentTarget %))
         :sx chip-btn-sx} (get sort-labels field field)]
       [typography
        {:variant "body2" :color "text.disabled" :sx {:userSelect "none"}} "·"]
       [tooltip {:title (if (= direction :asc) "Ascending" "Descending")}
        [box
         {:component "button"
          :onClick #(swap! app-state update-in
                      [:sort :direction]
                      (fn [d] (if (= :asc d) :desc :asc)))
          :sx chip-btn-sx}
         [sort-icon
          {:fontSize "small"
           :sx (when (= direction :desc) {:transform "scaleY(-1)"})}]]]
       [typography
        {:variant "body2" :color "text.disabled" :sx {:userSelect "none"}} "·"]
       [box
        {:component "button"
         :onClick #(reset! style-anchor (.-currentTarget %))
         :sx chip-btn-sx} styles [expand-more {:fontSize "small"}]]]
      [popover
       {:open (boolean @sort-anchor)
        :anchorEl @sort-anchor
        :onClose #(reset! sort-anchor nil)
        :anchorOrigin {:vertical "bottom" :horizontal "left"}
        :transformOrigin {:vertical "top" :horizontal "left"}}
       [box
        {:sx
         {:p 2 :display "flex" :flexDirection "column" :gap 2 :minWidth 240}}
        [form-control {:variant "outlined" :size "small" :fullWidth true}
         [select
          {:value field
           :size "small"
           :sx {"& .MuiSelect-icon" {:color "text.secondary"}}
           :onChange #(swap! app-state update
                        :sort
                        (fn [s]
                          (let [new-field (keyword (.. % -target -value))]
                            (if (= new-field (:field s))
                              s
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
          [menu-item {:value "updated_at"} "Last Updated"]]]]]
      [popover
       {:open (boolean @style-anchor)
        :anchorEl @style-anchor
        :onClose #(reset! style-anchor nil)
        :anchorOrigin {:vertical "bottom" :horizontal "left"}
        :transformOrigin {:vertical "top" :horizontal "left"}}
       [box {:sx {:p 2 :minWidth 200}}
        [style-selector app-state {:fullWidth true}]]]])))

(defn filter-bar
  ([app-state] (filter-bar app-state nil))
  ([app-state count-info] (filter-bar app-state count-info nil))
  ([app-state count-info {:keys [paper-sx]}]
   (let [classifications (:classifications @app-state)]
     [paper
      {:elevation 1
       :sx (merge {:p {:xs 2 :md 3} :mb 3 :borderRadius 2} paper-sx)}
      [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
       [filter-header app-state count-info] [sort-filter-chip app-state]]
      [collapse {:in (:show-filters? @app-state) :timeout "auto"}
       [box {:sx {:pt 1.5}}
        [filter-controls-grid app-state classifications 2]]]])))

(defn compact-filter-bar
  ([app-state] (compact-filter-bar app-state nil))
  ([app-state count-info]
   (let [classifications (:classifications @app-state)]
     [box
      {:sx {:px 1.5
            :py 1
            :mb 1.25
            :border "1px solid"
            :borderColor "divider"
            :borderRadius 2
            :backgroundColor "background.default"}}
      [filter-header app-state count-info {:compact? true}]
      [collapse {:in (:show-filters? @app-state) :timeout "auto"}
       [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
        [box {:sx {:mt 1}} [search-field app-state]]
        [filter-controls-grid app-state classifications 2]
        (sort-controls-row app-state nil {:compact? true})]]])))
