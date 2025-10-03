(ns wine-cellar.views.main
  (:require
    [wine-cellar.api :as api]
    [wine-cellar.common :as common]
    [wine-cellar.views.wines.form :refer [wine-form]]
    [wine-cellar.views.components :refer [toggle-button]]
    [wine-cellar.views.components.ai-provider-toggle :as provider-toggle]
    [wine-cellar.views.wines.list :refer [wine-list]]
    [wine-cellar.views.wines.detail :refer [wine-details-section]]
    [wine-cellar.views.grape-varieties.list :refer [grape-varieties-page]]
    [wine-cellar.views.classifications.list :refer [classifications-page]]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.paper :refer [paper]]
    [reagent-mui.material.button :refer [button]]
    [reagent-mui.material.icon-button :refer [icon-button]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.menu :refer [menu]]
    [reagent-mui.material.menu-item :refer [menu-item]]
    [reagent-mui.material.divider :refer [divider]]
    [reagent.core :as r]
    [reagent-mui.icons.close :refer [close]]
    [wine-cellar.views.components.debug :refer [debug-sidebar]]
    [wine-cellar.views.components.wine-chat :refer [wine-chat]]
    [wine-cellar.portal-debug :as pd]
    [wine-cellar.version :as version]
    [wine-cellar.utils.filters]))

