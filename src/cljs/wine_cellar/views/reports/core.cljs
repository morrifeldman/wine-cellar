(ns wine-cellar.views.reports.core
  (:require [reagent.core :as r]
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
            [wine-cellar.api :as api]))

(defn- highlight-wine-card
  [wine on-view-wine]
  (when wine
    [card {:sx {:display "flex" :flexDirection "column" :height "100%"}}
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
       (str (:region wine) ", " (:country wine))]
      [box {:sx {:mt 2}}
       [button
        {:variant "outlined" :size "small" :on-click #(on-view-wine (:id wine))}
        "View Details"]]]]))

(defn- handle-selection
  [app-state ids]
  (if (seq ids)
    (swap! app-state assoc
      :selected-wine-ids (set ids)
      :show-selected-wines? true
      :show-report? false)
    (js/console.warn "No IDs to select")))

(defn report-modal
  [app-state]
  (let [report (r/cursor app-state [:report])
        loading? (r/cursor app-state [:loading-report?])]
    (fn []
      (when (:show-report? @app-state)
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
               :p 2}}
         [paper
          {:elevation 24
           :sx {:width "100%"
                :maxWidth "900px"
                :maxHeight "90vh"
                :overflow "auto"
                :p 4
                :position "relative"}}
          ;; Close Button
          [icon-button
           {:sx {:position "absolute" :top 16 :right 16 :color "text.secondary"}
            :on-click #(swap! app-state dissoc :show-report? :report)}
           [:span "✕"]]
          ;; Regenerate Button
          [button
           {:variant "text"
            :size "small"
            :color "secondary"
            :sx {:position "absolute" :top 16 :right 60}
            :on-click #(let [provider (get-in @app-state [:ai :provider])]
                         (api/fetch-latest-report app-state
                                                  {:force? true
                                                   :provider provider}))}
           "Regenerate"]
          [typography
           {:variant "h3"
            :gutterBottom true
            :color "primary.main"
            :sx {:fontWeight 600}} "🍷 Cellar Insights"]
          (cond @loading?
                [box
                 {:sx {:display "flex"
                       :flexDirection "column"
                       :alignItems "center"
                       :p 8
                       :gap 2}} [circular-progress {:color "secondary"}]
                 [typography {:variant "body1" :color "text.secondary"}
                  "Consulting the sommelier..."]]
                @report
                (let [raw-data (:summary_data @report)
                      data (cond (map? raw-data) (walk/keywordize-keys raw-data)
                                 (nil? raw-data) {}
                                 :else (js->clj raw-data :keywordize-keys true))
                      commentary (:ai_commentary @report)
                      highlight (:highlight-wine data)
                      drink-now-ids (:drink-now-ids data)
                      expiring-ids (:expiring-ids data)
                      recent-ids (:recent-activity-ids data)]
                  [:div
                   [typography
                    {:variant "subtitle1"
                     :color "text.secondary"
                     :gutterBottom true}
                    (str "Report generated on " (:report_date @report))]
                   [divider {:sx {:my 2}}]
                   ;; AI Commentary
                   [paper
                    {:elevation 0
                     :sx {:bgcolor "rgba(0,0,0,0.2)"
                          :p 3
                          :mb 4
                          :borderRadius 2
                          :border "1px solid rgba(255,255,255,0.1)"}}
                    [typography
                     {:variant "body1"
                      :sx {:whiteSpace "pre-wrap"
                           :fontStyle "italic"
                           :lineHeight 1.6}} commentary]]
                   ;; Stats Grid
                   [grid {:container true :spacing 3 :sx {:mb 4}}
                    [grid {:item true :xs 12 :md 4}
                     [paper
                      {:sx {:p 2
                            :textAlign "center"
                            :height "100%"
                            :cursor "pointer"
                            :transition "transform 0.2s, box-shadow 0.2s"
                            "&:hover" {:transform "translateY(-4px)"
                                       :boxShadow 4
                                       :bgcolor "rgba(255,255,255,0.05)"}}
                       :on-click #(handle-selection app-state drink-now-ids)}
                      [typography {:variant "h3" :color "primary"}
                       (:drink-now-count data)]
                      [typography {:variant "subtitle2"} "Drink Now"]]]
                    [grid {:item true :xs 12 :md 4}
                     [paper
                      {:sx {:p 2
                            :textAlign "center"
                            :height "100%"
                            :cursor "pointer"
                            :transition "transform 0.2s, box-shadow 0.2s"
                            "&:hover" {:transform "translateY(-4px)"
                                       :boxShadow 4
                                       :bgcolor "rgba(255,255,255,0.05)"}}
                       :on-click #(handle-selection app-state expiring-ids)}
                      [typography {:variant "h3" :color "secondary"}
                       (:expiring-count data)]
                      [typography {:variant "subtitle2"} "Expiring Soon"]]]
                    [grid {:item true :xs 12 :md 4}
                     [paper
                      {:sx {:p 2
                            :textAlign "center"
                            :height "100%"
                            :cursor "pointer"
                            :transition "transform 0.2s, box-shadow 0.2s"
                            "&:hover" {:transform "translateY(-4px)"
                                       :boxShadow 4
                                       :bgcolor "rgba(255,255,255,0.05)"}}
                       :on-click #(handle-selection app-state recent-ids)}
                      [typography {:variant "h3" :color "success.main"}
                       (:recent-activity-count data)]
                      [typography {:variant "subtitle2"} "Recent Activity"]]]]
                   ;; Highlight Wine
                   (when highlight
                     [box {:sx {:mb 4}}
                      [typography {:variant "h5" :gutterBottom true}
                       "Spotlight Wine"]
                      [highlight-wine-card highlight
                       (fn [id]
                         (swap! app-state assoc
                           :selected-wine-id id
                           :show-report? false)
                         (api/fetch-wine-details app-state id))]])])
                :else [typography {:color "error"}
                       "Failed to load report."])]]))))
