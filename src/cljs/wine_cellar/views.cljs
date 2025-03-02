(ns wine-cellar.views
  (:require [goog.string :as gstring]
            [goog.string.format]
            [clojure.string :as str]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]
            [reagent.core :as r]
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
            [reagent-mui.icons.arrow-back :refer [arrow-back]]
            [reagent-mui.material.table :refer [table]]
            [reagent-mui.material.table-container :refer [table-container]]
            [reagent-mui.material.table-head :refer [table-head]]
            [reagent-mui.material.table-body :refer [table-body]]
            [reagent-mui.material.table-row :refer [table-row]]
            [reagent-mui.material.table-cell :refer [table-cell]]
            [reagent-mui.material.table-sort-label :refer [table-sort-label]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.icons.visibility :refer [visibility]]
            [reagent-mui.icons.delete :refer [delete]]))

(def form-field-style {:min-width "200px" :width "75%"})

(defn mui-select-field [{:keys [label value options required disabled on-change empty-option]}]
  [form-control
   {:variant "outlined"
    :margin "normal"
    :required required
    :disabled disabled
    :sx form-field-style}
   [input-label label]
   [select
    {:value (or value "")
     :label label
     :onChange #(on-change (.. % -target -value))}
    (when empty-option
      [menu-item {:value ""} (or empty-option "Select")])
    (for [[k v] options]
      ^{:key k}
      [menu-item {:value k} v])]])

(defn input-field [{:keys [label type required value on-change min step]}]
  [text-field
   {:label label
    :type type
    :required required
    :value value
    :margin "normal"
    :fullWidth false
    :variant "outlined"
    :sx form-field-style
    :InputProps (cond-> {}
                  min (assoc :min min)
                  step (assoc :step step))
    :on-change #(on-change (.. % -target -value))}])

(defn multi-select-field [{:keys [label value options required on-change]}]
  [form-control {:variant "outlined"
                 :margin "normal"
                 :required required
                 :sx form-field-style}
   [input-label label]
   [select
    {:value (if (vector? value) value [])
     :label label
     :multiple true
     :displayEmpty true
     :onChange #(on-change (js->clj (.. % -target -value)))}
    (for [option options]
      [menu-item {:key option :value option} option])]])

;; Data transformation helpers
(defn unique-countries [classifications]
  (->> classifications
       (map :country)
       distinct
       sort))

(defn regions-for-country [classifications country]
  (->> classifications
       (filter #(= country (:country %)))
       (map :region)
       distinct
       sort))

(defn aocs-for-region [classifications country region]
  (->> classifications
       (filter #(and (= country (:country %))
                    (= region (:region %))))
       (map :aoc)
       (remove nil?)
       distinct
       sort))

(defn classifications-for-aoc [classifications country region aoc]
  (->> classifications
       (filter #(and (= country (:country %))
                    (= region (:region %))
                    (= aoc (:aoc %))))
       (map :classification)
       (remove nil?)
       distinct
       sort))

(defn levels-for-classification [classifications country region aoc classification]
  (or
    (->> classifications
         (filter #(and (= country (:country %))
                      (= region (:region %))
                      (= aoc (:aoc %))
                      (= classification (:classification %))))
         first
         :levels)
    []))

(defn format-label
  "Convert a keyword like :producer or :wine-type to a human-readable label"
  [k]
  (-> (name k)
      (str/replace #"-|_" " ")
      (str/capitalize)))

(defn smart-field
  "A versatile form field that derives its label from the last part of the path"
  [app-state path & {:keys [label type required min max step component]
                    :or {type "text"
                         component input-field}}]
  (let [derived-label (format-label (last path))
        field-label (or label derived-label)
        field-value (get-in @app-state path)
        props (cond-> {:label field-label
                       :value field-value
                       :on-change #(swap! app-state assoc-in path %)}
                type (assoc :type type)
                required (assoc :required required)
                min (assoc :min min)
                max (assoc :max max)
                step (assoc :step step))]
    [component props]))

(defn date-field [{:keys [label required value on-change]}]
  [text-field
   {:label label
    :type "date"
    :required required
    :value value
    :margin "normal"
    :fullWidth false
    :variant "outlined"
    :InputLabelProps {:shrink true} ;; This is the key fix
    :sx form-field-style
    :on-change #(on-change (.. % -target -value))}])

(defn form-section [title]
  [grid {:item true :xs 12}
   [typography {:variant "subtitle1" :sx {:fontWeight "bold" :mt 2}} title]])

(defn smart-select-field
  [app-state path & {:keys [label options disabled on-change empty-option required]
                    :or {required false 
                         disabled false}}]
  (let [derived-label (format-label (last path))
        field-label (or label derived-label)
        field-value (get-in @app-state path)
        on-change-fn (or on-change #(swap! app-state assoc-in path %))]
    [mui-select-field 
     {:label field-label
      :value field-value
      :required required
      :disabled disabled
      :options options
      :empty-option empty-option
      :on-change on-change-fn}]))

(defn wine-form [app-state]
  (let [new-wine (:new-wine @app-state)
        classifications (:classifications @app-state)]
    [paper {:elevation 3 :sx {:p 3 :mb 3}}
     [typography {:variant "h5" :component "h2" :sx {:mb 3}} "Add New Wine"]
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          (api/create-wine app-state
                                           (update new-wine :price js/parseFloat)))}

      [grid {:container true :spacing 2}

       ;; Basic Information Section
       [form-section "Basic Information"]

       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :name]]]

       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :producer]]]

       ;; Styles is special due to multi-select
       [grid {:item true :xs 12 :md 4}
        [multi-select-field {:label "Style"
                             :value (:styles new-wine)
                             :required true
                             :options common/wine-styles
                             :on-change #(swap! app-state assoc-in [:new-wine :styles] %)}]]

       ;; Wine Classification Section
       [form-section "Wine Classification"]

       ;; Country dropdown (still needs custom logic)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :country]
         :required true
         :options (map #(vector % %) (unique-countries classifications))]]

       ;; Region dropdown (dependent on country)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :region]
         :required true
         :disabled (empty? (:country new-wine))
         :options (map #(vector % %) 
                       (regions-for-country classifications (:country new-wine)))]]

       ;; AOC dropdown (dependent on region)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :aoc]
         :disabled (or (empty? (:country new-wine))
                       (empty? (:region new-wine)))
         :options (map #(vector % %)
                       (aocs-for-region classifications
                                        (:country new-wine)
                                        (:region new-wine)))]]

       ;; Classification dropdown (dependent on AOC)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :classification]
         :disabled (or (empty? (:country new-wine))
                       (empty? (:region new-wine))
                       (empty? (:aoc new-wine)))
         :options (map #(vector % %) 
                       (classifications-for-aoc classifications
                                                (:country new-wine)
                                                (:region new-wine)
                                                (:aoc new-wine)))]]

       ;; Level dropdown (dependent on classification)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :level]
         :disabled (or (empty? (:country new-wine))
                       (empty? (:region new-wine))
                       (empty? (:aoc new-wine))
                       (empty? (:classification new-wine)))
         :options (map #(vector % %) 
                       (levels-for-classification
                         classifications
                         (:country new-wine)
                         (:region new-wine)
                         (:aoc new-wine)
                         (:classification new-wine)))
         :on-change #(swap! app-state assoc-in [:new-wine :level]
                            (when-not (empty? %) %))]]

       ;; Vintage with min/max constraints
       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :vintage]
         :type "number"
         :required true
         :min 1900
         :max 2100
         :on-change #(swap! app-state assoc-in [:new-wine :vintage]
                            (js/parseInt %))]]

       ;; Additional Information Section
       [form-section "Additional Information"]

       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :location]
         :required true]]

       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :quantity]
         :type "number"
         :required true
         :min 0
         :on-change #(swap! app-state assoc-in [:new-wine :quantity]
                            (js/parseInt %))]]


       ;; Price with decimal step
       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :price]
         :type "number"
         :required true
         :step "0.01"
         :min "0"
         :value (if (string? (:price new-wine))
                  (:price new-wine)
                  (str (:price new-wine)))]]

       [grid {:item true :xs 12}
        [box {:sx {:display "flex" :justifyContent "flex-end" :mt 2}}
         [button 
          {:type "submit"
           :variant "contained"
           :color "primary"}
          "Add Wine"]]]]]]))

