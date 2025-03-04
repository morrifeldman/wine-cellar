(ns wine-cellar.views.main
  (:require [wine-cellar.views.wines.form :refer [wine-form]]
            [wine-cellar.views.wines.list :refer [wine-list]]
            [wine-cellar.views.wines.detail :refer [wine-details-section]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]))

(defn main-app [app-state]
  [box {:sx {:p 3}}
   [typography {:variant "h2" :component "h1" :sx {:mb 3 :textAlign "center"}}
    "Wine Cellar"]

   (when-let [error (:error @app-state)]
     [paper {:elevation 3
             :sx {:p 2 :mb 3 :bgcolor "error.light" :color "error.dark"}}
      [typography {:variant "body1"} error]])

   [wine-form app-state]

   (if (:selected-wine-id @app-state)
     [wine-details-section app-state]
     [wine-list app-state])])

