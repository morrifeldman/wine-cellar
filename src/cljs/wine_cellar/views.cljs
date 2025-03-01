(ns wine-cellar.views
  (:require [goog.string :as gstring]
            [goog.string.format]
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
             :value value
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

(defn wine-list [app-state]
  (let [state @app-state]
    [:div.wine-list
     [:h2 "My Wines"]
     (cond
       (:loading? state)
       [:div.loading "Loading your wine collection..."]

       (empty? (:wines state))
       [:div.empty-state "No wines yet. Add your first wine above!"]

       :else
       [:table
        [:thead
         [:tr
          [:th "Producer"]
          [:th "Name"]
          [:th "Region"]
          [:th "AOC"]
          [:th "Classification"]
          [:th "Vintage"]
          [:th "Styles"]
          [:th "Level"]
          [:th "Location"]
          [:th "Quantity"]
          [:th "Price"]
          [:th "Actions"]]]
        [:tbody
         (for [wine (:wines state)]
           [:tr {:key (:id wine)}
            [:td (:producer wine)]
            [:td (:name wine)]
            [:td (:region wine)]
            [:td (:aoc wine)]
            [:td (:classification wine)]
            [:td (:vintage wine)]
            [:td (interpose ", " (:styles wine))]
            [:td (:level wine)]
            [:td (:location wine)]
            [:td (:quantity wine)]
            [:td (gstring/format "$%.2f" (or (:price wine) 0))]
            [:td
             [:button {:on-click #(api/delete-wine app-state (:id wine))}
              "Delete"]]])]])]))

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
