(ns wine-cellar.views.main
  (:require [wine-cellar.views.wines.form :refer [wine-form]]
            [wine-cellar.views.components :refer [toggle-button]]
            [wine-cellar.views.wines.list :refer [wine-list]]
            [wine-cellar.views.wines.detail :refer [wine-details-section]]
            [wine-cellar.views.admin.schema :refer [schema-admin-page]]
            [wine-cellar.views.grape-varieties.list :refer [grape-varieties-page]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.menu :refer [menu]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent.core :as r]
            [wine-cellar.api :as api]))

(defn admin-menu
  [app-state]
  (let [anchor-el (r/atom nil)]
    (fn [] [:div
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
                           (swap! app-state assoc :view :admin-schema))}
              "Database Schema"]]])))

(defn control-buttons
  [app-state]
  [box {:sx {:display "flex" :justifyContent "space-between" :mb 2}}
   [box {:sx {:display "flex" :gap 2}}
    [toggle-button
     {:app-state app-state
      :path [:show-wine-form?]
      :show-text "Add New Wine"
      :hide-text "Show Wine List"}] [admin-menu app-state]]
   [button {:variant "outlined" :color "secondary" :onClick #(api/logout)}
    "Logout"]])

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
     "Wine Cellar"]
    [typography {:variant "subtitle1" :color "text.secondary" :sx {:mt 1}}
     "Track your collection, tastings, and memories"]]
   (when-let [error (:error @app-state)]
     [paper
      {:elevation 3 :sx {:p 2 :mb 3 :bgcolor "error.light" :color "error.dark"}}
      [typography {:variant "body1"} error]])
   (cond
     ;; Admin views
     (= (:view @app-state) :admin-schema) [:div [schema-admin-page]
                                           [button
                                            {:variant "outlined"
                                             :color "primary"
                                             :on-click #(swap! app-state dissoc
                                                          :view)
                                             :sx {:mt 2}} "Back to Wine List"]]
     (= (:view @app-state) :grape-varieties) [:div [grape-varieties-page app-state]
                                              [button
                                               {:variant "outlined"
                                                :color "primary"
                                                :on-click #(swap! app-state dissoc :view)
                                                :sx {:mt 2}} "Back to Wine List"]]
     ;; Wine views
     (:selected-wine-id @app-state) [wine-details-section app-state]
     (:show-wine-form? @app-state) [:div [wine-form app-state]
                                    [control-buttons app-state]]
     :else [:div [wine-list app-state] [control-buttons app-state]])])

