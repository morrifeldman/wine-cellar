(ns wine-cellar.views.main
  (:require
    [wine-cellar.api :as api]
    [wine-cellar.views.wines.form :refer [wine-form]]
    [wine-cellar.views.components :refer [toggle-button]]
    [wine-cellar.views.wines.list :refer [wine-list]]
    [wine-cellar.views.wines.detail :refer [wine-details-section]]
    [wine-cellar.views.grape-varieties.list :refer [grape-varieties-page]]
    [wine-cellar.views.classifications.list :refer [classifications-page]]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.paper :refer [paper]]
    [reagent-mui.material.button :refer [button]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.menu :refer [menu]]
    [reagent-mui.material.menu-item :refer [menu-item]]
    [reagent-mui.material.divider :refer [divider]]
    [reagent.core :as r]
    [wine-cellar.views.components.debug :refer [debug-sidebar]]
    [wine-cellar.views.components.wine-chat :refer [wine-chat]]
    [wine-cellar.portal-debug :as pd]
    [wine-cellar.version :as version]
    [wine-cellar.utils.filters]))

(defn- normalize-provider
  [value]
  (cond
    (keyword? value) value
    (string? value) (keyword value)
    (nil? value) :anthropic
    :else :anthropic))

(defn logout
  []
  [button {:variant "outlined" :color "secondary" :onClick #(api/logout)}
   "Logout"])

(defn admin-menu
  [app-state]
  (let [anchor-el (r/atom nil)]
    (fn []
      [box
       [button
        {:variant "outlined"
         :color "primary"
         :on-click #(do (reset! anchor-el (.-currentTarget %))
                        (version/fetch-version!))} "Admin"]
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
        (let [provider (normalize-provider (get-in @app-state [:chat :provider]))]
          [menu-item
           {:on-click (fn []
                        (swap! app-state
                               update-in
                               [:chat :provider]
                               (fn [current]
                                 (let [current* (normalize-provider current)]
                                   (if (= current* :anthropic)
                                     :openai
                                     :anthropic)))))
            :sx {:display "flex"
                 :justify-content "space-between"
                 :gap 1}}
           [:span "AI Provider"]
           [:span {:style {:fontSize "0.85rem"
                           :fontWeight 600}}
            (case provider
              :openai "ChatGPT"
              :anthropic "Claude"
              (name provider))]])
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
  [box {:sx {:p 3 :maxWidth "1200px" :mx "auto"}}
   (when-let [error (:error @app-state)]
     [paper
      {:elevation 3 :sx {:p 2 :mb 3 :bgcolor "error.light" :color "error.dark"}}
      [typography {:variant "body1"} error]])
   (when (:success @app-state)
     [paper
      {:elevation 3
       :sx {:p 2 :mb 3 :bgcolor "success.light" :color "success.dark"}}
      [typography {:variant "body1"} (:success @app-state)]])
   (when (:regenerating-drinking-windows? @app-state)
     [paper
      {:elevation 3 :sx {:p 2 :mb 3 :bgcolor "info.light" :color "info.dark"}}
      (if-let [progress (:job-progress @app-state)]
        [box
         [typography {:variant "body1"}
          (str "🍷 Regenerating drinking windows... "
               (:progress progress)
               "/"
               (:total progress)
               " wines processed")]
         [box {:sx {:width "100%" :mt 1}}
          [box
           {:sx {:width (str (* 100 (/ (:progress progress) (:total progress)))
                             "%")
                 :height 8
                 :bgcolor "info.main"
                 :borderRadius 1}}]]]
        [typography {:variant "body1"}
         "🍷 Starting drinking window regeneration..."])])
   (cond (= (:view @app-state) :grape-varieties)
         [:div [grape-varieties-page app-state]
          [button
           {:variant "outlined"
            :color "primary"
            :on-click #(swap! app-state dissoc :view)
            :sx {:mt 2}} "Back to Wine List"]]
         (= (:view @app-state) :classifications)
         [:div [classifications-page app-state]
          [button
           {:variant "outlined"
            :color "primary"
            :on-click #(swap! app-state dissoc :view)
            :sx {:mt 2}} "Back to Wine List"]]
         ;; Wine views
         (:selected-wine-id @app-state) [:div [wine-details-section app-state]]
         (:show-wine-form? @app-state) [:div [top-controls app-state]
                                        [wine-form app-state]]
         :else [:div [top-controls app-state] [wine-list app-state]])
   (when (:show-debug-controls? @app-state) [debug-sidebar app-state])
   [wine-chat app-state]])
