(ns wine-cellar.views.wines.list
  (:require
    [clojure.string :as str]
    [reagent.core :as r]
    [wine-cellar.summary :as summary]
    [wine-cellar.views.components.stats-charts :as stats-charts]
    [wine-cellar.views.components.wine-card :refer [wine-card get-rating-color]]
    [wine-cellar.views.wines.filters :refer [filter-bar]]
    [wine-cellar.utils.filters :refer [filtered-sorted-wines]]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.paper :refer [paper]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.circular-progress :refer [circular-progress]]
    [reagent-mui.material.linear-progress :refer [linear-progress]]
    [reagent-mui.material.icon-button :refer [icon-button]]
    [reagent-mui.material.toggle-button-group :refer [toggle-button-group]]
    [reagent-mui.material.toggle-button :refer [toggle-button]]
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

(defn- format-number
  [value]
  (.toLocaleString (js/Number. (or value 0)) "en-US"))

(defn- format-currency
  [value]
  (.toLocaleString (js/Number. (or value 0))
                   "en-US"
                   (clj->js {:style "currency"
                             :currency "USD"
                             :maximumFractionDigits 0
                             :minimumFractionDigits 0})))

(defn- metric-label
  [metric value]
  (case metric
    :bottles (str value " " (if (= value 1) "bottle" "bottles"))
    (str value " " (if (= value 1) "wine" "wines"))))

(defn- default-detail
  [item metric percent]
  (let [value (get item metric 0)
        label (metric-label metric value)]
    (if percent (str label " (" percent "%)") label)))

(def toggle-style
  {:borderRadius 2
   :backgroundColor "rgba(255,255,255,0.04)"
   :border "1px solid rgba(255,255,255,0.16)"
   "& .MuiToggleButton-root"
   {:color "rgba(255,255,255,0.72)"
    :border "none"
    :textTransform "uppercase"
    :fontSize "0.75rem"
    :fontWeight 600
    :px 1.5
    :py 0.5
    "&:hover" {:backgroundColor "rgba(255,255,255,0.12)"}
    "&.Mui-selected" {:backgroundColor "rgba(100,181,246,0.24)"
                      :color "#ffffff"
                      "&:hover" {:backgroundColor "rgba(100,181,246,0.32)"}}}})

(defn- stats-metric-toggle
  [app-state]
  (let [metric (or (:stats-metric @app-state) :wines)]
    [toggle-button-group
     {:size "small"
      :color "primary"
      :exclusive true
      :value (name metric)
      :onChange (fn [_ value]
                  (when (some? value)
                    (swap! app-state assoc :stats-metric (keyword value))))
      :sx toggle-style} [toggle-button {:value "wines"} "Wines"]
     [toggle-button {:value "bottles"} "Bottles"]]))

(defn- window-group-toggle
  [app-state]
  (let [group (or (:stats-window-group @app-state) :overall)]
    [toggle-button-group
     {:size "small"
      :color "primary"
      :exclusive true
      :value (name group)
      :onChange
      (fn [_ value]
        (when (some? value)
          (swap! app-state assoc :stats-window-group (keyword value))))
      :sx toggle-style} [toggle-button {:value "overall"} "Overall"]
     [toggle-button {:value "style"} "Style"]
     [toggle-button {:value "country"} "Country"]
     [toggle-button {:value "price"} "Price bands"]]))

(defn- stats-summary-card
  [{:keys [title value subtitle color]}]
  [grid {:item true :xs 12 :sm 6 :md 3}
   [paper
    {:elevation 1
     :sx
     {:p 2 :height "100%" :display "flex" :flexDirection "column" :gap 0.75}}
    [typography
     {:variant "overline" :color "text.secondary" :sx {:letterSpacing "0.08em"}}
     title]
    [typography
     {:variant "h4" :color (or color "primary") :sx {:fontWeight 600}} value]
    (when subtitle
      [typography {:variant "body2" :color "text.secondary"} subtitle])]])

(def subset-order [:all :in-stock :selected])

(defn- format-breakdown
  [values formatter]
  (->> subset-order
       (map (fn [subset] (formatter (get values subset))))
       (str/join " / ")))

(defn- format-rating-segment [rating] (if (some? rating) (str rating) "—"))

(defn- stats-summary-grid
  [{:keys [counts bottles avg-rating value]}]
  (let [rating-for-color (get avg-rating :in-stock)
        rating-color (if (some? rating-for-color)
                       (get-rating-color rating-for-color)
                       "text.secondary")
        wines-text (format-breakdown counts #(format-number (or % 0)))
        bottles-text (format-breakdown bottles #(format-number (or % 0)))
        rating-text (format-breakdown avg-rating format-rating-segment)
        value-text (format-breakdown value #(format-currency (or % 0)))]
    [:<>
     [typography
      {:variant "caption"
       :color "text.secondary"
       :sx {:textTransform "uppercase"
            :letterSpacing "0.08em"
            :display "block"
            :mb 1}} "All History / In Stock / Selected"]
     [grid {:container true :spacing 3}
      [stats-summary-card {:title "Wines" :value wines-text}]
      [stats-summary-card {:title "Bottles" :value bottles-text}]
      [stats-summary-card
       {:title "Avg. Rating"
        :value rating-text
        :subtitle "Per wine (internal tastings)"
        :color rating-color}]
      [stats-summary-card {:title "Collection Value" :value value-text}]]]))

(defn- breakdown-card
  [{:keys [title items totals]} metric
   {:keys [max-items empty-copy progress? detail-fn]
    :or {max-items 5 empty-copy "No data yet"}}]
  (let [progress? (if (some? progress?) progress? true)
        total (get totals metric 0)
        display-items (->> items
                           (sort-by #(get % metric 0) >)
                           (take max-items))
        render-detail (fn [item percent]
                        (if detail-fn
                          (detail-fn item {:metric metric :percent percent})
                          (default-detail item metric percent)))]
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 1
       :sx
       {:p 2.5 :height "100%" :display "flex" :flexDirection "column" :gap 1.5}}
      [typography {:variant "subtitle1" :sx {:fontWeight 600}} title]
      (if (seq display-items)
        (for [[idx {:keys [label] :as item}] (map-indexed vector display-items)]
          (let [value (get item metric 0)
                percent (when (and progress? (pos? total))
                          (* 100 (/ value total)))
                rounded (when percent (js/Math.round percent))
                detail (render-detail item rounded)]
            ^{:key (str title "-" idx "-" label)}
            [box {:sx {:display "flex" :flexDirection "column" :gap 0.5}}
             [box
              {:sx {:display "flex"
                    :justifyContent "space-between"
                    :alignItems "center"}}
              [typography {:variant "body2" :sx {:fontWeight 500}} label]
              [typography {:variant "body2" :color "text.secondary"} detail]]
             (when percent
               [linear-progress
                {:variant "determinate"
                 :value (min 100 percent)
                 :sx {:height 6
                      :borderRadius 999
                      :backgroundColor "rgba(255,255,255,0.08)"}}])]))
        [typography {:variant "body2" :color "text.secondary"} empty-copy])]]))

(defn- inventory-card
  [inventory metric {:keys [compact?]}]
  (let [ordered (vec inventory)
        rows (if compact? (take-last 4 ordered) (take-last 7 ordered))
        label-suffix (if (= metric :bottles) "" " (wines)")]
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 1
       :sx {:p 2.5
            :height "100%"
            :display "flex"
            :flexDirection "column"
            :gap 1.25}}
      [typography {:variant "subtitle1" :sx {:fontWeight 600}}
       (str "Stock by purchase year" label-suffix)]
      (if (seq rows)
        (into
         [:<>]
         (map (fn [{:keys [year remaining purchased wines-remaining
                           wines-purchased]}]
                (let [current (if (= metric :bottles) remaining wines-remaining)
                      total (if (= metric :bottles) purchased wines-purchased)]
                  ^{:key (str "inventory-" year)}
                  [box
                   {:sx {:display "flex"
                         :justifyContent "space-between"
                         :alignItems "baseline"}}
                   [typography {:variant "body2" :sx {:fontWeight 600}} year]
                   [box {:sx {:display "flex" :gap 1.5 :color "text.secondary"}}
                    [typography {:variant "body2"}
                     (str (format-number current) " remaining")]
                    [typography {:variant "body2"}
                     (str (format-number total) " original")]]]))
              rows))
        [typography {:variant "body2" :color "text.secondary"}
         "Add purchase dates to see inventory trends."])]]))

