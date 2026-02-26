(ns wine-cellar.views.bar.core
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.tabs :refer [tabs]]
            [reagent-mui.material.tab :refer [tab]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [wine-cellar.nav :as nav]
            [wine-cellar.views.bar.spirits :refer [spirits-tab]]
            [wine-cellar.views.bar.inventory :refer [inventory-tab]]
            [wine-cellar.views.bar.recipes :refer [recipes-tab]]))

(def tab-values {:spirits 0 :recipes 1 :inventory 2})

(def tab-keys (into {} (map (fn [[k v]] [v k]) tab-values)))

(defn bar-page
  [app-state]
  (let [active-tab (get-in @app-state [:bar :active-tab] :spirits)
        tab-index (get tab-values active-tab 0)]
    [box
     [box
      {:sx {:display "flex"
            :justifyContent "space-between"
            :alignItems "center"
            :mb 2}} [typography {:variant "h5"} "Bar"]
      [button {:variant "outlined" :color "primary" :on-click #(nav/go-wines!)}
       "Wine Cellar"]]
     [tabs
      {:value tab-index
       :on-change (fn [_ v]
                    (swap! app-state assoc-in
                      [:bar :active-tab]
                      (get tab-keys v :spirits)))
       :sx {:mb 2 :borderBottom "1px solid rgba(0,0,0,0.12)"}}
      [tab {:label "Spirits"}] [tab {:label "Recipes"}]
      [tab {:label "Mixers & Garnishes"}]]
     (case active-tab
       :spirits [spirits-tab app-state]
       :recipes [recipes-tab app-state]
       :inventory [inventory-tab app-state]
       [spirits-tab app-state])]))
