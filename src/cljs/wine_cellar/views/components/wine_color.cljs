(ns wine-cellar.views.components.wine-color
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.form-label :refer [form-label]]
            [reagent-mui.material.slider :refer [slider]]
            [reagent-mui.material.typography :refer [typography]]
            [wine-cellar.common :as common]))

;; Wine Folly colors extracted from poster
(def wine-folly-colors
  {:pale-straw "#dddac2"
   :medium-straw "#dad4a6"
   :deep-straw "#e6e19f"
   :pale-yellow "#f4f1bb"
   :medium-yellow "#e2db7d"
   :deep-yellow "#e5d649"
   :pale-gold "#e1d7a1"
   :medium-gold "#e6d580"
   :deep-gold "#f2d063"
   :pale-brown "#daa235"
   :medium-brown "#bf7a29"
   :deep-brown "#74411d"
   :pale-amber "#e8b249"
   :medium-amber "#e79526"
   :deep-amber "#de7129"
   :pale-copper "#ebb78f"
   :medium-copper "#eb8d4e"
   :deep-copper "#df6d34"
   :pale-salmon "#e7b3a9"
   :medium-salmon "#f08d79"
   :deep-salmon "#ea6445"
   :pale-pink "#f2c2c2"
   :medium-pink "#e77c81"
   :deep-pink "#e05162"
   :pale-ruby "#8d1631"
   :medium-ruby "#8e192f"
   :deep-ruby "#781b2d"
   :pale-purple "#951940"
   :medium-purple "#721a3a"
   :deep-purple "#571329"
   :pale-garnet "#bf2d24"
   :medium-garnet "#951c20"
   :deep-garnet "#731714"
   :pale-tawny "#bb4d26"
   :medium-tawny "#983d20"
   :deep-tawny "#762317"})


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