(defn logout
  []
  [button {:variant "outlined" :color "secondary" :onClick #(api/logout)}
   "Logout"])

(defn- job-progress-card
  [state {:keys [flag job-type running-text starting-text]}]
  (let [running? (get state flag)
        progress (:job-progress state)
        relevant-progress (when (= (:job-type progress) job-type) progress)]
    (when running?
      [paper
       {:elevation 3
        :sx {:p 2 :mb 3 :bgcolor "info.light" :color "info.dark"}}
       (if relevant-progress
         (let [processed (or (:progress relevant-progress) 0)
               total (max 0 (or (:total relevant-progress) 0))
               raw (if (pos? total) (* 100 (/ processed total)) 0)
               percent (-> raw (js/Math.max 0) (js/Math.min 100))
               status (:status relevant-progress)
               retry-attempt (:retry-attempt relevant-progress)
               retry-max (:retry-max relevant-progress)
               retry-delay-ms (:retry-delay relevant-progress)
               retry-seconds (when retry-delay-ms (js/Math.round (/ retry-delay-ms 1000)))]
           [box
            [typography {:variant "body1"}
             (str running-text " " processed "/" total " wines processed")]
            (when (= status "retrying")
              [typography {:variant "body2"
                           :sx {:mt 0.5 :fontStyle "italic"}}
               (str "Retrying status check"
                    (when (and retry-attempt retry-max)
                      (str " (attempt " retry-attempt "/" retry-max ")"))
                    (when retry-seconds
                      (str " in " retry-seconds "s")))])
            [box {:sx {:width "100%" :mt 1}}
             [box {:sx {:width (str percent "%")
                        :height 8
                        :bgcolor "info.main"
                        :borderRadius 1}}]]])
         [typography {:variant "body1"} starting-text])])))

(defn admin-menu
  [app-state]
  (let [anchor-el (r/atom nil)]
    (fn []
      [box
       [button
        {:variant "outlined"
         :color "primary"
         :on-click #(do (reset! anchor-el (.-currentTarget %))
                        (version/fetch-version!)
                        (api/fetch-model-info app-state))} "Admin"]
       [menu
        {:anchor-el @anchor-el
         :open (boolean @anchor-el)
         :on-close #(reset! anchor-el nil)}
        ;; Version info
        [menu-item
         {:disabled true
          :sx {:fontSize "0.875rem"
               :color "text.secondary"
               :fontFamily "monospace"}} (version/version-string)] [divider]
        (let [provider (get-in @app-state [:ai :provider])
              models (get-in @app-state [:ai :models])
              model (get models provider)]
          [menu-item
           {:on-click #(provider-toggle/toggle-provider! app-state)
            :sx {:display "flex"
                 :flex-direction "column"
                 :align-items "flex-start"
                 :gap 0.5}}
           [:div {:style {:display "flex"
                          :justify-content "space-between"
                          :width "100%"}}
            [:span "AI Provider"]
            [:span {:style {:fontSize "0.85rem"
                            :fontWeight 600}}
             (common/provider-label provider)]]
           (when model
             [:div {:style {:align-self "flex-end"
                            :fontSize "0.75rem"
                            :color "rgba(255, 255, 255, 0.7)"
                            :font-family "monospace"}}
              model])])
        [divider]
        [menu-item
         {:on-click (fn []
                      (reset! anchor-el nil)
                      (api/fetch-grape-varieties app-state)
                      (swap! app-state assoc :view :grape-varieties))}
         "Grape Varieties"]
        [menu-item
         {:on-click (fn []
                      (reset! anchor-el nil)
                      (api/fetch-classifications app-state)
                      (swap! app-state assoc :view :classifications))}
         "Classifications"]
        [menu-item
         {:on-click (fn []
                      (reset! anchor-el nil)
                      (swap! app-state update :show-debug-controls? not))}
         (if (:show-debug-controls? @app-state)
           "Hide Debug Controls"
           "Show Debug Controls")]
        [menu-item
         {:on-click
          (fn [] (reset! anchor-el nil) (pd/toggle-debugging! app-state))}
         (if (pd/debugging?) "Stop Portal Debugging" "Start Portal Debugging")]
        (when (pd/debugging?)
          [menu-item
           {:on-click (fn [] (reset! anchor-el nil) (pd/reconnect-if-needed))}
           "Open Portal"])
        (when (pd/debugging?)
          [menu-item {:on-click (fn [] (reset! anchor-el nil) (tap> app-state))}
           "Tap> app-state"])
        (when (pd/debugging?)
          [menu-item
           {:on-click
            (fn [] (reset! anchor-el nil) (pd/watch-app-state app-state))}
           "Watch App State"])
        (when (pd/debugging?)
          [menu-item
           {:on-click
            (fn [] (reset! anchor-el nil) (pd/unwatch-app-state app-state))}
           "Unwatch App State"])
        [menu-item
         {:on-click
          (fn []
            (reset! anchor-el nil)
            (swap! app-state update :show-verification-checkboxes? not))}
         (if (:show-verification-checkboxes? @app-state)
           "Hide Verification Checkboxes"
           "Show Verification Checkboxes")]
        [menu-item
         {:on-click
          (fn []
            (reset! anchor-el nil)
            (when
              (js/confirm
               "Mark all wines as unverified? This will require you to verify them individually.")
              (api/mark-all-wines-unverified app-state)))}
         "Mark All Wines Unverified"]
        [menu-item
         {:on-click
          (fn []
            (reset! anchor-el nil)
            (let [filtered-count
                  (count (wine-cellar.utils.filters/filtered-sorted-wines
                          app-state))]
              (when
                (js/confirm
                 (str
                  "Regenerate drinking windows for "
                  filtered-count
                  " currently visible wines? This may take several minutes and will use AI API credits."))
                (api/regenerate-filtered-drinking-windows app-state))))
          :disabled (:regenerating-drinking-windows? @app-state)}
         (if (:regenerating-drinking-windows? @app-state)
           "Regenerating Drinking Windows..."
           "Regenerate Filtered Drinking Windows")]
        [menu-item
         {:on-click
          (fn []
            (reset! anchor-el nil)
            (let [filtered-count
                  (count (wine-cellar.utils.filters/filtered-sorted-wines
                          app-state))]
              (when
                (js/confirm
                 (str "Regenerate wine summaries for "
                      filtered-count
                      " currently visible wines? This may take several minutes and will use AI API credits."))
                (api/regenerate-filtered-wine-summaries app-state))))
          :disabled (:regenerating-wine-summaries? @app-state)}
         (if (:regenerating-wine-summaries? @app-state)
           "Regenerating Wine Summaries..."
           "Regenerate Filtered Wine Summaries")]
        [menu-item
         {:on-click
          (fn []
            (reset! anchor-el nil)
            (when
              (js/confirm
               "⚠️ DANGER: This will DELETE ALL DATA and reset the database schema!\n\nAre you absolutely sure you want to continue?")
              (api/reset-database app-state)))
          :sx {:color "error.main"}} "🔥 Reset Database"]]])))

(defn new-wine-or-list
  [app-state]
  [toggle-button
   {:app-state app-state
    :path [:show-wine-form?]
    :show-text "Add New Wine"
    :hide-text "Show Wine List"}])

(defn top-controls
  [app-state]
  [box
   {:sx {:display "flex"
         :justifyContent "space-between"
         :alignItems "center"
         :mb 3
         :pb 2
         :borderBottom "1px solid rgba(0,0,0,0.08)"}}
   ;; Left side: Add New Wine / Show Wine List
   [new-wine-or-list app-state]
   ;; Right side: Stats + Admin + Logout
   [box {:sx {:display "flex" :gap 1 :alignItems "center"}}
    [button
     {:variant "outlined"
      :color "primary"
      :onClick #(swap! app-state assoc :show-collection-stats? true)} "Stats"]
    [admin-menu app-state] [logout]]])

(defn main-app
  [app-state]
  (let [state @app-state]
    [box {:sx {:p 3 :maxWidth "1200px" :mx "auto"}}
     (when-let [error (:error state)]
       [paper
        {:elevation 3
         :sx {:p 2 :mb 3 :bgcolor "error.light" :color "error.dark"
              :position "relative"}}
        [box {:sx {:display "flex" :alignItems "flex-start"}}
         [typography {:variant "body1" :sx {:flex 1 :pr 2}} error]
         [icon-button {:aria-label "Dismiss error"
                       :size "small"
                       :onClick #(swap! app-state dissoc :error)
                       :sx {:color "error.dark"}}
          [close {:fontSize "small"}]]]])
     (when-let [success (:success state)]
       [paper
        {:elevation 3
         :sx {:p 2 :mb 3 :bgcolor "success.light" :color "success.dark"
              :position "relative"}}
        [box {:sx {:display "flex" :alignItems "flex-start"}}
         [typography {:variant "body1" :sx {:flex 1 :pr 2}} success]
         [icon-button {:aria-label "Dismiss success"
                       :size "small"
                       :onClick #(swap! app-state dissoc :success)
                       :sx {:color "success.dark"}}
          [close {:fontSize "small"}]]]])
     (when-let [card (job-progress-card state
                                        {:flag :regenerating-drinking-windows?
                                         :job-type :drinking-window
                                         :running-text "🍷 Regenerating drinking windows..."
                                         :starting-text "🍷 Starting drinking window regeneration..."})]
       card)
     (when-let [card (job-progress-card state
                                        {:flag :regenerating-wine-summaries?
                                         :job-type :wine-summary
                                         :running-text "📝 Regenerating wine summaries..."
                                         :starting-text "📝 Starting wine summary regeneration..."})]
       card)
     (cond (= (:view state) :grape-varieties)
           [:div [grape-varieties-page app-state]
            [button
             {:variant "outlined"
              :color "primary"
              :on-click #(swap! app-state dissoc :view)
              :sx {:mt 2}} "Back to Wine List"]]
           (= (:view state) :classifications)
           [:div [classifications-page app-state]
            [button
             {:variant "outlined"
              :color "primary"
              :on-click #(swap! app-state dissoc :view)
              :sx {:mt 2}} "Back to Wine List"]]
           ;; Wine views
           (:selected-wine-id state) [:div [wine-details-section app-state]]
           (:show-wine-form? state) [:div [top-controls app-state]
                                     [wine-form app-state]]
           :else [:div [top-controls app-state] [wine-list app-state]])
     (when (:show-debug-controls? state) [debug-sidebar app-state])
     [wine-chat app-state]]))
