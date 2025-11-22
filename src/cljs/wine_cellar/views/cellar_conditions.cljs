(ns wine-cellar.views.cellar-conditions
  (:require [reagent.core :as r]
            [wine-cellar.api :as api]
            [wine-cellar.utils.formatting :refer [format-date]]
            [goog.object :as gobj]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.material.stack :refer [stack]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            ["recharts" :refer
             [ResponsiveContainer LineChart Line XAxis YAxis CartesianGrid
              Tooltip Legend]]))

(def bucket-options
  [{:value "15m" :label "15 min"} {:value "1h" :label "1 hour"}
   {:value "6h" :label "6 hours"} {:value "1d" :label "1 day"}])

(def range-options
  [{:value :all :label "All time" :days nil}
   {:value :90d :label "90 days" :days 90}
   {:value :30d :label "30 days" :days 30} {:value :7d :label "7 days" :days 7}
   {:value :1d :label "24 hours" :days 1}])

(defn- now-iso [] (.toISOString (js/Date.)))

(defn- iso-days-ago
  [days]
  (when days
    (let [d (js/Date.)]
      (.setDate d (- (.getDate d) days))
      (.toISOString d))))

(defn- load-cellar-data!
  [app-state]
  (let [{:keys [device-id bucket range]} (:cellar-conditions @app-state)
        from (let [days (:days (some #(when (= (:value %) range) %)
                                     range-options))]
               (iso-days-ago days))]
    (api/fetch-latest-cellar-conditions app-state {:device-id device-id})
    (api/fetch-cellar-series
     app-state
     {:device-id device-id :bucket bucket :from from :to (now-iso)})))

(defn- device-options
  [latest-readings]
  (->> latest-readings
       (map :device_id)
       (remove nil?)
       distinct
       sort))

(defn- format-timestamp [ts] (if ts (format-date ts) "–"))

(defn- fahrenheit "Convert Celsius to Fahrenheit." [c] (+ (* 1.8 c) 32))

(defn- latest-card
  [{:keys [device_id measured_at temperature_c humidity_pct pressure_hpa
           battery_mv leak_detected]}]
  [paper {:elevation 2 :sx {:p 2 :flex 1 :minWidth 240}}
   [stack {:spacing 0.5}
    [typography {:variant "subtitle2" :sx {:color "text.secondary"}}
     (or device_id "(unknown device)")]
    (let [f (when temperature_c (fahrenheit temperature_c))]
      [typography {:variant "h6"}
       (if temperature_c
         (str (.toFixed (js/Number temperature_c) 1)
              " °C / "
              (.toFixed (js/Number f) 1)
              " °F")
         "–")])
    [typography {:variant "body2" :sx {:color "text.secondary"}}
     (str "Humidity "
          (or humidity_pct "–")
          "% | Pressure "
          (or pressure_hpa "–")
          " hPa | Battery "
          (or battery_mv "–")
          " mV")]
    [typography {:variant "caption" :sx {:color "text.secondary"}}
     (str "Measured " (format-timestamp measured_at))]
    (when leak_detected
      [chip {:label "Leak detected" :color "error" :size "small"}])]])

(defn- format-bucket-ts
  [ts]
  (when ts
    (let [d (js/Date. ts)]
      (.toLocaleString d
                       "en-US"
                       #js {:month "short"
                            :day "numeric"
                            :year "numeric"
                            :hour "2-digit"
                            :minute "2-digit"}))))

