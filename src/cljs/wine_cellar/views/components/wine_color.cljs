(ns wine-cellar.views.components.wine-color
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.form-label :refer [form-label]]
            [reagent-mui.material.slider :refer [slider]]
            [reagent-mui.material.typography :refer [typography]]
            [wine-cellar.common :as common]))

;; Wine Folly colors extracted from poster
(def wine-folly-colors
  {:pale-straw "#fdfbee"
   :medium-straw "#faf9da"
   :deep-straw "#eeebbe"
   :pale-yellow "#f4f5ce"
   :medium-yellow "#f7f3b0"
   :deep-yellow "#f2e647"
   :pale-gold "#f2eecc"
   :medium-gold "#f2e49a"
   :deep-gold "#f4d165"
   :pale-brown "#e8af3e"
   :medium-brown "#ce8e2c"
   :deep-brown "#5b391b"
   :pale-amber "#fccf6a"
   :medium-amber "#f4b245"
   :deep-amber "#e47929"
   :pale-copper "#efc7a4"
   :medium-copper "#f7ad7c"
   :deep-copper "#e67b41"
   :pale-salmon "#f7d0c9"
   :medium-salmon "#f1a595"
   :deep-salmon "#ed7e62"
   :pale-pink "#f6d5d6"
   :medium-pink "#ed9293"
   :deep-pink "#ef6972"
   :pale-ruby "#9a1b39"
   :medium-ruby "#7f1b35"
   :deep-ruby "#3b101a"
   :pale-purple "#9d1c45"
   :medium-purple "#52182e"
   :deep-purple "#210d14"
   :pale-garnet "#c02824"
   :medium-garnet "#a32129"
   :deep-garnet "#581211"
   :pale-tawny "#ac4325"
   :medium-tawny "#943921"
   :deep-tawny "#80231a"})


;; Base colors without intensity
(def base-colors-by-style
  {:white [:amber :brown :gold :yellow :straw]
   :rose [:copper :salmon :pink]
   :red [:tawny :garnet :ruby :purple]})

(def intensities [:deep :medium :pale])

(defn get-base-colors
  "Get available base colors for a wine style"
  [style-info wine-style]
  (let [info (or style-info (common/style->info wine-style))
        palette (:palette info)]
    (get base-colors-by-style palette [])))

(defn get-color-hex
  "Get hex color for base color and intensity"
  [base-color intensity]
  (when (and base-color intensity)
    (let [full-color-key (keyword (str (name intensity) "-" (name base-color)))]
      (get wine-folly-colors full-color-key))))



