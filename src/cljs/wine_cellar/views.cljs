(ns wine-cellar.views
  (:require [goog.string :as gstring]
            [goog.string.format]
            [wine-cellar.api :as api]))

(def wine-types
  ["red" "white" "rose" "sparkling" "fortified" "orange"])

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
                 :on-change #(swap! app-state assoc-in [:new-wine :name] (.. % -target -value))}]]
       [:div
        [:label "Vintage: "]
        [:input {:type "number"
                 :required true
                 :min 1900
                 :max 2100
                 :value (:vintage new-wine)
                 :on-change #(swap! app-state assoc-in [:new-wine :vintage] (js/parseInt (.. % -target -value)))}]]
       [:div
        [:label "Type: "]
        [:select {:value (:type new-wine)
                  :required true
                  :on-change #(swap! app-state assoc-in [:new-wine :type] (.. % -target -value))}
         [:option {:value ""} "Select a type"]
         (for [type wine-types]
           ^{:key type}
           [:option {:value type} 
            (when (not-empty type)
              (str (.toUpperCase (first type)) (subs type 1)))])]]
       [:div
        [:label "Location: "]
        [:input {:type "text"
                 :required true
                 :value (:location new-wine)
                 :on-change #(swap! app-state assoc-in [:new-wine :location] (.. % -target -value))}]]
       [:div
        [:label "Quantity: "]
        [:input {:type "number"
                 :required true
                 :min 0
                 :value (:quantity new-wine)
                 :on-change #(swap! app-state assoc-in [:new-wine :quantity] (js/parseInt (.. % -target -value)))}]]
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
       [:div "No wines yet. Add your first wine above!"]

       :else
       [:table
        [:thead
         [:tr
          [:th "Name"]
          [:th "Vintage"]
          [:th "Type"]
          [:th "Location"]
          [:th "Quantity"]
          [:th "Price"]
          [:th "Actions"]]]
        [:tbody
         (for [wine (:wines state)]
           (when (:id wine)  ;; Only render wines with IDs
             [:tr {:key (:id wine)}  ;; Move the key to the tr element
              [:td (:name wine)]
              [:td (:vintage wine)]
              [:td (when-let [type (:type wine)]
                     (when (not-empty type)
                       (case type
                         "red" "Red"
                         "white" "White"
                         "rose" "Rosé"
                         "sparkling" "Sparkling"
                         "fortified" "Fortified"
                         "orange" "Orange"
                         type)))]
              [:td (:location wine)]
              [:td (:quantity wine)]
              [:td.price (if-let [price (:price wine)]
                           (gstring/format "$%.2f" price)
                           "$0.00")]
              [:td
               [:button {:on-click #(api/delete-wine app-state (:id wine))}
                "Delete"]]]))]])]))

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
