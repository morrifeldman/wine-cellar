(ns wine-cellar.views
  (:require [goog.string :as gstring]
            [goog.string.format]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]))

(defn unique-countries [classifications]
  (->> classifications
       (map :country)
       distinct
       sort))

(defn country-select [app-state]
  (let [classifications (:classifications @app-state)
        countries (unique-countries classifications)]
    [:div
     [:label "Country: "]
     [:select {:value (get-in @app-state [:new-wine :country])
               :required true
               :on-change #(swap! app-state assoc-in [:new-wine :country]
                                  (.. % -target -value))}
      [:option {:value ""} "Select a country"]
      (for [country countries]
        ^{:key country}
        [:option {:value country} country])]]))

(defn regions-for-country [classifications country]
  (->> classifications
       (filter #(= country (:country %)))
       (map :region)
       distinct
       sort))

(defn region-select [app-state]
  (let [country (get-in @app-state [:new-wine :country])
        regions (regions-for-country (:classifications @app-state) country)]
    [:div
     [:label "Region: "]
     [:select {:value (get-in @app-state [:new-wine :region])
               :required true
               :disabled (empty? country)
               :on-change #(swap! app-state assoc-in [:new-wine :region] (.. % -target -value))}
      [:option {:value ""} "Select a region"]
      (for [region regions]
        ^{:key region}
        [:option {:value region} region])]]))

(defn aocs-for-region [classifications country region]
  (->> classifications
       (filter #(and (= country (:country %))
                    (= region (:region %))))
       (map :aoc)
       (remove nil?)
       distinct
       sort))

(defn aoc-select [app-state]
  (let [{:keys [country region]} (:new-wine @app-state)
        aocs (aocs-for-region (:classifications @app-state) country region)]
    [:div
     [:label "AOC: "]
     [:select {:value (get-in @app-state [:new-wine :aoc])
               :disabled (or (empty? country) (empty? region))
               :on-change #(swap! app-state assoc-in [:new-wine :aoc] (.. % -target -value))}
      [:option {:value ""} "Select an AOC"]
      (for [aoc aocs]
        ^{:key aoc}
        [:option {:value aoc} aoc])]]))

(defn classifications-for-aoc [classifications country region aoc]
 (->> classifications
      (filter #(and (= country (:country %))
                   (= region (:region %))
                   (= aoc (:aoc %))))
      (map :classification)
      (remove nil?)
      distinct
      sort))

(defn classification-select [app-state]
 (let [{:keys [country region aoc]} (:new-wine @app-state)
       classifications (classifications-for-aoc (:classifications @app-state) 
                                             country region aoc)]
   [:div
    [:label "Classification: "]
    [:select {:value (get-in @app-state [:new-wine :classification])
              :disabled (or (empty? country) (empty? region) (empty? aoc))
              :on-change #(swap! app-state assoc-in [:new-wine :classification] 
                                (.. % -target -value))}
     [:option {:value ""} "Select a classification"]
     (for [c classifications]
       ^{:key c}
       [:option {:value c} c])]]))

(defn wine-form [app-state]
  (let [new-wine (:new-wine @app-state)]
    [:div.wine-form
     [:h3 "Add New Wine"]
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          (let [wine (update new-wine :price #(if (string? %)
                                                                (js/parseFloat %)
                                                                %))]
                            (api/create-wine app-state wine)))}
      [:div
       [:label "Name: "]
       [:input {:type "text"
                :required true
                :value (:name new-wine)
                :on-change #(swap! app-state assoc-in [:new-wine :name]
                                   (.. % -target -value))}]]
      [:div
       [:label "Producer: "]
       [:input {:type "text"
                :required false
                :value (:producer new-wine)
                :on-change #(swap! app-state assoc-in [:new-wine :producer]
                                   (.. % -target -value))}]]
      [country-select app-state]
      [region-select app-state]
      [aoc-select app-state]
      [classification-select app-state]
      [:div
       [:label "Styles: "]
       [:select {:multiple true
                 :value (:styles new-wine)
                 :required true
                 :on-change
                 (fn [state]
                   (let [selected (.. state -target -selectedOptions)
                         values (js->clj (array-seq selected))
                         styles (mapv #(.-value %) values)]
                     (swap! app-state assoc-in [:new-wine :styles] styles)))}
        (for [style common/wine-styles]
          ^{:key style}
          [:option {:value style}
           (str (.toUpperCase (first style)) (subs style 1))])]]
      [:div
       [:label "Location: "]
       [:input {:type "text"
                :required true
                :value (:location new-wine)
                :on-change #(swap! app-state assoc-in [:new-wine :location]
                                   (.. % -target -value))}]]
      [:div
       [:label "Quantity: "]
       [:input {:type "number"
                :required true
                :min 0
                :value (:quantity new-wine)
                :on-change #(swap! app-state assoc-in [:new-wine :quantity]
                                   (js/parseInt (.. % -target -value)))}]]
      [:div
       [:label "Price: "]
       [:input {:type "number"
                :required true
                :step "0.01"
                :min "0"
                :value (if (string? (:price new-wine))
                         (:price new-wine)
                         (str (:price new-wine)))
                :on-change #(swap! app-state assoc-in [:new-wine :price]
                                   (.. % -target -value))}]]
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
            [:td (interpose ", " (map str (:styles wine)))]
            [:td (:location wine)]
            [:td (:quantity wine)]
            [:td (if-let [price (:price wine)]
                   (gstring/format "$%.2f" price)
                   "$0.00")]
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