(defn wine-color-selector
  "Wine color selection component with Wine Folly colors"
  [{:keys [style-info wine-style selected-color selected-intensity on-change]}]
  (let [info (or style-info (common/style->info wine-style))
        available-colors (get-base-colors info wine-style)
        default-color (or (:default-color info) (first available-colors))
        default-intensity (or (:default-intensity info) :medium)
        current-color (or selected-color default-color)
        current-intensity (or selected-intensity default-intensity)
        color-index (when current-color (.indexOf available-colors current-color))
        intensity-index (when current-intensity (.indexOf intensities current-intensity))
        current-hex (get-color-hex current-color current-intensity)
        color-marks (mapv (fn [idx base]
                            {:value idx :label (name base)})
                          (range)
                          available-colors)
        intensity-marks (mapv (fn [idx intensity]
                                {:value idx :label (name intensity)})
                              (range)
                              intensities)
        mark-label-slot {:style {:left "auto"
                                 :right "100%"
                                 :marginRight "4px"
                                 :textAlign "right"}}]
    (if (empty? available-colors)
      [typography {:variant "body2" :color "text.secondary"}
       "Color selection not available for this wine style"]
      (let [color-column
            [box {:sx {:display "flex"
                       :width "40%"
                       :flexDirection "column"
                       :alignItems "flex-end"
                       :gap 1}}
             [typography {:variant "body2"
                           :sx {:width "100%"
                                :alignSelf "flex-end"
                                :textAlign "right"
                                :pr 6}}
              "Color"]
             [slider {:orientation "vertical"
                      :value (or color-index 0)
                      :min 0
                      :max (dec (count available-colors))
                      :step 1
                      :marks color-marks
                      :sx {:height 150 :alignSelf "flex-end"}
                      :slotProps {:markLabel mark-label-slot}
                      :onChange (fn [_ value]
                                  (let [new-color (nth available-colors value)]
                                    (when on-change
                                      (on-change {:color new-color
                                                  :intensity current-intensity}))))}]]
            glass-column
            [box {:sx {:display "flex"
                       :flexDirection "column"
                       :alignItems "center"
                       :width "40%"
                       :maxWidth "200px"}}
             [box {:sx {:display "flex"
                        :flexDirection "column"
                        :alignItems "center"
                        :gap 0 #_1.25
                        :width "75%"
                        :maxWidth "320px"
                        :backgroundColor "white"
                        :padding 2
                        :borderRadius 2
                        :boxShadow "0 2px 8px rgba(0,0,0,0.1)"
                        :border "1px solid #e0e0e0"}}
              [box {:sx {:width 48
                         :height 78
                         :backgroundColor (or current-hex "#f5f5f5")
                         :border "2px solid #666"
                         :borderRadius "0 0 30px 30px"
                         :position "relative"
                         :boxShadow "inset 0 2px 4px rgba(0,0,0,0.1)"
                         :clipPath "polygon(15% 0%, 85% 0%, 100% 100%, 0% 100%)"}}]]
             [box {:sx {:width "100%"
                        :maxWidth "260px"
                        :mx "auto"}}
              [slider {:orientation "horizontal"
                       :value (or intensity-index 1)
                       :min 0
                       :max (dec (count intensities))
                       :step 1
                       :marks intensity-marks
                       :sx {:width "100%"}
                       :onChange (fn [_ value]
                                   (let [new-intensity (nth intensities value)]
                                     (when on-change
                                       (on-change {:color current-color
                                                   :intensity new-intensity}))))}]
             [typography {:variant "body2"
                            :sx {:textAlign "center" :mb 0.5}}
               "Intensity"] ]]]
        [form-control {:sx {:width "100%"}}
         [form-label {:sx {:mb 2 :fontWeight "bold"}} "Wine Color"]
         [box {:sx {:display "flex"
                    :alignItems "flex-start"
                    :gap 0
                    :px 0
                    :justifyContent "center"
                    :width "100%"}}
          color-column
          glass-column]]))))

(defn wine-color-display
  "Display selected wine color as small wine glass on white background"
  [{:keys [selected-color selected-intensity size]}]
  (when (and selected-color selected-intensity)
    (let [glass-size (case size
                       :small 22
                       :large 38
                       30)
          glass-height (case size
                         :small 30
                         :large 50
                         40)
          current-hex (get-color-hex selected-color selected-intensity)]
      [box {:sx {:display "flex" :alignItems "center" :gap 1.5}}
       ;; Wine glass on white background
       [box
        {:sx {:backgroundColor "white"
              :padding 1
              :borderRadius 1
              :boxShadow "0 1px 3px rgba(0,0,0,0.2)"
              :border "1px solid #e0e0e0"}}
        [box
         {:sx {:width glass-size
               :height glass-height
               :backgroundColor (or current-hex "#f5f5f5")
               :border "1px solid #666"
               :borderRadius "0 0 12px 12px"
               :boxShadow "inset 0 1px 2px rgba(0,0,0,0.1)"
               :clipPath "polygon(20% 0%, 80% 0%, 100% 100%, 0% 100%)"}}]]
       [typography
          {:variant (case size
                      :small "caption"
                      "body2")
           :sx {:textTransform "capitalize"}}
          (str (name selected-intensity) " " (name selected-color))]])))
