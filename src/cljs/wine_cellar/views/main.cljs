(ns wine-cellar.views.main
  (:require [wine-cellar.views.wines.form :refer [wine-form]]
            [wine-cellar.views.components :refer [toggle-button]]
            [wine-cellar.views.wines.list :refer [wine-list]]
            [wine-cellar.views.wines.detail :refer [wine-details-section]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]))

(defn wine-form-toggle [app-state]
  [toggle-button
   {:app-state app-state
    :path [:show-wine-form?]
    :show-text "Add New Wine"
    :hide-text "Hide Wine Form"}])

(defn main-app [app-state]
  [box {:sx {:p 3
             :maxWidth "1200px"
             :mx "auto"}}
   [box {:sx {:textAlign "center"
              :mb 4
              :pb 3
              :borderBottom "1px solid rgba(0,0,0,0.08)"}}
    [typography {:variant "h2"
                 :component "h1"
                 :sx {:fontWeight 300
                      :color "primary.main"}}
     "Wine Cellar"]
    [typography {:variant "subtitle1"
                 :color "text.secondary"
                 :sx {:mt 1}}
     "Track your collection, tastings, and memories"]]

   (when-let [error (:error @app-state)]
     [paper {:elevation 3
             :sx {:p 2 :mb 3 :bgcolor "error.light" :color "error.dark"}}
      [typography {:variant "body1"} error]])

   (cond
     ;; If a wine is selected, show the details section
     (:selected-wine-id @app-state)
     [wine-details-section app-state]

     ;; If wine form is open, show only the toggle and form
     (:show-wine-form? @app-state)
     [:div
      [wine-form-toggle app-state]
      [wine-form app-state]]

     ;; Otherwise, show the toggle, form, and list
     :else
     [:div
      [wine-form-toggle app-state]
      [wine-list app-state]])])

