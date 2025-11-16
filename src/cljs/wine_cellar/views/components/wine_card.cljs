(ns wine-cellar.views.components.wine-card
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.checkbox :refer [checkbox]]
            [reagent-mui.material.form-control-label :refer
             [form-control-label]]
            [wine-cellar.utils.vintage :refer
             [tasting-window-status tasting-window-color]]
            [wine-cellar.views.components :refer [quantity-control]]
            [wine-cellar.state :as app-state]
            [wine-cellar.api :as api]))

;; Utility functions
(defn get-rating-color
  [rating]
  (cond (>= rating 90) "rating.high"
        (>= rating 80) "rating.medium"
        :else "rating.low"))

(defn wine-thumbnail
  [_app-state wine]
  [box
   {:sx {:mr 2
         :width 120
         :height 120
         :display "flex"
         :alignItems "center"
         :justifyContent "center"
         :borderRadius 1
         :bgcolor "background.default"}}
   (if (:label_thumbnail wine)
     [box
      {:component "img"
       :src (:label_thumbnail wine)
       :sx {:width "100%"
            :height "100%"
            :objectFit "contain"
            :borderRadius 1
            :transition "transform 0.2s"
            ":hover" {:transform "scale(1.05)"}}}]
     [typography
      {:variant "body2" :color "text.secondary" :sx {:textAlign "center"}}
      "No Image"])])

(defn wine-basic-info
  [wine]
  [box {:sx {:flex 1}}
   [typography
    {:variant "h6"
     :component "h3"
     :sx {:fontSize "1rem"
          :fontWeight "bold"
          :mb 0.3 ;; Reduced margin
          :lineHeight 1.1}} ;; Reduced line height
    (:producer wine)]
   [typography {:variant "body1" :sx {:mb 0.3}} ;; Reduced margin
    (:name wine)]
   [typography {:variant "body2" :color "text.secondary"}
    (let [vintage (:vintage wine)
          disgorgement (:disgorgement_year wine)]
      (cond (and (not vintage) disgorgement) (str "NV (" disgorgement " Disg.)")
            (and vintage disgorgement) (str vintage " (" disgorgement " Disg.)")
            vintage (str vintage)
            :else "NV"))]])

(defn wine-header
  [app-state wine]
  [box {:sx {:display "flex" :mb 0}} [wine-thumbnail app-state wine]
   [wine-basic-info wine]])

(defn wine-region-info
  [wine]
  [grid {:item true :xs 12}
   [box {:sx {:display "flex" :alignItems "center" :mb 0.3}} ;; Reduced
                                                             ;; margin
    [typography
     {:variant "body2" :color "text.secondary" :sx {:mr 1 :minWidth "60px"}}
     "Region:"]
    [typography {:variant "body2"}
     (str (:region wine) (when (:aoc wine) (str " • " (:aoc wine))))]]])

(defn wine-style-info
  [wine]
  [grid {:item true :xs 6}
   [box {:sx {:display "flex" :alignItems "center"}}
    [typography
     {:variant "body2" :color "text.secondary" :sx {:mr 1 :minWidth "60px"}}
     "Style:"] [typography {:variant "body2"} (or (:style wine) "-")]]])

(defn wine-classification-info
  [wine]
  [grid {:item true :xs 6}
   [box {:sx {:display "flex" :alignItems "center"}}
    [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}} "Class:"]
    [typography
     {:variant "body2"
      :sx {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"}}
     (or (:classification wine) "-")]]])

(defn wine-location-info
  [wine]
  [grid {:item true :xs 6}
   [box {:sx {:display "flex" :alignItems "center"}}
    [typography
     {:variant "body2" :color "text.secondary" :sx {:mr 1 :minWidth "60px"}}
     "Location:"] [typography {:variant "body2"} (or (:location wine) "-")]]])

(defn wine-price-info
  [wine]
  [grid {:item true :xs 6}
   [box {:sx {:display "flex" :alignItems "center"}}
    [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}} "Price:"]
    [typography {:variant "body2"}
     (if-let [price (:price wine)]
       (gstring/format "$%.2f" price)
       "-")]]])