(defn- optimal-window-card
  [app-state optimal-window metric]
  (let [group (or (:stats-window-group @app-state) :overall)
        dataset (or (get optimal-window group) (get optimal-window :overall))
        series (or (:series dataset) [])
        current-year (:current-year dataset)
        unscheduled (or (:unscheduled dataset) {:wines 0 :bottles 0})
        metric-label-text (case metric
                            :bottles "Bottles"
                            "Wines")
        group-label (case group
                      :style " by style"
                      :country " by country"
                      :price " by price band"
                      "")
        title (str metric-label-text " in optimal window" group-label)
        unscheduled-value (get unscheduled metric 0)]
    [grid {:item true :xs 12}
     [paper
      {:elevation 1
       :sx
       {:p 2.5 :height "100%" :display "flex" :flexDirection "column" :gap 1.5}}
      [box
       {:sx
        {:display "flex" :justifyContent "space-between" :alignItems "center"}}
       [typography {:variant "subtitle1" :sx {:fontWeight 600}} title]
       [window-group-toggle app-state]]
      [stats-charts/optimal-window-chart
       {:series series :current-year current-year :metric metric}]
      (when (pos? unscheduled-value)
        (let [plural? (not= unscheduled-value 1)]
          [typography {:variant "caption" :color "text.secondary"}
           (str (metric-label metric unscheduled-value)
                (if plural? " do not" " does not")
                " have a tasting window yet.")]))]]))

