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
        color-index (when current-color
                      (.indexOf available-colors current-color))
        intensity-index (when current-intensity
                          (.indexOf intensities current-intensity))
        current-hex (get-color-hex current-color current-intensity)]
    (if (empty? available-colors)
      [typography {:variant "body2" :color "text.secondary"}
       "Color selection not available for this wine style"]
      [form-control {:sx {:width "100%"}}
       [form-label {:sx {:mb 2 :fontWeight "bold"}} "Wine Color"]
       [box
        {:sx {:display "flex"
              :alignItems "center"
              :gap 4
              :justifyContent "center"}}
        [box
         {:sx {:display "flex"
               :flexDirection "column"
               :alignItems "center"
               :gap 1}}
         [typography {:variant "body2"} "Color"]
         [slider
          {:orientation "vertical"
           :value (or color-index 0)
           :min 0
           :max (dec (count available-colors))
           :step 1
           :marks (mapv (fn [idx color] {:value idx :label (name color)})
                        (range)
                        available-colors)
           :sx {:height 160}
           :onChange (fn [_ value]
                       (let [new-color (nth available-colors value)]
                         (when on-change
                           (on-change {:color new-color
                                       :intensity current-intensity}))))}]]
        [box
         {:sx {:display "flex"
               :flexDirection "column"
               :alignItems "center"
               :gap 2
               :backgroundColor "white"
               :padding 3
               :borderRadius 2
               :boxShadow "0 2px 8px rgba(0,0,0,0.1)"
               :border "1px solid #e0e0e0"}}
         [box
          {:sx {:width 60
                :height 80
                :backgroundColor (or current-hex "#f5f5f5")
                :border "2px solid #666"
                :borderRadius "0 0 30px 30px"
                :position "relative"
                :boxShadow "inset 0 2px 4px rgba(0,0,0,0.1)"
                :clipPath "polygon(15% 0%, 85% 0%, 100% 100%, 0% 100%)"}}]
         (when current-hex
           [typography
            {:variant "caption"
             :sx {:textAlign "center" :fontFamily "monospace"}} current-hex])
         (when (and current-color current-intensity)
           [typography
            {:variant "body2"
             :sx {:textAlign "center" :textTransform "capitalize"}}
            (str (name current-intensity) " " (name current-color))])]
        [box
         {:sx {:display "flex"
               :flexDirection "column"
               :alignItems "center"
               :gap 1}}
         [typography {:variant "body2"} "Intensity"]
         [slider
          {:orientation "vertical"
           :value (or intensity-index 1)
           :min 0
           :max (dec (count intensities))
           :step 1
           :marks (mapv (fn [idx intensity]
                          {:value idx :label (name intensity)})
                        (range)
                        intensities)
           :sx {:height 160}
           :onChange (fn [_ value]
                       (let [new-intensity (nth intensities value)]
                         (when on-change
                           (on-change {:color current-color
                                       :intensity new-intensity}))))}]]]])))

(defn wine-color-display
  "Display selected wine color as small wine glass on white background"
  [{:keys [selected-color selected-intensity size show-label?]}]
  (when (and selected-color selected-intensity)
    (let [glass-size (case size
                       :small 24
                       :large 40
                       32)
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
       (when show-label?
         [typography
          {:variant (case size
                      :small "caption"
                      "body2")
           :sx {:textTransform "capitalize"}}
          (str (name selected-intensity) " " (name selected-color))])])))
