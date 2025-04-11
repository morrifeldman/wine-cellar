(ns wine-cellar.views.wines.list
  (:require [goog.string :as gstring]
            [goog.string.format]
            [wine-cellar.views.components :refer [sortable-header quantity-control]]
            [wine-cellar.views.wines.filters :refer [filter-bar]]
            [wine-cellar.utils.filters :refer [filtered-sorted-wines]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.vintage :refer
             [tasting-window-status
              tasting-window-color]]
            [reagent-mui.material.grid :refer [grid]]
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
            [reagent-mui.material.collapse :refer [collapse]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.expand-more :refer [expand-more]]
            [reagent-mui.icons.expand-less :refer [expand-less]]))

(defn get-rating-color [rating]
  (cond
    (>= rating 90) "rating.high"
    (>= rating 80) "rating.medium"
    :else "rating.low"))

(defn wine-table-row [app-state wine]
  [table-row {:hover true
              :sx {"&:last-child td, &:last-child th" {:border 0}}}
   [table-cell 
    [box {:sx {:display "flex" :alignItems "center"}}
     (when (:label_thumbnail wine)
       [box {:component "img"
             :src (:label_thumbnail wine)
             :sx {:width 40
                  :height 40
                  :mr 1
                  :objectFit "contain"
                  :borderRadius 1}}])
     (:producer wine)]]
   [table-cell (:name wine)]
   [table-cell (:region wine)]
   [table-cell (:aoc wine)]
   [table-cell (:classification wine)]
   [table-cell (:vintage wine)]
   [table-cell (:style wine)]
   [table-cell (:level wine)]
   [table-cell
    (if-let [rating (:latest_rating wine)]
      [typography {:sx {:color (get-rating-color rating)
                        :fontWeight "bold"}}
       (str rating "/100")]
      "-")]
   [table-cell
    (let [status (tasting-window-status wine)
          drink-from-year (or (:drink_from_year wine)
                              (when-let [date (:drink_from wine)]
                                (.getFullYear (js/Date. date))))
          drink-until-year (or (:drink_until_year wine)
                               (when-let [date (:drink_until wine)]
                                 (.getFullYear (js/Date. date))))]
      [box {:sx {:color (tasting-window-color status)
                 :fontWeight "medium"
                 :display "flex"
                 :flexDirection "column"
                 :alignItems "center"}}
       (when drink-from-year
         [typography {:variant "body2"
                      :sx {:lineHeight 1.2}}
          (str drink-from-year)])
       (when (and drink-from-year drink-until-year)
         [typography {:variant "body2"
                      :sx {:lineHeight 1.2}}
          "—"])
       (when drink-until-year
         [typography {:variant "body2"
                      :sx {:lineHeight 1.2}}
          (str drink-until-year)])])]
   [table-cell (:location wine)]
   [table-cell
    [quantity-control app-state (:id wine) (:quantity wine)]]
   [table-cell (gstring/format "$%.2f" (or (:price wine) 0))]
   [table-cell
    {:align "right"}
    [box {:display "flex"
          :flexDirection "column"
          :alignItems "flex-end"}
     [button
      {:variant "outlined"
       :color "primary"
       :size "small"
       :sx {:mb 1}
       :onClick #(do
                   (swap! app-state assoc :selected-wine-id (:id wine))
                   (swap! app-state assoc :new-tasting-note {})
                   (api/fetch-tasting-notes app-state (:id wine))
                   (api/fetch-wine-details app-state (:id wine)))}
      "View"]
     [button
      {:variant "outlined"
       :color "error"
       :size "small"
       :onClick #(api/delete-wine app-state (:id wine))}
      "Delete"]]]])

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
      [table-cell "Style"]
      [sortable-header app-state "Level" :level]
      [sortable-header app-state "Last Rating" :latest_rating]
      [table-cell "Tasting Window"]
      [sortable-header app-state "Location" :location]
      [sortable-header app-state "Quantity" :quantity]
      [sortable-header app-state "Price" :price]
      [table-cell {:align "right"} "Actions"]]]
    [table-body
     (for [wine wines]
       ^{:key (:id wine)}
       [wine-table-row app-state wine])]]])

(defn wine-stats [app-state]
  (let [wines (:wines @app-state)
        visible-wines (filtered-sorted-wines app-state)
        total-wines (count wines)
        visible-count (count visible-wines)
        total-bottles (reduce + 0 (map :quantity wines))
        ratings (keep :latest_rating wines)
        avg-rating (if (seq ratings)
                     (js/Math.round (/ (reduce + 0 ratings) (count ratings)))
                     "-")
        total-value (js/Math.round (reduce + 0 (map #(* (or (:price %) 0) (:quantity %)) wines)))]
    [paper {:elevation 2 :sx {:p 3 :mb 3 :borderRadius 2}}
     [box {:sx {:display "flex"
                :justifyContent "space-between"
                :alignItems "center"
                :mb 2}}
      [typography {:variant "h6" :component "h3"} "Collection Overview"]
      [icon-button
       {:onClick #(swap! app-state update :show-stats? not)
        :size "small"}
       (if (:show-stats? @app-state)
         [expand-less]
         [expand-more])]]

     [collapse {:in (:show-stats? @app-state)
                :timeout "auto"}
      [grid {:container true :spacing 3}
       [grid {:item true :xs 12 :sm 6 :md 3}
        [paper {:elevation 1 :sx {:p 2 :textAlign "center" :height "100%"}}
         [typography {:variant "h4" :color "primary"} (str visible-count "/" total-wines)]
         [typography {:variant "body2" :color "text.secondary"} "Wines"]]]

       [grid {:item true :xs 12 :sm 6 :md 3}
        [paper {:elevation 1 :sx {:p 2 :textAlign "center" :height "100%"}}
         [typography {:variant "h4" :color "primary"} total-bottles]
         [typography {:variant "body2" :color "text.secondary"} "Bottles"]]]

       [grid {:item true :xs 12 :sm 6 :md 3}
        [paper {:elevation 1 :sx {:p 2 :textAlign "center" :height "100%"}}
         [typography {:variant "h4"
                      :color (if (= avg-rating "-")
                               "text.secondary"
                               (get-rating-color (js/parseInt avg-rating)))}
          (if (= avg-rating "-") "-" (str avg-rating "/100"))]
         [typography {:variant "body2" :color "text.secondary"} "Avg. Rating"]]]

       [grid {:item true :xs 12 :sm 6 :md 3}
        [paper {:elevation 1 :sx {:p 2 :textAlign "center" :height "100%"}}
         [typography {:variant "h4" :color "primary"} (str "$" total-value)]
         [typography {:variant "body2" :color "text.secondary"} "Collection Value"]]]]]]))

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
        [wine-stats app-state]

        ;; Wine details view or table with filtering
        (if (:selected-wine-id @app-state)
          [:div] ;; Wine details are rendered separately
          [paper {:elevation 3 :sx {:p 2 :mb 3}}
           [filter-bar app-state]
           [wine-table app-state (filtered-sorted-wines app-state)]])]))])