(defn- cumulative-leaving-card
  [app-state cumulative-leaving metric]
  (let [group (or (:stats-window-group @app-state) :overall)
        dataset (or (get cumulative-leaving group)
                    (get cumulative-leaving :overall))
        series (or (:series dataset) [])
        current-year (:current-year dataset)
        metric-label-text (case metric
                            :bottles "Bottles"
                            "Wines")
        group-label (case group
                      :style " by style"
                      :country " by country"
                      :price " by price band"
                      "")
        title (str metric-label-text
                   " leaving optimal window (cumulative)"
                   group-label)]
    [grid {:item true :xs 12}
     [paper
      {:elevation 1
       :sx
       {:p 2.5 :height "100%" :display "flex" :flexDirection "column" :gap 1.5}}
      [box
       {:sx
        {:display "flex" :justifyContent "space-between" :alignItems "center"}}
       [typography {:variant "subtitle1" :sx {:fontWeight 600}} title]
       [window-group-toggle app-state]]
      [stats-charts/cumulative-leaving-chart
       {:series series :current-year current-year :metric metric}]]]))

(defn- stats-content
  [app-state stats metric {:keys [compact?]}]
  (let [{:keys [totals style price drinking-window country varieties inventory
                optimal-window cumulative-leaving]}
        stats
        max-items (if compact? 4 7)]
    [:<> [stats-summary-grid totals]
     [grid {:container true :spacing 3 :sx {:mt 0.5}}
      [breakdown-card
       {:title "By style" :items (:items style) :totals (:totals style)} metric
       {:max-items max-items
        :empty-copy "Add wine styles to see this breakdown."}]
      [breakdown-card
       {:title "By country" :items (:items country) :totals (:totals country)}
       metric
       {:max-items max-items
        :empty-copy "Record country details to populate this view."}]
      [breakdown-card
       {:title "Price bands" :items (:items price) :totals (:totals price)}
       metric
       {:max-items max-items :empty-copy "Add prices to compare value bands."}]
      [breakdown-card
       {:title "Drinking window"
        :items (:items drinking-window)
        :totals (:totals drinking-window)} metric
       {:max-items max-items
        :empty-copy "Set tasting windows to track readiness."}]
      [breakdown-card
       {:title "Top varieties"
        :items (:items varieties)
        :totals (:totals varieties)} metric
       {:max-items (if compact? 5 8)
        :progress? false
        :empty-copy "Capture grape varieties to surface favorites."}]
      [inventory-card inventory metric {:compact? compact?}]
      [optimal-window-card app-state optimal-window metric]
      [cumulative-leaving-card app-state cumulative-leaving metric]]]))