(defn- WineGlassIcon
  "SVG Wine Glass Icon with realistic shading, highlights, and 3D effects"
  [{:keys [color width height viewBox]
    :or {width 48 height 96 viewBox "0 0 64 128"}}]
  (let [gradient-id (str "glass-gradient-" (hash color))]
    [:svg
     {:viewBox viewBox
      :width width
      :height height
      :style {:filter "drop-shadow(0px 4px 6px rgba(0,0,0,0.2))"}}
     [:defs
      ;; Complex gradient for volume: Bright top (light entry), Deep bottom
      ;; (sediment/depth)
      [:linearGradient {:id gradient-id :x1 "0%" :y1 "0%" :x2 "0%" :y2 "100%"}
       [:stop {:offset "0%" :stop-color "white" :stop-opacity "0.9"}]
       [:stop {:offset "15%" :stop-color "white" :stop-opacity "0.3"}]
       [:stop {:offset "45%" :stop-color "white" :stop-opacity "0.0"}] ;; True
                                                                       ;; color
                                                                       ;; window
       [:stop {:offset "80%" :stop-color "black" :stop-opacity "0.3"}]
       [:stop {:offset "100%" :stop-color "black" :stop-opacity "0.6"}]]]
     ;; --- Base & Stem (Behind Bowl) ---
     ;; Base with slight perspective curve
     [:path
      {:d "M16 120 Q32 125 48 120 L48 119 Q32 124 16 119 Z"
       :fill "#d0d0d0"
       :stroke "#999"
       :stroke-width "0.5"}]
     ;; Stem
     [:path
      {:d "M32 84 L32 119"
       :stroke "#a0a0a0"
       :stroke-width "2.5"
       :stroke-linecap "square"}]
     ;; --- Liquid & Bowl ---
     [:g
      ;; 1. Main Liquid Body
      [:path
       {:d "M12 24 C12 62 18 84 32 84 C46 84 52 62 52 24 Z"
        :fill (or color "#f5f5f5")}]
      ;; 2. Gradient Overlay for 3D Volume
      [:path
       {:d "M12 24 C12 62 18 84 32 84 C46 84 52 62 52 24 Z"
        :fill (str "url(#" gradient-id ")")
        :style {:mix-blend-mode "multiply"}}]
      ;; 3. Glass Wall Highlight (Inner Left)
      [:path
       {:d "M13.5 26 C13.5 60 19 82 30 82"
        :stroke "rgba(255,255,255,0.25)"
        :stroke-width "1.5"
        :fill "none"}]
      ;; 4. Specular Highlight (The "Gloss" Reflection) - Key for realism
      [:path
       {:d "M15 32 Q14 55 19 72"
        :stroke "rgba(255,255,255,0.85)"
        :stroke-width "2.5"
        :stroke-linecap "round"
        :fill "none"
        :filter "blur(0.8px)"}]
      ;; 5. Meniscus / Surface
      ;; Surface Fill (faint reflection)
      [:ellipse
       {:cx "32" :cy "24" :rx "20" :ry "5" :fill "rgba(255,255,255,0.15)"}]
      ;; Darker Meniscus Ring (where liquid touches glass)
      [:ellipse
       {:cx "32"
        :cy "24"
        :rx "19.5"
        :ry "4.5"
        :stroke "rgba(0,0,0,0.15)"
        :stroke-width "1"
        :fill "none"}]
      ;; Front Rim Highlight (Glass Edge)
      [:path
       {:d "M12 24 A 20 5 0 0 0 52 24"
        :stroke "rgba(255,255,255,0.6)"
        :stroke-width "1.5"
        :fill "none"}]
      ;; 6. Bowl Outline (Subtle Container)
      [:path
       {:d "M12 24 C12 62 18 84 32 84 C46 84 52 62 52 24"
        :fill "none"
        :stroke "rgba(0,0,0,0.15)" ;; Very faint dark line for definition
        :stroke-width "1"}]]]))

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
        current-hex (get-color-hex current-color current-intensity)
        color-marks (mapv (fn [idx base] {:value idx :label (name base)})
                          (range)
                          available-colors)
        intensity-marks (mapv (fn [idx intensity]
                                {:value idx :label (name intensity)})
                              (range)
                              intensities)
        mark-label-slot
        {:style
         {:left "auto" :right "100%" :marginRight "4px" :textAlign "right"}}]
    (if (empty? available-colors)
      [typography {:variant "body2" :color "text.secondary"}
       "Color selection not available for this wine style"]
      (let [color-column
            [box
             {:sx {:display "flex"
                   :flex 1
                   :maxWidth "200px"
                   :flexDirection "column"
                   :alignItems "center"
                   :gap 1}}
             [typography
              {:variant "body2" :sx {:width "100%" :textAlign "center"}}
              "Color"]
             [slider
              {:orientation "vertical"
               :value (or color-index 0)
               :min 0
               :max (dec (count available-colors))
               :step 1
               :marks color-marks
               :sx {:height 150}
               :slotProps {:markLabel mark-label-slot}
               :onChange (fn [_ value]
                           (let [new-color (nth available-colors value)]
                             (when on-change
                               (on-change {:color new-color
                                           :intensity current-intensity}))))}]]
            glass-column
            [box
             {:sx {:display "flex"
                   :flexDirection "column"
                   :alignItems "center"
                   :flex 1
                   :maxWidth "200px"}}
             [box
              {:sx {:display "flex"
                    :flexDirection "column"
                    :alignItems "center"
                    :gap 0
                    :width "100%"
                    :backgroundColor "white"
                    :padding 2
                    :borderRadius 2
                    :boxShadow "0 2px 8px rgba(0,0,0,0.1)"
                    :border "1px solid #e0e0e0"}}
              [WineGlassIcon
               {:color current-hex
                :width 100
                :height 140
                :viewBox "0 12 64 90"}]]
             [box {:sx {:width "100%" :maxWidth "260px" :mx "auto"}}
              [slider
               {:orientation "horizontal"
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
              [typography {:variant "body2" :sx {:textAlign "center" :mb 0.5}}
               "Intensity"]]]]
        [form-control {:sx {:width "100%"}}
         [form-label {:sx {:mb 2 :fontWeight "bold"}} "Wine Color"]
         [box
          {:sx {:display "flex"
                :alignItems "flex-start"
                :gap 4
                :px 0
                :justifyContent "center"
                :width "100%"}} color-column glass-column]]))))

(defn wine-color-display
  "Display selected wine color as small wine glass on white background"
  [{:keys [selected-color selected-intensity size]}]
  (when (and selected-color selected-intensity)
    (let [{:keys [width height]} (case size
                                   :small {:width 20 :height 40}
                                   :large {:width 40 :height 80}
                                   {:width 30 :height 60})
          current-hex (get-color-hex selected-color selected-intensity)]
      [box {:sx {:display "flex" :alignItems "center" :gap 1.5}}
       ;; Wine glass on white background
       [box
        {:sx {:backgroundColor "white"
              :padding "6px"
              :borderRadius "4px"
              :boxShadow "0 1px 3px rgba(0,0,0,0.15)"
              :border "1px solid #e0e0e0"
              :display "flex"
              :alignItems "center"
              :justifyContent "center"}}
        [WineGlassIcon {:color current-hex :width width :height height}]]
       [typography
        {:variant (case size
                    :small "caption"
                    "body2")
         :sx {:textTransform "capitalize"}}
        (str (name selected-intensity) " " (name selected-color))]])))
