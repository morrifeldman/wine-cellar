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
            [reagent-mui.icons.wine-bar :refer [wine-bar]]
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
  (cond (>= rating 95) "#FFD54F" ;; Gold
        (>= rating 90) "#E8C3C8" ;; Primary Light (Pinkish)
        (>= rating 85) "#B0BEC5" ;; Blue Grey
        :else "#9E9E9E")) ;; Grey

(defn- dot-join
  "Joins a sequence of strings with a dot separator, filtering out nils/empties."
  [items]
  (->> items
       (remove str/blank?)
       (str/join " • ")))

(defn wine-thumbnail
  [_app-state wine]
  [box
   {:sx {:mr 2
         :width 70
         :height 130
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

(defn wine-geography-info
  [wine]
  (let [{:keys [style region appellation appellation_tier]} wine
        ;; Deduplicate: if appellation is the same as region, don't show
        ;; both
        display-appellation (when-not (= region appellation) appellation)
        ;; Combine appellation and tier if both exist
        app-with-tier (if (and display-appellation appellation_tier)
                        (str display-appellation " " appellation_tier)
                        (or display-appellation appellation_tier))
        line-text (dot-join [style region app-with-tier])]
    (when-not (str/blank? line-text)
      [grid {:item true :xs 12}
       [typography {:variant "body2" :sx {:mb 0.3}} line-text]])))

(defn wine-classification-line
  [wine]
  (let [{:keys [classification designation]} wine
        line-text (dot-join [classification designation])]
    (when-not (str/blank? line-text)
      [grid {:item true :xs 12}
       [typography {:variant "body2" :color "text.secondary" :sx {:mb 0.3}}
        line-text]])))

(defn wine-specs-line
  [wine]
  (let [location (:location wine)
        price (when-let [p (:price wine)]
                (gstring/format "$%.0f" (js/Number p)))
        abv (when-let [a (:alcohol_percentage wine)] (str a "%"))
        dosage (when-let [d (:dosage wine)]
                 (str (js/Math.round (js/Number d)) " g/L"))
        line-text (dot-join [location price abv dosage])]
    (when-not (str/blank? line-text)
      [grid {:item true :xs 12}
       [typography {:variant "body2" :color "text.secondary" :sx {:mb 0.3}}
        line-text]])))

(defn wine-varieties-info
  [wine]
  (when-let [varieties (seq (:varieties wine))]
    [grid {:item true :xs 12}
     [box
      {:sx {:display "grid"
            :gridTemplateColumns "auto auto"
            :justifyContent "start"
            :columnGap 1
            :rowGap 0.3
            :mb 0.3}}
      (let [sorted-varieties (sort-by #(or (:percentage %) 0) > varieties)]
        (for [[idx v] (map-indexed vector sorted-varieties)]
          ^{:key (str (:name v) idx)}
          [:<>
           [typography
            {:variant "body2" :color "text.secondary" :sx {:lineHeight 1.2}}
            (:name v)]
           (when (:percentage v)
             [typography
              {:variant "body2"
               :sx {:fontWeight "bold"
                    :color "#ffffff"
                    :lineHeight 1.2
                    :textAlign "right"}}
              (str (js/Math.round (:percentage v)) "%")])]))]]))

(defn wine-details-grid
  [wine]
  [grid {:container true :spacing 0.5} ;; Reduced spacing
   [wine-geography-info wine] [wine-classification-line wine]
   [wine-specs-line wine] [wine-varieties-info wine]])

(defn internal-rating-badge
  [rating]
  [box
   {:sx {:display "flex"
         :alignItems "center"
         :px 1.5
         :py 0.4
         :borderRadius "20px"
         :border "1px solid"
         :bgcolor "rgba(25,118,210,0.08)"
         :borderColor "rgba(25,118,210,0.2)"}}
   [typography
    {:sx {:color (get-rating-color rating) :fontWeight "bold" :mr 0.5}}
    (str rating)]
   [typography
    {:variant "caption" :sx {:color "text.secondary" :fontSize "0.7rem"}}
    "(Internal)"]])

(defn external-rating-badge
  [rating]
  [box
   {:sx {:display "flex"
         :alignItems "center"
         :px 1.5
         :py 0.4
         :borderRadius "20px"
         :border "1px solid"
         :bgcolor "rgba(0,0,0,0.04)"
         :borderColor "rgba(0,0,0,0.12)"}}
   [typography
    {:sx {:color (get-rating-color rating) :fontWeight "bold" :mr 0.5}}
    (str rating)]
   [typography
    {:variant "caption" :sx {:color "text.secondary" :fontSize "0.7rem"}}
    "(External)"]])

(defn wine-rating-display
  [wine]
  (let [internal-rating (:latest_internal_rating wine)
        external-rating (:average_external_rating wine)]
    (when (or internal-rating external-rating)
      [box {:sx {:display "flex" :alignItems "center"}}
       [box {:sx {:display "flex" :alignItems "center" :gap 0.5}}
        (when internal-rating [internal-rating-badge internal-rating])
        (when external-rating [external-rating-badge external-rating])]])))

(defn- tasting-window-bg
  [status]
  (case status
    :too-young "rgba(237,108,2,0.12)"
    :ready "rgba(46,125,50,0.12)"
    :too-old "rgba(211,47,47,0.12)"
    "transparent"))

(defn wine-tasting-window
  [status drink-from-year drink-until-year]
  [box {:sx {:display "flex" :alignItems "center"}}
   [box
    {:sx {:color (tasting-window-color status)
          :fontWeight "medium"
          :display "flex"
          :alignItems "center"}}
    [box
     {:sx {:display "flex"
           :alignItems "center"
           :bgcolor (tasting-window-bg status)
           :borderRadius "20px"
           :px 1
           :py 0.2}}
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
     [quantity-control app-state (:id wine) quantity display-text
      original-quantity
      {:mode :card :minus-icon [wine-bar {:fontSize "small"}]}]]))

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

(defn- wine-style-border-color
  [style]
  (case style
    "Red" "rgba(160, 50, 65, 0.75)"
    "White" "rgba(200, 170, 65, 0.75)"
    "Rosé" "rgba(220, 125, 140, 0.75)"
    "Sparkling" "rgba(190, 185, 210, 0.7)"
    "Orange" "rgba(190, 115, 45, 0.75)"
    "rgba(120, 80, 90, 0.4)"))

(defn- wine-style-background
  [style]
  (case style
    "Red" "linear-gradient(to right, rgba(114,47,55,0.05), transparent)"
    "White" "linear-gradient(to right, rgba(180,150,55,0.05), transparent)"
    "Rosé" "linear-gradient(to right, rgba(200,95,110,0.05), transparent)"
    "Sparkling" "linear-gradient(to right, rgba(180,175,200,0.04), transparent)"
    "Orange" "linear-gradient(to right, rgba(175,105,40,0.05), transparent)"
    nil))

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
      :sx (cond-> {:p 1.5 ;; Reduced padding
                   :mb 2
                   :borderRadius 2
                   :position "relative"
                   :overflow "hidden"
                   :transition "transform 0.2s, box-shadow 0.2s"
                   :height "100%"
                   :display "flex"
                   :flexDirection "column"
                   :justifyContent "space-between"
                   :borderLeft (str "3px solid "
                                    (wine-style-border-color (:style wine)))
                   :backgroundImage (wine-style-background (:style wine))
                   :cursor "pointer"
                   ":hover" {:transform "translateY(-2px)" :boxShadow 4}}
            selected? (assoc :border "1px solid rgba(144,202,249,0.65)"))
      :onClick #(api/load-wine-detail-page app-state (:id wine))}
     [wine-selection-checkbox app-state wine]
     ;; Wine header with thumbnail and basic info
     [wine-header app-state wine]
     ;; Wine details
     [box {:sx {:mb 0 :mt -1.5}} ;; Added negative margin to pull up
                                 ;; details
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