(defn tasting-note-form [app-state wine-id]
  (let [new-note (:new-tasting-note @app-state)]
    [paper {:elevation 1 :sx {:p 3 :mb 3}}
     [typography {:variant "h6" :sx {:mb 2}} "Add Tasting Note"]
     [:form {:on-submit (fn [e]
                     (.preventDefault e)
                     (api/create-tasting-note app-state wine-id 
                       (update new-note :rating #(if (string? %) (js/parseInt %) %))))}

      [grid {:container true :spacing 2}
       ;; Date input
       [grid {:item true :xs 12 :md 6}
        [date-field {:label "Tasting Date"
                     :required true
                     :value (:tasting_date new-note)
                     :on-change #(swap! app-state assoc-in
                                        [:new-tasting-note :tasting_date] %)}]]

       ;; Tasting notes textarea
       [grid {:item true :xs 12}
        [text-field
         {:label "Notes"
          :multiline true
          :rows 4
          :required true
          :fullWidth true
          :value (:notes new-note)
          :sx form-field-style
          :variant "outlined"
          :onChange #(swap! app-state assoc-in [:new-tasting-note :notes]
                            (.. % -target -value))}]]

       ;; Rating input
       [grid {:item true :xs 12 :md 6}
        [smart-field app-state [:new-tasting-note :rating]
         :label "Rating (1-100)"
         :type "number"
         :required true
         :min 1
         :max 100
         :on-change #(swap! app-state assoc-in [:new-tasting-note :rating]
                            (js/parseInt %))]]

       ;; Submit button
       [grid {:item true :xs 12 :sx {:textAlign "right" :mt 2}}
        [button
         {:type "submit"
          :variant "contained"
          :color "primary"}
         "Add Note"]]]]]))

(defn format-date [date-string]
  (if (str/blank? date-string)
    ""
    (let [parts (str/split (first (str/split date-string #"T")) #"-")
          year (first parts)
          month (second parts)
          day (last parts)]
      (str month "/" day "/" year))))

(defn tasting-note-item [app-state wine-id note]
  [paper {:elevation 1 :sx {:p 2 :mb 2}}
   [grid {:container true}
    ;; Header with date and rating
    [grid {:item true :xs 9}
     [typography {:variant "subtitle1" :sx {:fontWeight "bold"}}
      (format-date (:tasting_date note))]]

    [grid {:item true :xs 3 :sx {:textAlign "right"}}
     [typography {:variant "subtitle1" 
                  :sx {:color (if (>= (:rating note) 90) "success.main" "text.primary")}}
      (str "Rating: " (:rating note) "/100")]]

    ;; Note content
    [grid {:item true :xs 12 :sx {:mt 1 :mb 1}}
     [typography {:variant "body1" :sx {:whiteSpace "pre-wrap"}}
      (:notes note)]]

    ;; Delete button
    [grid {:item true :xs 12 :sx {:display "flex" :justifyContent "flex-end"}}
     [button
      {:size "small"
       :color "error"
       :variant "outlined"
       :onClick #(api/delete-tasting-note app-state wine-id (:id note))}
      "Delete"]]]])

(defn tasting-notes-list [app-state wine-id]
  (let [notes (:tasting-notes @app-state)]
    [box {:sx {:mb 3}}
     [typography {:variant "h5" :component "h3" :sx {:mb 2}} "Tasting Notes"]
     (if (empty? notes)
       [typography {:variant "body1" :sx {:fontStyle "italic"}} "No tasting notes yet."]
       [box {:sx {:mt 2}}
        (for [note notes]
          ^{:key (:id note)}
          [tasting-note-item app-state wine-id note])])]))

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
       [typography {:variant "body1"}
        [box {:component "span" :sx {:fontWeight "bold"}} "Location: "]
        (:location wine)
        " · "
        [box {:component "span" :sx {:fontWeight "bold"}} "Quantity: "]
        (:quantity wine)]]]

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

(defn sortable-header [app-state label field]
  (let [sort-state (:sort @app-state)
        current-field (:field sort-state)
        current-direction (:direction sort-state)
        is-active (= field current-field)
        direction (if (= :asc current-direction) "asc" "desc")]
    [table-cell 
     {:align "left" 
      :sx {:font-weight "bold"}}
     [table-sort-label
      (cond-> {:active is-active
               :onClick #(swap! app-state update :sort 
                                (fn [sort]
                                  (if (= field (:field sort))
                                    {:field field 
                                     :direction (if (= :asc (:direction sort)) :desc :asc)}
                                    {:field field :direction :asc})))}
        is-active (assoc :direction direction))
      label]]))

;; Filter helper functions
(defn matches-text-search? [wine search-term]
  (if (empty? search-term)
    true  ;; No search term, match all wines
    (let [search-lower (str/lower-case search-term)
          searchable-fields [(or (:name wine) "")
                             (or (:producer wine) "")
                             (or (:region wine) "")
                             (or (:aoc wine) "")]]
      (some #(str/includes?
              (str/lower-case %)
              search-lower)
            searchable-fields))))

(defn matches-country? [wine country]
  (or (nil? country) (= country (:country wine))))

(defn matches-region? [wine region]
  (or (nil? region) (= region (:region wine))))

(defn matches-style? [wine style]
  (or (nil? style) 
      (and (:styles wine)
           (some #(= style %) (:styles wine)))))

(defn apply-sorting [wines field direction]
  (if field
    (let [sorted (sort-by (fn [wine]
                           (let [val (get wine field)]
                             (cond
                               ;; Handle nil ratings specifically
                               (and (= field :latest_rating) (nil? val)) -1  ;; Sort null ratings last
                               (nil? val) ""  ;; For other fields, use empty string
                               (number? val) val
                               :else (str/lower-case (str val)))))
                         wines)]
      (if (= :desc direction) (reverse sorted) sorted))
    wines))

;; Main filtering and sorting function
(defn filtered-sorted-wines [app-state]
  (let [wines (:wines @app-state)
        {:keys [search country region styles]} (:filters @app-state)
        {:keys [field direction]} (:sort @app-state)]
    
    (as-> wines w
      ;; Apply all filters
      (filter #(matches-text-search? % search) w)
      (filter #(matches-country? % country) w)
      (filter #(matches-region? % region) w)
      (filter #(matches-style? % styles) w)
      ;; Apply sorting
      (apply-sorting w field direction))))

(defn wine-table-row [app-state wine]
  [table-row {:hover true
              :sx {"&:last-child td, &:last-child th" {:border 0}}}
   [table-cell (:producer wine)]
   [table-cell (:name wine)]
   [table-cell (:region wine)]
   [table-cell (:aoc wine)]
   [table-cell (:classification wine)]
   [table-cell (:vintage wine)]
   [table-cell (str/join ", " (:styles wine))]
   [table-cell (:level wine)]
   [table-cell (if-let [rating (:latest_rating wine)]
                 (str rating "/100")
                 "-")]
   [table-cell (:location wine)]
   [table-cell (:quantity wine)]
   [table-cell (gstring/format "$%.2f" (or (:price wine) 0))]
   [table-cell 
    {:align "right"}
    [button
     {:variant "contained"
      :color "primary"
      :size "small"
      :start-icon (r/as-element [visibility])
      :sx {:mr 1}
      :onClick #(do
                 (swap! app-state assoc :selected-wine-id (:id wine))
                 (swap! app-state assoc :new-tasting-note {})
                 (api/fetch-tasting-notes app-state (:id wine)))}
     "View"]
    [button
     {:variant "outlined"
      :color "error"
      :size "small"
      :start-icon (r/as-element [delete])
      :onClick #(api/delete-wine app-state (:id wine))}
     "Delete"]]])

(defn wine-table [app-state wines]
  [table-container
   [table {:sx {:min-width 1200}}
    [table-head
     [table-row
      [sortable-header app-state "Producer" :producer]
      [sortable-header app-state "Name" :name]
      [sortable-header app-state "Region" :region]
      [sortable-header app-state "AOC" :aoc]
      [sortable-header app-state "Classification" :classification]
      [sortable-header app-state "Vintage" :vintage]
      [table-cell "Styles"]  ;; Not sortable (array)
      [sortable-header app-state "Level" :level]
      [sortable-header app-state "Last Rating" :latest_rating]
      [sortable-header app-state "Location" :location]
      [sortable-header app-state "Quantity" :quantity]
      [sortable-header app-state "Price" :price]
      [table-cell {:align "right"} "Actions"]]]
    [table-body
     (for [wine wines]
       ^{:key (:id wine)}
       [wine-table-row app-state wine])]]])

(defn wine-list [app-state]
  [box {:sx {:width "100%" :mt 3}}
   [typography {:variant "h4" :component "h2" :sx {:mb 2}} "My Wines"]
   (if (:loading? @app-state)
     [box {:display "flex" :justifyContent "center" :p 4}
      [circular-progress]]
     
     (if (empty? (:wines @app-state))
       [paper {:elevation 2 :sx {:p 3 :textAlign "center"}}
        [typography {:variant "h6"} "No wines yet. Add your first wine above!"]]
       
       [box 
        ;; Wine details view or table with filtering
        (if (:selected-wine-id @app-state)
          [wine-details-section app-state]
          [paper {:elevation 3 :sx {:p 2 :mb 3}}
           [filter-bar app-state]
           [wine-table app-state (filtered-sorted-wines app-state)]])]))])

(defn main-app [app-state]
  [box {:sx {:p 3}}
   [typography {:variant "h2" :component "h1" :sx {:mb 3 :textAlign "center"}} 
    "Wine Cellar"]
   
   (when-let [error (:error @app-state)]
     [paper {:elevation 3 
             :sx {:p 2 :mb 3 :bgcolor "error.light" :color "error.dark"}}
      [typography {:variant "body1"} error]])
   
   [wine-form app-state]
   [wine-list app-state]])