(defn- tooltip-formatter
  [unit]
  (fn [v _ payload]
    (let [n (js/Number v)
          p (.-payload payload)]
      #js [(str (.toFixed n 1) unit) (str (gobj/get p "device_id"))])))

(defn- chart-lines
  [devices palette metric]
  (for [[idx device] (map-indexed vector devices)]
    ^{:key device}
    [:> Line
     {:type "monotone"
      :dataKey (fn [row]
                 (when (= device (gobj/get row "device_id"))
                   (let [v (gobj/get row (name metric))]
                     (when (some? v) (js/Number v)))))
      :name device
      :stroke (nth palette (mod idx (count palette)))
      :dot false
      :connectNulls true
      :isAnimationActive false}]))

(defn- chart
  [{:keys [data metric unit]}]
  (let [convert (if (= metric :avg_temperature_c)
                  (fn [v]
                    (-> v
                        js/Number
                        fahrenheit))
                  (fn [v] (js/Number v)))
        chart-data (->> data
                        (map (fn [row]
                               (cond-> row
                                 (some? (metric row)) (update metric convert))))
                        vec)
        devices (->> chart-data
                     (map :device_id)
                     (remove nil?)
                     distinct
                     sort
                     vec)
        palette ["#8be9fd" "#ff79c6" "#f1fa8c" "#50fa7b" "#ffb86c" "#bd93f9"
                 "#ff5555" "#9aedfe"]]
    [:> ResponsiveContainer {:width "100%" :height 280}
     [:> LineChart
      {:data chart-data :margin (clj->js {:top 10 :right 30 :bottom 0 :left 0})}
      [:> CartesianGrid
       {:strokeDasharray "3 3" :stroke "rgba(255,255,255,0.12)"}]
      [:> XAxis
       {:dataKey "bucket_start"
        :tick {:fill "#f4f0eb"}
        :axisLine {:stroke "#f4f0eb"}
        :tickFormatter (fn [value]
                         (let [d (js/Date. value)]
                           (.toLocaleDateString d
                                                "en-US"
                                                #js {:month "short"
                                                     :day "numeric"})))}]
      [:> YAxis
       {:tick {:fill "#f4f0eb"}
        :axisLine {:stroke "#f4f0eb"}
        :tickFormatter (fn [v]
                         (let [n (js/Number v)] (str (.toFixed n 1) unit)))}]
      [:> Tooltip
       {:contentStyle #js {:backgroundColor "#2b0e16"
                           :border "1px solid #f4f0eb"}
        :labelStyle #js {:color "#f4f0eb"}
        :itemStyle #js {:color "#f4f0eb"}
        :labelFormatter (fn [value] (or (format-bucket-ts value) value))
        :formatter (tooltip-formatter unit)}]
      [:> Legend {:wrapperStyle #js {:color "#f4f0eb"}}]
      (chart-lines devices palette metric)]]))

(defn- bucket-select
  [app-state state]
  [form-control {:size "small" :sx {:minWidth 180}}
   [input-label {:id "bucket-label"} "Bucket size"]
   [select
    {:labelId "bucket-label"
     :value (or (:bucket state) "")
     :label "Bucket size"
     :onChange (fn [e]
                 (swap! app-state assoc-in
                   [:cellar-conditions :bucket]
                   (.. e -target -value))
                 (load-cellar-data! app-state))}
    (for [{:keys [value label]} bucket-options]
      ^{:key value} [menu-item {:value value} label])]])

(defn- range-select
  [app-state state]
  [form-control {:size "small" :sx {:minWidth 180}}
   [input-label {:id "range-label"} "Range"]
   [select
    {:labelId "range-label"
     :value (or (some-> (:range state)
                        name)
                "all")
     :label "Range"
     :onChange (fn [e]
                 (swap! app-state assoc-in
                   [:cellar-conditions :range]
                   (keyword (.. e -target -value)))
                 (load-cellar-data! app-state))}
    (for [{:keys [value label]} range-options]
      ^{:key value} [menu-item {:value (name value)} label])]])

(defn- device-select
  [app-state state devices]
  [form-control {:size "small" :sx {:minWidth 180}}
   [input-label {:id "device-label"} "Device"]
   [select
    {:labelId "device-label"
     :value (or (:device-id state) "all")
     :label "Device"
     :onChange (fn [e]
                 (let [val (.. e -target -value)
                       v (when (not= val "all") val)]
                   (swap! app-state assoc-in [:cellar-conditions :device-id] v)
                   (load-cellar-data! app-state)))}
    [menu-item {:value "all"} "All devices"]
    (for [d devices] ^{:key d} [menu-item {:value d} d])]])

(defn- filters-row
  [app-state state devices loading?]
  [box {:sx {:display "flex" :gap 2 :alignItems "center"}}
   [bucket-select app-state state] [range-select app-state state]
   [device-select app-state state devices]
   [button {:variant "outlined" :onClick #(load-cellar-data! app-state)}
    "Refresh"] (when loading? [circular-progress {:size 22}])])

(defn- latest-grid
  [latest]
  [box
   {:sx {:display "grid"
         :gridTemplateColumns "repeat(auto-fill, minmax(260px, 1fr))"
         :gap 2}}
   (for [reading latest]
     ^{:key (str (:device_id reading) (:measured_at reading))}
     [latest-card reading])])

(defn- charts-panel
  [series]
  [paper {:elevation 3 :sx {:p 2}}
   [stack {:direction "column" :spacing 2}
    [typography {:variant "h6" :sx {:color "#f4f0eb"}} "Temperature (°F)"]
    [chart {:data series :metric :avg_temperature_c :unit "°F"}]
    [typography {:variant "h6" :sx {:color "#f4f0eb"}} "Humidity"]
    [chart {:data series :metric :avg_humidity_pct :unit "%"}]
    [typography {:variant "h6" :sx {:color "#f4f0eb"}} "Pressure"]
    [chart {:data series :metric :avg_pressure_hpa :unit " hPa"}]]])

(defn- empty-state
  []
  [paper {:elevation 1 :sx {:p 2 :color "text.secondary"}}
   "No cellar readings yet. Post data from your ESP32 sentinel to see charts here."])

(defn cellar-conditions-panel
  [app-state]
  (let [state (:cellar-conditions @app-state)
        latest (:latest state)
        series (sort-by :bucket_start (:series state))
        devices (device-options latest)
        loading? (or (:loading-latest? state) (:loading-series? state))]
    (r/with-let [_ (load-cellar-data! app-state)]
                [box {:sx {:display "flex" :flexDirection "column" :gap 2}}
                 [filters-row app-state state devices loading?]
                 (when (seq latest) [latest-grid latest])
                 (when (seq series) [charts-panel series])
                 (when (and (empty? latest) (empty? series) (not loading?))
                   [empty-state])])))
