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
            [reagent-mui.icons.expand-more :refer [expand-more]]
            [reagent-mui.icons.expand-less :refer [expand-less]]
            [reagent-mui.material.modal :refer [modal]]
            [reagent-mui.material.backdrop :refer [backdrop]]
            [reagent-mui.icons.close :refer [close]]))


(defn wine-card-grid
  [app-state wines]
  [grid {:container true :spacing 2}
   (for [wine wines]
     ^{:key (:id wine)}
     [grid {:item true :xs 12 :sm 6 :md 4 :lg 3 :sx {:mb 2}}
      [wine-card app-state wine]])])

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

(defn collection-stats-modal
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
    [modal
     {:open (boolean (get @app-state :show-collection-stats?))
      :onClose #(swap! app-state dissoc :show-collection-stats?)
      :closeAfterTransition true}
     [backdrop
      {:sx {:color "white"}
       :open (boolean (get @app-state :show-collection-stats?))}
      [box
       {:sx {:position "absolute"
             :top "50%"
             :left "50%"
             :transform "translate(-50%, -50%)"
             :width "80vw"
             :maxWidth "800px"
             :bgcolor "container.main"
             :borderRadius 2
             :boxShadow 24
             :p 4
             :outline "none"}}
       ;; Header with title and close button
       [box
        {:sx {:display "flex"
              :justifyContent "space-between"
              :alignItems "center"
              :mb 3}} [typography {:variant "h5"} "Collection Overview"]
        [icon-button
         {:onClick #(swap! app-state dissoc :show-collection-stats?)
          :sx {:minWidth "auto" :p 1 :color "text.secondary"}} [close]]]
       ;; Stats grid
       [grid {:container true :spacing 3}
        [grid {:item true :xs 12 :sm 6 :md 3}
         [paper {:elevation 1 :sx {:p 3 :textAlign "center" :height "100%"}}
          [typography {:variant "h3" :color "primary"}
           (str visible-count "/" total-wines)]
          [typography {:variant "body1" :color "text.secondary"} "Wines"]]]
        [grid {:item true :xs 12 :sm 6 :md 3}
         [paper {:elevation 1 :sx {:p 3 :textAlign "center" :height "100%"}}
          [typography {:variant "h3" :color "primary"} total-bottles]
          [typography {:variant "body1" :color "text.secondary"} "Bottles"]]]
        [grid {:item true :xs 12 :sm 6 :md 3}
         [paper {:elevation 1 :sx {:p 3 :textAlign "center" :height "100%"}}
          [typography
           {:variant "h3"
            :color (if (= avg-rating "-")
                     "text.secondary"
                     (get-rating-color (js/parseInt avg-rating)))}
           (if (= avg-rating "-") "-" (str avg-rating "/100"))]
          [typography {:variant "body1" :color "text.secondary"}
           "Avg. Rating"]]]
        [grid {:item true :xs 12 :sm 6 :md 3}
         [paper {:elevation 1 :sx {:p 3 :textAlign "center" :height "100%"}}
          [typography {:variant "h3" :color "primary"} (str "$" total-value)]
          [typography {:variant "body1" :color "text.secondary"}
           "Collection Value"]]]]]]]))

(defn wine-list
  [app-state]
  [box {:sx {:width "100%" :mt 3}}
   (if (:loading? @app-state)
     [box {:display "flex" :justifyContent "center" :p 4} [circular-progress]]
     (if (empty? (:wines @app-state))
       [paper {:elevation 2 :sx {:p 3 :textAlign "center"}}
        [typography {:variant "h6"} "No wines yet. Add your first wine above!"]]
       [:<>
        ;; Collection stats modal
        [collection-stats-modal app-state]
        ;; Wine details view or card grid with filtering
        (if (:selected-wine-id @app-state)
          [:div] ;; Wine details are rendered separately
          [:<> [filter-bar app-state]
           [wine-card-grid app-state (filtered-sorted-wines app-state)]])]))])

