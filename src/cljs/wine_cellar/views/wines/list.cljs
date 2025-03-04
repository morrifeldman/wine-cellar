(ns wine-cellar.views.wines.list
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [wine-cellar.views.components :refer [sortable-header quantity-control]]
            [wine-cellar.views.wines.filters :refer [filter-bar]]
            [wine-cellar.utils.filters :refer [filtered-sorted-wines]]
            [wine-cellar.api :as api]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.table :refer [table]]
            [reagent-mui.material.table-container :refer [table-container]]
            [reagent-mui.material.table-head :refer [table-head]]
            [reagent-mui.material.table-body :refer [table-body]]
            [reagent-mui.material.table-row :refer [table-row]]
            [reagent-mui.material.table-cell :refer [table-cell]]
            [reagent-mui.icons.visibility :refer [visibility]]
            [reagent-mui.icons.delete :refer [delete]]))

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
   [table-cell 
    [quantity-control app-state (:id wine) (:quantity wine)]]
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
          [:div] ;; Wine details are rendered separately
          [paper {:elevation 3 :sx {:p 2 :mb 3}}
           [filter-bar app-state]
           [wine-table app-state (filtered-sorted-wines app-state)]])]))])