(defn wine-alcohol-info
  [wine]
  [grid {:item true :xs 6}
   [box {:sx {:display "flex" :alignItems "center"}}
    [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}} "ABV:"]
    [typography {:variant "body2"}
     (if-let [abv (:alcohol_percentage wine)]
       (str abv "%")
       "-")]]])

(defn wine-dosage-info
  [wine]
  [grid {:item true :xs 6}
   [box {:sx {:display "flex" :alignItems "center"}}
    [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}}
     "Dosage:"]
    [typography {:variant "body2"}
     (if-let [dosage (:dosage wine)]
       (str (js/Math.round dosage) " g/L")
       "-")]]])

(defn wine-varieties-info
  [wine]
  [grid {:item true :xs 12}
   [box {:sx {:display "flex" :alignItems "center" :mb 0.3}}
    [typography
     {:variant "body2" :color "text.secondary" :sx {:mr 1 :minWidth "60px"}}
     "Varieties:"]
    [typography {:variant "body2"}
     (if-let [varieties (:varieties wine)]
       (if (seq varieties)
         (->> varieties
              (map (fn [v]
                     (if (:percentage v)
                       (str (:name v) " " (:percentage v) "%")
                       (:name v))))
              (str/join ", "))
         "-")
       "-")]]])

(defn wine-details-grid
  [wine]
  [grid {:container true :spacing 0.5} ;; Reduced spacing
   [wine-region-info wine] [wine-style-info wine]
   [wine-classification-info wine] [wine-location-info wine]
   [wine-price-info wine] [wine-alcohol-info wine] [wine-dosage-info wine]
   [wine-varieties-info wine]])

(defn wine-rating-display
  [wine]
  [box {:sx {:display "flex" :alignItems "center"}}
   (let [internal-rating (:latest_internal_rating wine)
         external-rating (:average_external_rating wine)]
     (if (or internal-rating external-rating)
       [box {:sx {:display "flex" :alignItems "center"}}
        [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}}
         "Rating:"]
        [box {:sx {:display "flex" :alignItems "center" :gap 0.5}}
         ;; Internal rating
         (when internal-rating
           [box
            {:sx {:display "flex"
                  :alignItems "center"
                  :px 1
                  :py 0.3
                  :borderRadius 1
                  :border "1px solid"
                  :bgcolor "rgba(25,118,210,0.08)"
                  :borderColor "rgba(25,118,210,0.2)"}}
            [typography
             {:sx {:color (get-rating-color internal-rating)
                   :fontWeight "bold"
                   :mr 0.5}} (str internal-rating)]
            [typography
             {:variant "caption"
              :sx {:color "text.secondary" :fontSize "0.7rem"}} "(Internal)"]])
         ;; External rating (average)
         (when external-rating
           [box
            {:sx {:display "flex"
                  :alignItems "center"
                  :px 1
                  :py 0.3
                  :borderRadius 1
                  :border "1px solid"
                  :bgcolor "rgba(0,0,0,0.04)"
                  :borderColor "rgba(0,0,0,0.12)"}}
            [typography
             {:sx {:color (get-rating-color external-rating)
                   :fontWeight "bold"
                   :mr 0.5}} (str external-rating)]
            [typography
             {:variant "caption"
              :sx {:color "text.secondary" :fontSize "0.7rem"}}
             "(External)"]])]]
       [typography {:variant "body2" :color "text.secondary"} "No Rating"]))])

(defn wine-tasting-window
  [status drink-from-year drink-until-year]
  [box {:sx {:display "flex" :alignItems "center"}}
   [box
    {:sx {:color (tasting-window-color status)
          :fontWeight "medium"
          :display "flex"
          :alignItems "center"}}
    [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}} "Drink:"]
    [box {:sx {:display "flex"}}
     (when drink-from-year
       [typography {:variant "body2" :sx {:lineHeight 1.2}}
        (str drink-from-year)])
     (when (and drink-from-year drink-until-year)
       [typography {:variant "body2" :sx {:lineHeight 1.2 :mx 0.5}} "—"])
     (when drink-until-year
       [typography {:variant "body2" :sx {:lineHeight 1.2}}
        (str drink-until-year)])]]])

