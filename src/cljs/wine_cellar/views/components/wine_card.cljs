(ns wine-cellar.views.components.wine-card
  (:require [goog.string :as gstring]
            [goog.string.format]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.grid :refer [grid]]
            [wine-cellar.utils.vintage :refer
             [tasting-window-status tasting-window-color]]
            [wine-cellar.views.components :refer [quantity-control]]
            [wine-cellar.api :as api]))

;; Utility functions
(defn get-rating-color
  [rating]
  (cond (>= rating 90) "rating.high"
        (>= rating 80) "rating.medium"
        :else "rating.low"))

(defn wine-thumbnail
  [app-state wine]
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
    (if (:vintage wine) (str (:vintage wine)) "NV")]])

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
     (gstring/format "$%.2f" (or (:price wine) 0))]]])

(defn wine-alcohol-info
  [wine]
  [grid {:item true :xs 6}
   [box {:sx {:display "flex" :alignItems "center"}}
    [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}} "ABV:"]
    [typography {:variant "body2"}
     (if-let [abv (:alcohol_percentage wine)]
       (str abv "%")
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
              (clojure.string/join ", "))
         "-")
       "-")]]])

(defn wine-details-grid
  [wine]
  [grid {:container true :spacing 0.5} ;; Reduced spacing
   [wine-region-info wine] [wine-style-info wine]
   [wine-classification-info wine] [wine-location-info wine]
   [wine-price-info wine] [wine-alcohol-info wine] [wine-varieties-info wine]])

(defn wine-rating-display
  [wine]
  [box {:sx {:display "flex" :alignItems "center"}}
   (if-let [rating (:latest_rating wine)]
     [box {:sx {:display "flex" :alignItems "center"}}
      [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}}
       "Rating:"]
      [typography {:sx {:color (get-rating-color rating) :fontWeight "bold"}}
       (str rating "/100")]]
     [typography {:variant "body2" :color "text.secondary"} "No Rating"])])

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
  [wine status drink-from-year drink-until-year]
  [box
   {:sx {:mt "auto"
         :pt 0.5 ;; Reduced padding
         :borderTop "1px solid rgba(0,0,0,0.08)"
         :display "flex"
         :justifyContent "space-between"
         :alignItems "center"}} [wine-rating-display wine]
   [wine-tasting-window status drink-from-year drink-until-year]])

;; Using quantity-control from components.cljs

(defn wine-quantity-display
  [app-state wine]
  [box {:sx {:display "flex" :alignItems "center"}}
   [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}}
    "Quantity:"] [quantity-control app-state (:id wine) (:quantity wine)]])

(defn wine-controls
  [app-state wine]
  [box
   {:sx {:display "flex" :justifyContent "center" :alignItems "center" :mt 0.5}} ;; Further
                                                                                 ;; reduced
                                                                                 ;; margin
   [wine-quantity-display app-state wine]])

(defn wine-card
  [app-state wine]
  (let [status (tasting-window-status wine)
        drink-from-year (or (:drink_from_year wine)
                            (when-let [date (:drink_from wine)]
                              (.getFullYear (js/Date. date))))
        drink-until-year (or (:drink_until_year wine)
                             (when-let [date (:drink_until wine)]
                               (.getFullYear (js/Date. date))))]
    [paper
     {:elevation 2
      :sx
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
       :bgcolor "background.paper"
       :backgroundImage
       (when (= (:style wine) "Red")
         "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))")
       :cursor "pointer"
       ":hover" {:transform "translateY(-2px)" :boxShadow 4}}
      :onClick #(api/load-wine-detail-page app-state (:id wine))}
     ;; Wine header with thumbnail and basic info
     [wine-header app-state wine]
     ;; Wine details
     [box {:sx {:mb 0}} ;; Removed margin completely
      [wine-details-grid wine]
      ;; Bottom section with rating, tasting window
      [wine-bottom-info wine status drink-from-year drink-until-year]
      ;; Quantity control and action buttons
      [box
       {:onClick (fn [e] (.stopPropagation e)) ;; Prevent card click when
                                               ;; interacting with controls
        :sx {:width "100%"}} [wine-controls app-state wine]]]]))
