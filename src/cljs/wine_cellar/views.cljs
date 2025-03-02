(ns wine-cellar.views
  (:require [goog.string :as gstring]
            [goog.string.format]
            [clojure.string :as str]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]))

(defn select-field [{:keys [label value options required disabled on-change empty-option]}]
  [:div
   [:label (str label ": ")]
   [:select {:value (or value "")
            :required required
            :disabled disabled
            :on-change #(on-change (.. % -target -value))}
    [:option {:value ""} (or empty-option "Select")]
    (for [[k v] options]
      ^{:key k}
      [:option {:value k} v])]])

(defn input-field [{:keys [label type required value on-change min step]}]
  [:div
   [:label (str label ": ")]
   [:input (cond-> {:type type
                    :required required
                    :value value
                    :on-change #(on-change (.. % -target -value))}
             min (assoc :min min)
             step (assoc :step step))]])

(defn multi-select-field [{:keys [label value options required on-change]}]
  [:div
   [:label (str label ": ")]
   [:select {:multiple true
             :value (or value [])  ;; Default to empty array if nil
             :required required
             :on-change (fn [e]
                         (let [selected (.. e -target -selectedOptions)
                               values (js->clj (array-seq selected))
                               final-values (mapv #(.-value %) values)]
                           (on-change final-values)))}
    (for [[k v] options]
      ^{:key k}
      [:option {:value k} v])]])

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

;; Form Components
(defn wine-form [app-state]
  (let [new-wine (:new-wine @app-state)
        classifications (:classifications @app-state)]
    [:div.wine-form
     [:h3 "Add New Wine"]
     [:form {:on-submit (fn [e]
                         (.preventDefault e)
                         (api/create-wine app-state
                           (update new-wine :price js/parseFloat)))}
      
      ;; Basic Info
      [input-field {:label "Name"
                    :type "text"
                    :required false
                    :value (:name new-wine)
                    :on-change #(swap! app-state assoc-in [:new-wine :name] %)}]
      
      [input-field {:label "Producer"
                    :type "text"
                    :value (:producer new-wine)
                    :on-change #(swap! app-state assoc-in [:new-wine :producer] %)}]
      
      ;; Wine Classification
      [select-field {:label "Country"
                     :value (:country new-wine)
                     :required true
                     :options (map #(vector % %) (unique-countries classifications))
                     :on-change #(swap! app-state assoc-in [:new-wine :country] %)}]
      
      [select-field {:label "Region"
                     :value (:region new-wine)
                     :required true
                     :disabled (empty? (:country new-wine))
                     :options (map #(vector % %) 
                                 (regions-for-country classifications (:country new-wine)))
                     :on-change #(swap! app-state assoc-in [:new-wine :region] %)}]
      
      [select-field {:label "AOC"
                     :value (:aoc new-wine)
                     :disabled (or (empty? (:country new-wine))
                                 (empty? (:region new-wine)))
                     :options (map #(vector % %) 
                                 (aocs-for-region classifications 
                                                (:country new-wine)
                                                (:region new-wine)))
                     :on-change #(swap! app-state assoc-in [:new-wine :aoc] %)}]
      
      [select-field {:label "Classification"
                     :value (:classification new-wine)
                     :disabled (or (empty? (:country new-wine))
                                 (empty? (:region new-wine))
                                 (empty? (:aoc new-wine)))
                     :options (map #(vector % %) 
                                 (classifications-for-aoc classifications
                                                        (:country new-wine)
                                                        (:region new-wine)
                                                        (:aoc new-wine)))
                     :on-change #(swap! app-state assoc-in [:new-wine :classification] %)}]
      
      [select-field {:label "Level"
               :value (or (:level new-wine) "")
               :disabled (or (empty? (:country new-wine))
                             (empty? (:region new-wine))
                             (empty? (:aoc new-wine))
                             (empty? (:classification new-wine)))
               :options (map #(vector % %) (levels-for-classification
                                             classifications
                                             (:country new-wine)
                                             (:region new-wine)
                                             (:aoc new-wine)
                                             (:classification new-wine)))
               :on-change #(swap! app-state assoc-in [:new-wine :level]
                                (when-not (empty? %) %))}]
     [input-field {:label "Vintage"
              :type "number"
              :required true
              :min 1900
              :max 2100 
              :value (:vintage new-wine)
              :on-change #(swap! app-state assoc-in [:new-wine :vintage]
                                (js/parseInt %))}]

      [multi-select-field {:label "Styles"
                          :value (:styles new-wine)
                          :required true
                          :options (map #(vector % %) common/wine-styles)
                          :on-change #(swap! app-state assoc-in [:new-wine :styles] %)}]
      
      ;; Additional Info
      [input-field {:label "Location"
                    :type "text"
                    :required true
                    :value (:location new-wine)
                    :on-change #(swap! app-state assoc-in [:new-wine :location] %)}]
        
      [input-field {:label "Quantity"
                    :type "number"
                    :required true
                    :min 0
                    :value (:quantity new-wine)
                    :on-change #(swap! app-state assoc-in [:new-wine :quantity] 
                                     (js/parseInt %))}]
      
      [input-field {:label "Price"
                    :type "number"
                    :required true
                    :step "0.01"
                    :min "0"
                    :value (if (string? (:price new-wine))
                            (:price new-wine)
                            (str (:price new-wine)))
                    :on-change #(swap! app-state assoc-in [:new-wine :price] %)}]
      
      [:button {:type "submit"} "Add Wine"]]]))

;; Tasting Notes Components
(defn tasting-note-form [app-state wine-id]
  (let [new-note (:new-tasting-note @app-state)]
    [:div.tasting-note-form
     [:h4 "Add Tasting Note"]
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          (api/create-tasting-note app-state wine-id new-note))}
      
      [input-field {:label "Tasting Date"
                    :type "date"
                    :required true
                    :value (:tasting_date new-note)
                    :on-change #(swap! app-state assoc-in [:new-tasting-note :tasting_date] %)}]
      
      [:div.form-group
       [:label "Notes: "]
       [:textarea {:required true
                   :value (:notes new-note)
                   :on-change #(swap! app-state assoc-in [:new-tasting-note :notes]
                                      (.. % -target -value))
                   :rows 4
                   :cols 50}]]
      
      [input-field {:label "Rating (1-100)"
                    :type "number"
                    :required true
                    :min 1
                    :max 100
                    :value (:rating new-note)
                    :on-change #(swap! app-state assoc-in [:new-tasting-note :rating]
                                       (js/parseInt %))}]
      
      [:button {:type "submit"} "Add Note"]]]))

(defn tasting-note-item [app-state wine-id note]
  [:div.tasting-note
   [:div.note-header
    [:strong (:tasting_date note)]
    [:span.rating (str "Rating: " (:rating note) "/100")]
    [:div.note-actions
     [:button {:on-click #(api/delete-tasting-note app-state wine-id (:id note))}
      "Delete"]]]
   [:div.note-content
    [:p (:notes note)]]])

(defn tasting-notes-list [app-state wine-id]
  (let [notes (:tasting-notes @app-state)]
    [:div.tasting-notes
     [:h3 "Tasting Notes"]
     (if (empty? notes)
       [:p "No tasting notes yet."]
       [:div.notes-list
        (for [note notes]
          ^{:key (:id note)}
          [tasting-note-item app-state wine-id note])])]))

;; Wine detail view with tasting notes
(defn wine-detail [app-state wine]
  (let [wine-id (:id wine)]
    [:div.wine-detail
     [:h3 (str (:producer wine) (when-let [name (:name wine)] (str " - " name)))]
     [:div.wine-info
      [:p (str (:vintage wine) " " (:region wine) 
              (when-let [aoc (:aoc wine)] (str " - " aoc))
              (when-let [classification (:classification wine)] (str " - " classification)))]
      [:p (str "Styles: " (str/join ", " (:styles wine)))]
      [:p (str "Quantity: " (:quantity wine) " · Location: " (:location wine))]]
     
     [tasting-notes-list app-state wine-id]
     [tasting-note-form app-state wine-id]]))

(defn filter-bar [app-state]
  (let [filters (:filters @app-state)
        classifications (:classifications @app-state)]
    [:div.filter-bar
     [:input.search-input {:type "text"
                           :placeholder "Search wines..."
                           :value (:search filters)
                           :on-change #(swap! app-state assoc-in [:filters :search] 
                                            (.. % -target -value))}]
     
     [select-field {:label "Country"
                    :value (:country filters)
                    :empty-option "All Countries"
                    :options (map #(vector % %) (unique-countries classifications))
                    :on-change #(swap! app-state assoc-in [:filters :country] 
                                      (when-not (empty? %) %))}]
     
     [select-field {:label "Region"
                    :value (:region filters)
                    :empty-option "All Regions"
                    :disabled (empty? (:country filters))
                    :options (map #(vector % %) 
                                (regions-for-country classifications (:country filters)))
                    :on-change #(swap! app-state assoc-in [:filters :region] 
                                      (when-not (empty? %) %))}]
     
     [select-field {:label "Style"
                    :value (:styles filters)
                    :empty-option "All Styles"
                    :options (map #(vector % %) common/wine-styles)
                    :on-change #(swap! app-state assoc-in [:filters :styles] 
                                      (when-not (empty? %) %))}]
     
     [:button.clear-filters 
      {:on-click #(swap! app-state assoc :filters {:search "" :country nil :region nil :styles nil})}
      "Clear Filters"]]))

(defn sortable-header [app-state label field]
  (let [sort-state (:sort @app-state)
        current-field (:field sort-state)
        current-direction (:direction sort-state)
        is-active (= field current-field)]
    [:th.sortable {:on-click #(swap! app-state update :sort 
                                    (fn [sort]
                                      (if (= field (:field sort))
                                        {:field field 
                                         :direction (if (= :asc (:direction sort)) :desc :asc)}
                                        {:field field :direction :asc})))}
     [:span label]
     [:span.sort-icon 
      (cond
        (not is-active) " ⇵"
        (= :asc current-direction) " ↑"
        :else " ↓")]]))

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
  [:tr
   [:td (:producer wine)]
   [:td (:name wine)]
   [:td (:region wine)]
   [:td (:aoc wine)]
   [:td (:classification wine)]
   [:td (:vintage wine)]
   [:td (interpose ", " (:styles wine))]
   [:td (:level wine)]
   [:td (if-let [rating (:latest_rating wine)]
          (str rating "/100")
          [:span.no-rating "—"])]
   [:td (:location wine)]
   [:td (:quantity wine)]
   [:td (gstring/format "$%.2f" (or (:price wine) 0))]
   [:td
    [:button {:on-click #(do
                          (swap! app-state assoc :selected-wine-id (:id wine))
                          (swap! app-state assoc :new-tasting-note {})
                          (api/fetch-tasting-notes app-state (:id wine)))}
     "Details"]
    [:button {:on-click #(api/delete-wine app-state (:id wine))}
     "Delete"]]])

;; Wine details section (when a wine is selected)
(defn wine-details-section [app-state]
  (when-let [selected-wine-id (:selected-wine-id @app-state)]
    (when-let [selected-wine (first (filter #(= (:id %) selected-wine-id)
                                           (:wines @app-state)))]
      [:div
       [wine-detail app-state selected-wine]
       [:button.back-button
        {:on-click #(do
                     (swap! app-state dissoc :selected-wine-id :tasting-notes)
                     (swap! app-state assoc :new-tasting-note {}))}
        "Back to List"]])))

;; Wine table component
(defn wine-table [app-state wines]
  [:table
   [:thead
    [:tr
     [sortable-header app-state "Producer" :producer]
     [sortable-header app-state "Name" :name]
     [sortable-header app-state "Region" :region]
     [sortable-header app-state "AOC" :aoc]
     [sortable-header app-state "Classification" :classification]
     [sortable-header app-state "Vintage" :vintage]
     [:th "Styles"]  ;; Not sortable (array)
     [sortable-header app-state "Level" :level]
     [sortable-header app-state "Last Rating" :latest_rating]
     [sortable-header app-state "Location" :location]
     [sortable-header app-state "Quantity" :quantity]
     [sortable-header app-state "Price" :price]
     [:th "Actions"]]]
   [:tbody
    (for [wine wines]
      ^{:key (:id wine)}
      [wine-table-row app-state wine])]])

;; Main wine list component
(defn wine-list [app-state]
  [:div.wine-list
   [:h2 "My Wines"]
   (if (:loading? @app-state)
     [:div.loading "Loading your wine collection..."]
     
     (if (empty? (:wines @app-state))
       [:div.empty-state "No wines yet. Add your first wine above!"]
       
       [:div
        ;; Wine details view or table with filtering
        (if (:selected-wine-id @app-state)
          [wine-details-section app-state]
          [:div
           [filter-bar app-state]
           [wine-table app-state (filtered-sorted-wines app-state)]])]))])

(defn main-app [app-state]
  [:div
   [:h1 "Wine Cellar"]
   (when-let [error (:error @app-state)]
     [:div.error {:style {:color "red"
                         :padding "10px"
                         :margin "10px 0"
                         :border "1px solid red"
                         :background-color "#ffebee"}}
      error])
   [wine-form app-state]
   [wine-list app-state]])