(defn wine-bottom-info
  [wine]
  [box {:sx {:mt "auto" :pt 0.5 :borderTop "1px solid rgba(0,0,0,0.08)"}}
   ;; Rating row
   [wine-rating-display wine]])

;; Using quantity-control from components.cljs

(defn wine-verification-checkbox
  [app-state wine]
  [form-control-label
   {:control (r/as-element [checkbox
                            {:checked (boolean (:verified wine))
                             :size "small"
                             :onChange
                             (fn [e]
                               (let [new-verified (.. e -target -checked)]
                                 (api/update-wine app-state
                                                  (:id wine)
                                                  {:verified new-verified})))}])
    :label "Verified"
    :sx {:ml 0 :mr 1}}])

(defn wine-quantity-display
  [app-state wine]
  (let [quantity (:quantity wine)
        original-quantity (:original_quantity wine)
        display-text (if original-quantity
                       (str quantity "/" original-quantity)
                       (str quantity))]
    [box {:sx {:display "flex" :alignItems "center" :gap 1}}
     [typography {:variant "body2" :color "text.secondary"} "Quantity:"]
     [quantity-control app-state (:id wine) quantity display-text
      original-quantity]]))

(defn wine-controls
  [app-state wine status drink-from-year drink-until-year]
  [box {:sx {:mt 0.5}}
   ;; Top row: drinking window + quantity
   [box
    {:sx {:display "flex" :justifyContent "space-between" :alignItems "center"}}
    ;; Left side: drinking window
    [wine-tasting-window status drink-from-year drink-until-year]
    ;; Right side: quantity
    [wine-quantity-display app-state wine]]
   ;; Bottom row: verification checkbox (when enabled)
   (when (:show-verification-checkboxes? @app-state)
     [box {:sx {:mt 0.5}} [wine-verification-checkbox app-state wine]])])

(defn- selected-wine?
  [app-state wine-id]
  (contains? (get @app-state :selected-wine-ids #{}) wine-id))

(defn- wine-selection-checkbox
  [app-state wine]
  (when-let [wine-id (:id wine)]
    (let [checked? (selected-wine? app-state wine-id)]
      [box
       {:sx {:position "absolute"
             :top 8
             :right 8
             :zIndex 2
             :backgroundColor "rgba(0,0,0,0.24)"
             :borderRadius 2}
        :on-click #(.stopPropagation %)}
       [checkbox
        {:checked checked?
         :color "primary"
         :size "small"
         :inputProps {:aria-label "Select wine"}
         :on-change (fn [event]
                      (let [checked (.. event -target -checked)]
                        (app-state/toggle-wine-selection! app-state
                                                          wine-id
                                                          checked)))}]])))

(defn wine-card
  [app-state wine]
  (let [status (tasting-window-status wine)
        drink-from-year (:drink_from_year wine)
        drink-until-year (:drink_until_year wine)
        selected? (when-let [wine-id (:id wine)]
                    (selected-wine? app-state wine-id))]
    [paper
     {:elevation (if selected? 6 2)
      :sx
      (cond->
        {:p 1.5 ;; Reduced padding
         :mb 2
         :borderRadius 2
         :position "relative"
         :overflow "hidden"
         :transition "transform 0.2s, box-shadow 0.2s"
         :height "100%"
         :display "flex"
         :flexDirection "column"
         :justifyContent "space-between"
         :backgroundImage
         (when (= (:style wine) "Red")
           "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))")
         :cursor "pointer"
         ":hover" {:transform "translateY(-2px)" :boxShadow 4}}
        selected? (assoc :border "1px solid rgba(144,202,249,0.65)"))
      :onClick #(api/load-wine-detail-page app-state (:id wine))}
     [wine-selection-checkbox app-state wine]
     ;; Wine header with thumbnail and basic info
     [wine-header app-state wine]
     ;; Wine details
     [box {:sx {:mb 0}} ;; Removed margin completely
      [wine-details-grid wine]
      ;; Bottom section with rating
      [wine-bottom-info wine]
      ;; Controls with drinking window, quantity, and verification
      [box
       {:onClick (fn [e] (.stopPropagation e)) ;; Prevent card click when
                                               ;; interacting with controls
        :sx {:width "100%"}}
       [wine-controls app-state wine status drink-from-year
        drink-until-year]]]]))
