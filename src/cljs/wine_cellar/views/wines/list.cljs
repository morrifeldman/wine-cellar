(ns wine-cellar.views.wines.list
  (:require [goog.string.format]
            [wine-cellar.views.components.wine-card :refer
             [wine-card get-rating-color]]
            [wine-cellar.views.wines.filters :refer [filter-bar]]
            [wine-cellar.utils.filters :refer [filtered-sorted-wines]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.collapse :refer [collapse]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.icons.expand-more :refer [expand-more]]
            [reagent-mui.icons.expand-less :refer [expand-less]]
            [reagent-mui.icons.arrow-drop-up :refer [arrow-drop-up]]
            [reagent-mui.icons.arrow-drop-down :refer [arrow-drop-down]]))

(defn sort-control
  [app-state]
  (let [sort-state (:sort @app-state)
        current-field (:field sort-state)
        current-direction (:direction sort-state)]
    [box {:sx {:mb 2 :display "flex" :alignItems "center"}}
     [typography {:variant "body2" :color "text.secondary" :sx {:mr 2}}
      "Sort by:"]
     [form-control {:size "small" :sx {:minWidth 120 :mr 2}}
      [select
       {:value (or current-field "producer")
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
       [menu-item {:value "created_at"} "Date Added"]
       [menu-item {:value "updated_at"} "Last Updated"]]]
     [button
      {:variant "outlined"
       :size "small"
       :onClick #(swap! app-state update-in
                   [:sort :direction]
                   (fn [dir] (if (= :asc dir) :desc :asc)))}
      (if (= :asc current-direction)
        [box {:sx {:display "flex" :alignItems "center"}} "Ascending "
         [arrow-drop-up {:sx {:ml 0.5}}]]
        [box {:sx {:display "flex" :alignItems "center"}} "Descending "
         [arrow-drop-down {:sx {:ml 0.5}}]])]]))

(defn wine-card-grid
  [app-state wines]
  [box [sort-control app-state]
   [grid {:container true :spacing 2}
    (for [wine wines]
      ^{:key (:id wine)}
      [grid {:item true :xs 12 :sm 6 :md 4 :lg 3}
       [wine-card app-state wine]])]])

(defn wine-stats
  [app-state]
  (let [wines (:wines @app-state)
        visible-wines (filtered-sorted-wines app-state)
        total-wines (count wines)
        visible-count (count visible-wines)
        total-bottles (reduce + 0 (map :quantity wines))
        ratings (keep :latest_rating wines)
        avg-rating (if (seq ratings)
                     (js/Math.round (/ (reduce + 0 ratings) (count ratings)))
                     "-")
        total-value
        (js/Math.round
         (reduce + 0 (map #(* (or (:price %) 0) (:quantity %)) wines)))]
    [paper {:elevation 2 :sx {:p 3 :mb 3 :borderRadius 2}}
     [box
      {:sx {:display "flex"
            :justifyContent "space-between"
            :alignItems "center"
            :mb 2}}
      [typography {:variant "h6" :component "h3"} "Collection Overview"]
      [icon-button
       {:onClick #(swap! app-state update :show-stats? not) :size "small"}
       (if (:show-stats? @app-state)
         [expand-less {:sx {:color "text.secondary"}}]
         [expand-more {:sx {:color "text.secondary"}}])]]
     [collapse {:in (:show-stats? @app-state) :timeout "auto"}
      [grid {:container true :spacing 3}
       [grid {:item true :xs 12 :sm 6 :md 3}
        [paper {:elevation 1 :sx {:p 2 :textAlign "center" :height "100%"}}
         [typography {:variant "h4" :color "primary"}
          (str visible-count "/" total-wines)]
         [typography {:variant "body2" :color "text.secondary"} "Wines"]]]
       [grid {:item true :xs 12 :sm 6 :md 3}
        [paper {:elevation 1 :sx {:p 2 :textAlign "center" :height "100%"}}
         [typography {:variant "h4" :color "primary"} total-bottles]
         [typography {:variant "body2" :color "text.secondary"} "Bottles"]]]
       [grid {:item true :xs 12 :sm 6 :md 3}
        [paper {:elevation 1 :sx {:p 2 :textAlign "center" :height "100%"}}
         [typography
          {:variant "h4"
           :color (if (= avg-rating "-")
                    "text.secondary"
                    (get-rating-color (js/parseInt avg-rating)))}
          (if (= avg-rating "-") "-" (str avg-rating "/100"))]
         [typography {:variant "body2" :color "text.secondary"} "Avg. Rating"]]]
       [grid {:item true :xs 12 :sm 6 :md 3}
        [paper {:elevation 1 :sx {:p 2 :textAlign "center" :height "100%"}}
         [typography {:variant "h4" :color "primary"} (str "$" total-value)]
         [typography {:variant "body2" :color "text.secondary"}
          "Collection Value"]]]]]]))

(defn wine-list
  [app-state]
  [box {:sx {:width "100%" :mt 3}}
   [typography {:variant "h4" :component "h2" :sx {:mb 2}} "My Wines"]
   (if (:loading? @app-state)
     [box {:display "flex" :justifyContent "center" :p 4} [circular-progress]]
     (if (empty? (:wines @app-state))
       [paper {:elevation 2 :sx {:p 3 :textAlign "center"}}
        [typography {:variant "h6"} "No wines yet. Add your first wine above!"]]
       [box [wine-stats app-state]
        ;; Wine details view or card grid with filtering
        (if (:selected-wine-id @app-state)
          [:div] ;; Wine details are rendered separately
          [paper {:elevation 3 :sx {:p 2 :mb 3}} [filter-bar app-state]
           [wine-card-grid app-state (filtered-sorted-wines app-state)]])]))])

