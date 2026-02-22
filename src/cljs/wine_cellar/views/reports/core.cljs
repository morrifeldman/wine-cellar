(ns wine-cellar.views.reports.core
  (:require [reagent.core :as r]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.divider :refer [divider]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.card :refer [card]]
            [reagent-mui.material.card-content :refer [card-content]]
            [reagent-mui.material.card-media :refer [card-media]]
            [reagent-mui.material.grid :refer [grid]]
            ["react-markdown" :default ReactMarkdown]
            [wine-cellar.api :as api]))

(defn- open-wine
  [app-state id]
  (swap! app-state #(-> %
                        (assoc :return-to-report? true :selected-wine-id id)
                        (dissoc :show-report?)))
  (api/load-wine-detail-page app-state id))

(defn- highlight-wine-card
  [wine on-view-wine]
  (when wine
    [card
     {:sx {:display "flex"
           :flexDirection "column"
           :height "100%"
           :cursor "pointer"
           :transition "transform 0.2s, box-shadow 0.2s"
           "&:hover" {:transform "translateY(-2px)" :boxShadow 4}}
      :on-click #(on-view-wine (:id wine))}
     (when (:label_thumbnail wine)
       [card-media
        {:component "img"
         :height "200"
         :image (:label_thumbnail wine)
         :alt (:name wine)}])
     [card-content
      [typography {:variant "h6" :component "div"}
       (or (:name wine) (:producer wine))]
      [typography {:variant "subtitle1" :color "text.secondary"}
       (:vintage wine)]
      [typography {:variant "body2" :color "text.secondary" :sx {:mt 1}}
       (str (:region wine) ", " (:country wine))]]]))

(defn- handle-selection
  [app-state ids]
  (if (seq ids)
    (swap! app-state assoc
      :selected-wine-ids (set ids)
      :show-selected-wines? true
      :show-report? false
      :return-to-report? true)
    (js/console.warn "No IDs to select")))

(defn- markdown-components
  [app-state]
  {:h1 (fn [props]
         (r/as-element [typography
                        {:variant "h4"
                         :gutterBottom true
                         :color "secondary.main"
                         :sx {:mt 2 :mb 1}} (.. props -children)]))
   :h2 (fn [props]
         (r/as-element [typography
                        {:variant "h5"
                         :gutterBottom true
                         :color "secondary.main"
                         :sx {:mt 2 :mb 1}} (.. props -children)]))
   :h3 (fn [props]
         (r/as-element [typography
                        {:variant "h6" :gutterBottom true :sx {:mt 2 :mb 1}}
                        (.. props -children)]))
   :p (fn [props]
        (r/as-element [typography
                       {:variant "body1" :paragraph true :sx {:lineHeight 1.7}}
                       (.. props -children)]))
   :li (fn [props]
         (r/as-element [typography
                        {:component "li" :variant "body1" :sx {:ml 2}}
                        (.. props -children)]))
   :strong (fn [props]
             (r/as-element [typography
                            {:component "span"
                             :sx {:fontWeight "bold" :color "secondary.light"}}
                            (.. props -children)]))
   :em (fn [props]
         (r/as-element [typography {:component "span" :sx {:fontStyle "italic"}}
                        (.. props -children)]))
   :a (fn [props]
        (let [href (.. props -href)
              children (.. props -children)]
          (if (and href (string/starts-with? href "wine:"))
            (let [wine-id (js/parseInt (subs href 5))]
              (r/as-element [typography
                             {:component "span"
                              :variant "body1"
                              :sx {:color "primary.light"
                                   :textDecoration "underline"
                                   :cursor "pointer"
                                   :fontWeight 600
                                   "&:hover" {:color "primary.main"}}
                              :on-click (fn [e]
                                          (.preventDefault e)
                                          (.stopPropagation e)
                                          (open-wine app-state wine-id))}
                             children]))
            (r/as-element [:a {:href href} children]))))})

(defn- stat-card
  [color count label on-click]
  [paper
   {:sx {:p 2
         :textAlign "center"
         :height "100%"
         :cursor "pointer"
         :transition "transform 0.2s, box-shadow 0.2s"
         "&:hover" {:transform "translateY(-4px)"
                    :boxShadow 4
                    :bgcolor "rgba(255,255,255,0.05)"}}
    :on-click on-click} [typography {:variant "h3" :color color} count]
   [typography {:variant "subtitle2"} label]])

(defn- stat-grid
  [app-state data]
  [grid {:container true :spacing 2 :sx {:mb 4}}
   [grid {:item true :xs 6 :sm 4}
    [stat-card "info.main" (:recently-added-count data) "Recently Added"
     #(handle-selection app-state (:recently-added-ids data))]]
   [grid {:item true :xs 6 :sm 4}
    [stat-card "primary" (:drink-now-count data) "Drink Now"
     #(handle-selection app-state (:drink-now-ids data))]]
   [grid {:item true :xs 6 :sm 4}
    [stat-card "secondary" (:expiring-count data) "Expiring Soon"
     #(handle-selection app-state (:expiring-ids data))]]
   [grid {:item true :xs 6 :sm 4}
    [stat-card "error.main" (:past-prime-count data) "Past Prime"
     #(handle-selection app-state (:past-prime-ids data))]]
   [grid {:item true :xs 12 :sm 8}
    [stat-card "success.main" (:recent-activity-count data) "Recent Activity"
     #(handle-selection app-state (:recent-activity-ids data))]]])

(defn- report-body
  [app-state report]
  (let [raw-data (:summary_data @report)
        data (cond (map? raw-data) (walk/keywordize-keys raw-data)
                   (nil? raw-data) {}
                   :else (js->clj raw-data :keywordize-keys true))
        commentary (:ai_commentary @report)
        highlight (:highlight-wine data)]
    [:div
     [typography
      {:variant "subtitle1" :color "text.secondary" :gutterBottom true}
      (str "Report generated on " (:report_date @report))]
     (when (:ai-model data)
       [typography {:variant "caption" :color "text.secondary"}
        (str "Generated by " (:ai-model data))]) [divider {:sx {:my 2}}]
     [paper
      {:elevation 0
       :sx {:bgcolor "rgba(0,0,0,0.2)"
            :p 3
            :mb 4
            :borderRadius 2
            :border "1px solid rgba(255,255,255,0.1)"}}
      [:> ReactMarkdown
       {:components (markdown-components app-state)
        :urlTransform (fn [url] url)} commentary]] [stat-grid app-state data]
     (when highlight
       [box {:sx {:mb 4}}
        [typography {:variant "h5" :gutterBottom true} "Spotlight Wine"]
        [highlight-wine-card highlight #(open-wine app-state %)]])]))

(defn- report-paper
  [app-state report loading?]
  [paper
   {:elevation 24
    :sx {:width "100%"
         :maxWidth "900px"
         :maxHeight "90vh"
         :overflow "auto"
         :p 4
         :position "relative"}}
   [icon-button
    {:sx {:position "absolute" :top 16 :right 16 :color "text.secondary"}
     :on-click #(swap! app-state dissoc :show-report? :report)} [:span "✕"]]
   [button
    {:variant "text"
     :size "small"
     :color "secondary"
     :sx {:position "absolute" :top 16 :right 60}
     :on-click #(let [provider (get-in @app-state [:ai :provider])]
                  (api/fetch-latest-report app-state
                                           {:force? true :provider provider})
                  (api/fetch-report-list app-state))} "Regenerate"]
   [typography
    {:variant "h3"
     :gutterBottom true
     :color "primary.main"
     :sx {:fontWeight 600}} "🍷 Cellar Insights"]
   (cond @loading? [box
                    {:sx {:display "flex"
                          :flexDirection "column"
                          :alignItems "center"
                          :p 8
                          :gap 2}} [circular-progress {:color "secondary"}]
                    [typography {:variant "body1" :color "text.secondary"}
                     "Consulting the sommelier..."]]
         @report [report-body app-state report]
         :else [typography {:color "error"} "Failed to load report."])])

(defn- nav-arrow
  [direction has? id app-state]
  (let [left? (= direction :left)]
    [icon-button
     {:sx {:position "absolute"
           (if left? :left :right) 8
           :top "50%"
           :transform "translateY(-50%)"
           :opacity (if has? 1 0.25)
           :color "rgba(255,255,255,0.9)"
           :bgcolor "rgba(255,255,255,0.12)"
           :border "1px solid rgba(255,255,255,0.2)"
           "&:hover" {:bgcolor "rgba(255,255,255,0.2)"}}
      :disabled (not has?)
      :on-click #(api/fetch-report-by-id app-state id)}
     [:span {:style {:fontSize "1.5rem" :lineHeight 1}} (if left? "‹" "›")]]))

(defn report-modal
  [app-state]
  (let [report (r/cursor app-state [:report])
        loading? (r/cursor app-state [:loading-report?])
        prev-show (r/atom false)]
    (fn []
      (let [show? (:show-report? @app-state)]
        (when (and show? (not @prev-show))
          (reset! prev-show true)
          (api/fetch-report-list app-state))
        (when-not show? (reset! prev-show false))
        (when show?
          (let [report-list (get-in @app-state [:report-nav :list])
                current-id (:id @report)
                current-idx (when (seq report-list)
                              (first (keep-indexed
                                      (fn [i r] (when (= (:id r) current-id) i))
                                      report-list)))
                has-prev? (and current-idx
                               (< (inc current-idx) (count report-list)))
                has-next? (and current-idx (> current-idx 0))
                prev-id (when has-prev?
                          (:id (nth report-list (inc current-idx))))
                next-id (when has-next?
                          (:id (nth report-list (dec current-idx))))]
            [box
             {:sx {:position "fixed"
                   :top 0
                   :left 0
                   :width "100%"
                   :height "100%"
                   :bgcolor "rgba(0,0,0,0.5)"
                   :zIndex 1300
                   :display "flex"
                   :justifyContent "center"
                   :alignItems "center"
                   :p 2}} [report-paper app-state report loading?]
             [nav-arrow :left has-prev? prev-id app-state]
             [nav-arrow :right has-next? next-id app-state]]))))))
