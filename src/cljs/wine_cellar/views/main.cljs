(ns wine-cellar.views.main
  (:require
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
    [reagent.core :as r]
    [wine-cellar.views.components.portal-debug :refer [debug-button]]
    [wine-cellar.views.components.debug :refer [debug-sidebar]]
    [wine-cellar.api :as api]))

(defn logout
  []
  [button {:variant "outlined" :color "secondary" :onClick #(api/logout)}
   "Logout"])

(defn admin-menu
  [app-state]
  (let [anchor-el (r/atom nil)]
    (fn [] [box {:sx {:mb 2}}
            [button
             {:variant "outlined"
              :color "primary"
              :on-click #(reset! anchor-el (.-currentTarget %))} "Admin"]
            [menu
             {:anchor-el @anchor-el
              :open (boolean @anchor-el)
              :on-close #(reset! anchor-el nil)}
             [menu-item
              {:on-click (fn []
                           (reset! anchor-el nil)
                           (swap! app-state assoc :view :grape-varieties))}
              "Grape Varieties"]
             [menu-item
              {:on-click (fn []
                           (reset! anchor-el nil)
                           (swap! app-state assoc :view :classifications))}
              "Classifications"]
             [menu-item
              {:on-click (fn []
                           (swap! app-state update :show-debug-controls? not))}
              (if (:show-debug-controls? @app-state)
                "Hide Debug Controls"
                "Show Debug Controls")]] [logout]])))

(defn new-wine-or-list
  [app-state]
  [toggle-button
   {:app-state app-state
    :path [:show-wine-form?]
    :show-text "Add New Wine"
    :hide-text "Show Wine List"}])

(defn main-app
  [app-state]
  [box {:sx {:p 3 :maxWidth "1200px" :mx "auto"}}
   [box
    {:sx {:textAlign "center"
          :mb 4
          :pb 3
          :borderBottom "1px solid rgba(0,0,0,0.08)"}}
    [typography
     {:variant "h2" :component "h1" :sx {:fontWeight 300 :color "primary.main"}}
     "Wine Cellar"]]
   (when-let [error (:error @app-state)]
     [paper
      {:elevation 3 :sx {:p 2 :mb 3 :bgcolor "error.light" :color "error.dark"}}
      [typography {:variant "body1"} error]])
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
         (:show-wine-form? @app-state) [:div [wine-form app-state]
                                        [new-wine-or-list app-state]]
         :else [:div [new-wine-or-list app-state] [wine-list app-state]])
   [admin-menu app-state]
   (when (:show-debug-controls? @app-state)
     [:div [debug-button] [debug-sidebar app-state]])])