(defn collection-stats-modal
  [app-state]
  (let [open? (boolean (get @app-state :show-collection-stats?))
        wines (:wines @app-state)
        visible-wines (filtered-sorted-wines app-state)
        stats-data (summary/collection-stats wines
                                             {:visible-wines visible-wines})
        metric (or (:stats-metric @app-state) :wines)]
    [modal
     {:open open?
      :onClose #(swap! app-state dissoc :show-collection-stats?)
      :closeAfterTransition true}
     [backdrop {:sx {:color "white"} :open open?}
      [box
       {:sx {:position "absolute"
             :top "50%"
             :left "50%"
             :transform "translate(-50%, -50%)"
             :width "82vw"
             :maxWidth "900px"
             :bgcolor "container.main"
             :borderRadius 2
             :boxShadow 24
             :p 4
             :outline "none"
             :maxHeight "80vh"
             :overflow "auto"}}
       [box
        {:sx {:display "flex"
              :justifyContent "space-between"
              :alignItems "center"
              :mb 3}} [typography {:variant "h5"} "Collection Overview"]
        [box {:sx {:display "flex" :alignItems "center" :gap 1.5}}
         [stats-metric-toggle app-state]
         [icon-button
          {:onClick #(swap! app-state dissoc :show-collection-stats?)
           :sx {:minWidth "auto" :p 1 :color "text.secondary"}} [close]]]]
       [stats-content app-state stats-data metric {:compact? false}]]]]))

(defn wine-list
  [app-state]
  (r/create-class
   {:component-did-mount (fn []
                           (when-let [pos (:saved-list-scroll-pos @app-state)]
                             (.scrollTo js/window 0 pos)
                             (swap! app-state dissoc :saved-list-scroll-pos)))
    :reagent-render
    (fn [app-state]
      (let [state @app-state
            wines (:wines state)]
        [box {:sx {:width "100%" :mt 3}}
         (cond (:loading? state)
               [box {:display "flex" :justifyContent "center" :p 4}
                [circular-progress]]
               (empty? wines) [paper
                               {:elevation 2 :sx {:p 3 :textAlign "center"}}
                               [typography {:variant "h6"}
                                "No wines yet. Add your first wine above!"]]
               :else
               (let [selected-ids (or (:selected-wine-ids state) #{})
                     selected-wines
                     (when (seq selected-ids)
                       (vec (filter #(contains? selected-ids (:id %)) wines)))
                     show-selected? (and (:show-selected-wines? state)
                                         (seq selected-wines))
                     visible-wines (if show-selected?
                                     selected-wines
                                     (filtered-sorted-wines app-state))
                     show-out-of-stock? (:show-out-of-stock? state)
                     base-wines (if show-out-of-stock?
                                  wines
                                  (filter #(pos? (or (:quantity %) 0)) wines))
                     visible-count (count visible-wines)
                     total-count (if show-selected?
                                   (count selected-wines)
                                   (count base-wines))
                     selection-count (count (or selected-ids #{}))]
                 [:<> [collection-stats-modal app-state]
                  (if (:selected-wine-id state)
                    [:div]
                    [:<>
                     [filter-bar app-state
                      {:visible visible-count
                       :total total-count
                       :selected-count selection-count
                       :selected-view? show-selected?}]
                     [wine-card-grid app-state visible-wines]])]))]))}))
