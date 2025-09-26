(ns wine-cellar.views.wines.list
  (:require [wine-cellar.utils.stats :as stats]
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
            [reagent-mui.material.linear-progress :refer [linear-progress]]
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

(defn- wines-label
  [count]
  (str count " " (if (= count 1) "wine" "wines")))

(defn- stats-summary-card
  [{:keys [title value subtitle color]}]
  [grid {:item true :xs 12 :sm 6 :md 3}
   [paper {:elevation 1
           :sx {:p 2
                :height "100%"
                :display "flex"
                :flexDirection "column"
                :gap 0.75}}
    [typography {:variant "overline"
                 :color "text.secondary"
                 :sx {:letterSpacing "0.08em"}} title]
    [typography {:variant "h4" :color (or color "primary") :sx {:fontWeight 600}}
     value]
    (when subtitle
      [typography {:variant "body2" :color "text.secondary"} subtitle])]])

(defn- stats-summary-grid
  [{:keys [total-wines visible-wines total-bottles avg-rating total-value]}]
  (let [rating-color (if avg-rating (get-rating-color avg-rating) "text.secondary")
        rating-text (if avg-rating (str avg-rating "/100") "—")]
    [grid {:container true :spacing 3}
     [stats-summary-card
      {:title "Wines"
       :value (str visible-wines "/" total-wines)
       :subtitle "Visible / total"}]
     [stats-summary-card
      {:title "Bottles"
       :value (format-number total-bottles)
       :subtitle "In-stock bottles"}]
     [stats-summary-card
      {:title "Avg. Rating"
       :value rating-text
       :subtitle "Internal tastings"
       :color rating-color}]
     [stats-summary-card
      {:title "Collection Value"
       :value (format-currency total-value)
       :subtitle "Estimated at purchase price"}]]))

(defn- breakdown-card
  [{:keys [title items total]}
   {:keys [max-items empty-copy progress? detail-fn]
    :or {max-items 5 empty-copy "No data yet"}}]
  (let [progress? (if (some? progress?) progress? true)
        default-detail (fn [{:keys [count]} pct]
                         (if pct
                           (str (wines-label count) " (" pct "%)")
                           (wines-label count)))
        detail-fn (or detail-fn default-detail)
        display-items (take max-items items)]
    [grid {:item true :xs 12 :md 6}
     [paper {:elevation 1
             :sx {:p 2.5 :height "100%" :display "flex" :flexDirection "column" :gap 1.5}}
      [typography {:variant "subtitle1" :sx {:fontWeight 600}} title]
      (if (seq display-items)
        (for [[idx {:keys [label count] :as item}] (map-indexed vector display-items)]
          (let [percent (when (and progress? (pos? total))
                          (* 100 (/ count total)))
                rounded (when percent (js/Math.round percent))
                detail (detail-fn item rounded)]
            ^{:key (str title "-" idx "-" label)}
            [box {:sx {:display "flex" :flexDirection "column" :gap 0.5}}
             [box {:sx {:display "flex" :justifyContent "space-between" :alignItems "center"}}
              [typography {:variant "body2" :sx {:fontWeight 500}} label]
              [typography {:variant "body2" :color "text.secondary"} detail]]
             (when percent
               [linear-progress
                {:variant "determinate"
                 :value (min 100 percent)
                 :sx {:height 6 :borderRadius 999
                      :backgroundColor "rgba(255,255,255,0.08)"}}])]))
        [typography {:variant "body2" :color "text.secondary"} empty-copy])]]))

(defn- inventory-card
  [inventory {:keys [compact?]}]
  (let [ordered (vec inventory)
        rows (if compact?
               (take-last 4 ordered)
               (take-last 7 ordered))]
    [grid {:item true :xs 12 :md 6}
     [paper {:elevation 1
             :sx {:p 2.5 :height "100%" :display "flex" :flexDirection "column" :gap 1.25}}
      [typography {:variant "subtitle1" :sx {:fontWeight 600}} "Stock by purchase year"]
      (if (seq rows)
        (into
         [:<>]
         (map (fn [{:keys [year remaining purchased]}]
                ^{:key (str "inventory-" year)}
                [box {:sx {:display "flex" :justifyContent "space-between"
                           :alignItems "baseline"}}
                 [typography {:variant "body2" :sx {:fontWeight 600}} year]
                 [box {:sx {:display "flex" :gap 1.5 :color "text.secondary"}}
                  [typography {:variant "body2"}
                   (str (format-number remaining) " remaining")]
                  [typography {:variant "body2"}
                   (str (format-number purchased) " original")]]])
              rows))
        [typography {:variant "body2" :color "text.secondary"}
         "Add purchase dates to see inventory trends."])]]))

(defn- stats-content
  [stats {:keys [compact?]}]
  (let [{:keys [totals style price drinking-window country varieties inventory]} stats
        max-items (if compact? 4 7)]
    [:<>
     [stats-summary-grid totals]
     [grid {:container true :spacing 3 :sx {:mt 0.5}}
      [breakdown-card {:title "By style"
                       :items (:items style)
                       :total (:total style)}
       {:max-items max-items
        :empty-copy "Add wine styles to see this breakdown."}]
      [breakdown-card {:title "By country"
                       :items (:items country)
                       :total (:total country)}
       {:max-items max-items
        :empty-copy "Record country details to populate this view."}]
      [breakdown-card {:title "Price bands"
                       :items (:items price)
                       :total (:total price)}
       {:max-items max-items
        :empty-copy "Add prices to compare value bands."}]
      [breakdown-card {:title "Drinking window"
                       :items (:items drinking-window)
                       :total (:total drinking-window)}
       {:max-items max-items
        :empty-copy "Set tasting windows to track readiness."}]
      [breakdown-card {:title "Top varieties"
                       :items (:items varieties)
                       :total (:total varieties)}
       {:max-items (if compact? 5 8)
        :progress? false
        :detail-fn (fn [{:keys [count]} _] (wines-label count))
        :empty-copy "Capture grape varieties to surface favorites."}]
      [inventory-card inventory {:compact? compact?}]]]))

(defn wine-stats
  [app-state]
  (let [wines (:wines @app-state)
        visible-wines (filtered-sorted-wines app-state)
        stats-data (stats/collection-stats wines {:visible-wines visible-wines})]
    [paper {:elevation 2 :sx {:p 3 :mb 3 :borderRadius 2}}
     [box {:sx {:display "flex" :justifyContent "space-between" :alignItems "center" :mb 2}}
      [typography {:variant "h6" :component "h3"} "Collection Overview"]
      [icon-button
       {:onClick #(swap! app-state update :show-stats? not)
        :size "small"}
       (if (:show-stats? @app-state)
         [expand-less {:sx {:color "text.secondary"}}]
         [expand-more {:sx {:color "text.secondary"}}])]]
     [collapse {:in (:show-stats? @app-state) :timeout "auto"}
      [box {:sx {:pt 1}}
       [stats-content stats-data {:compact? true}]]]]))

(defn collection-stats-modal
  [app-state]
  (let [open? (boolean (get @app-state :show-collection-stats?))
        wines (:wines @app-state)
        visible-wines (filtered-sorted-wines app-state)
        stats-data (stats/collection-stats wines {:visible-wines visible-wines})]
    [modal {:open open?
            :onClose #(swap! app-state dissoc :show-collection-stats?)
            :closeAfterTransition true}
     [backdrop {:sx {:color "white"}
                :open open?}
      [box {:sx {:position "absolute"
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
       [box {:sx {:display "flex" :justifyContent "space-between" :alignItems "center" :mb 3}}
        [typography {:variant "h5"} "Collection Overview"]
        [icon-button {:onClick #(swap! app-state dissoc :show-collection-stats?)
                      :sx {:minWidth "auto" :p 1 :color "text.secondary"}}
         [close]]]
       [stats-content stats-data {:compact? false}]]]]))

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
