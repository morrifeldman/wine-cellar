(ns wine-cellar.views.components.stats-charts
  (:require [reagent.core :as r]
            ["recharts" :refer
             [ResponsiveContainer AreaChart Area LineChart Line XAxis YAxis
              CartesianGrid Tooltip ReferenceLine Legend]]))

(def palette
  ["#64b5f6" "#ffb74d" "#ce93d8" "#81c784" "#4db6ac" "#f06292" "#ba68c8"
   "#7986cb" "#a1887f"])

(defn- format-number
  [value]
  (.toLocaleString (js/Number. (or value 0)) "en-US"))

(defn- metric->label
  [metric value]
  (case metric
    :bottles (str value " " (if (= value 1) "bottle" "bottles"))
    (str value " " (if (= value 1) "wine" "wines"))))

(defn- tooltip-formatter
  [metric value props]
  (let [count (if (array? value) (aget value 0) value)
        label (some-> props
                      (.-payload)
                      (.-year))]
    #js [(metric->label metric count) (str "Year " label)]))

(defn- build-line-data
  [series metric]
  (let [points (map :points series)
        year-points (first points)]
    (if (seq year-points)
      (map-indexed (fn [idx point]
                     (let [year (:year point)]
                       (reduce (fn [row {:keys [key points]}]
                                 (let [value (get (nth points idx) metric 0)
                                       data-key (or key "overall")]
                                   (assoc row data-key value)))
                               {:year year}
                               series)))
                   year-points)
      [])))

(defn optimal-window-chart
  [{:keys [series current-year metric]}]
  (let [metric (or metric :wines)
        multi-series? (> (count series) 1)]
    (r/with-let
     [mounted? (r/atom false)]
     (r/after-render #(reset! mounted? true))
     (cond
       (not @mounted?) [:div {:style {:height 260}}]
       (seq series)
       (let [chart-data (if multi-series?
                          (build-line-data series metric)
                          (map (fn [point]
                                 {:year (:year point)
                                  :value (get point metric 0)})
                               (:points (first series))))]
         [:> ResponsiveContainer {:width "100%" :height 260}
          (if multi-series?
            [:> LineChart
             {:data (clj->js chart-data)
              :margin (clj->js {:top 20 :right 30 :bottom 0 :left 0})}
             [:> CartesianGrid
              {:strokeDasharray "3 3" :stroke "rgba(255,255,255,0.12)"}]
             [:> XAxis
              {:dataKey "year"
               :tickFormatter (fn [value] (str value))
               :stroke "rgba(255,255,255,0.6)"}]
             [:> YAxis
              {:allowDecimals false
               :tickFormatter format-number
               :width 50
               :stroke "rgba(255,255,255,0.6)"}]
             [:> Tooltip
              {:formatter (fn [value name props]
                            (let [label (some-> props
                                                (.-payload)
                                                (.-year))]
                              #js [(metric->label metric value)
                                   (str name " • " label)]))
               :cursor (clj->js {:stroke "rgba(255,255,255,0.35)"})}]
             [:> Legend
              {:wrapperStyle (clj->js {:color "rgba(255,255,255,0.72)"})}]
             (when current-year
               [:> ReferenceLine
                {:x current-year
                 :stroke "#ffffff"
                 :strokeDasharray "3 3"
                 :label (clj->js {:value "Today" :fill "#ffffff" :offset 10})}])
             (doall (map-indexed (fn [idx {:keys [key label]}]
                                   ^{:key key}
                                   [:> Line
                                    {:type "monotone"
                                     :dataKey key
                                     :name label
                                     :stroke (nth palette
                                                  (mod idx (count palette)))
                                     :strokeWidth 2
                                     :dot false
                                     :isAnimationActive false}])
                                 series))]
            [:> AreaChart
             {:data (clj->js chart-data)
              :margin (clj->js {:top 20 :right 30 :bottom 0 :left 0})}
             [:> CartesianGrid
              {:strokeDasharray "3 3" :stroke "rgba(255,255,255,0.12)"}]
             [:> XAxis
              {:dataKey "year"
               :tickFormatter (fn [value] (str value))
               :stroke "rgba(255,255,255,0.6)"}]
             [:> YAxis
              {:allowDecimals false
               :tickFormatter format-number
               :width 50
               :stroke "rgba(255,255,255,0.6)"}]
             [:> Tooltip
              {:formatter (fn [value _ props]
                            (tooltip-formatter metric value props))
               :cursor (clj->js {:fill "rgba(255,255,255,0.08)"})}]
             (when current-year
               [:> ReferenceLine
                {:x current-year
                 :stroke "#ffb74d"
                 :strokeDasharray "4 2"
                 :label (clj->js {:value "Today"
                                  :position "insideTop"
                                  :fill "#ffb74d"
                                  :offset 8})}])
             [:> Area
              {:type "monotone"
               :dataKey "value"
               :name (case metric
                       :bottles "Bottles"
                       "Wines")
               :stroke "#64b5f6"
               :fill "rgba(100,181,246,0.35)"
               :strokeWidth 2
               :isAnimationActive false}]])])
       :else
       [:div {:style {:color "var(--mui-palette-text-secondary)"}}
        "No drinking-window data yet. Add drink-from and drink-until years to see the forecast."]))))
