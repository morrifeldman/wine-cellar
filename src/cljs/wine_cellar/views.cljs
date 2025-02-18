(ns wine-cellar.views
  (:require [goog.string :as gstring]
            [goog.string.format]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]))

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
      [:div
       [:label "Country: "]
       [:input {:type "text"
                :required true
                :value (:country new-wine)
                :on-change #(swap! app-state assoc-in [:new-wine :country]
                                   (.. % -target -value))}]]
      [:div
       [:label "Region: "]
       [:input {:type "text"
                :required true
                :value (:region new-wine)
                :on-change #(swap! app-state assoc-in [:new-wine :region]
                                   (.. % -target -value))}]]
      [:div
       [:label "AOC: "]
       [:input {:type "text"
                :value (:aoc new-wine)
                :on-change #(swap! app-state assoc-in [:new-wine :aoc]
                                   (.. % -target -value))}]]
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
            [:td (str (:region wine) 
                     (when (:aoc wine) 
                       (str " - " (:aoc wine))))]
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
