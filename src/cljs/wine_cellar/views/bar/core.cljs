(ns wine-cellar.views.bar.core
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.tabs :refer [tabs]]
            [reagent-mui.material.tab :refer [tab]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.arrow-back :refer [arrow-back]]
            [reagent-mui.icons.camera-alt :refer [camera-alt]]
            [wine-cellar.nav :as nav]
            [wine-cellar.views.bar.spirits :refer [spirits-tab]]
            [wine-cellar.views.bar.inventory :refer [inventory-tab]]
            [wine-cellar.views.bar.photo-import :refer [photo-import-dialog]]
            [wine-cellar.views.bar.recipes :refer
             [recipes-tab save-recipe-dialog]]))

(def tab-values {:recipes 0 :spirits 1 :inventory 2})

(def tab-keys (into {} (map (fn [[k v]] [v k]) tab-values)))

(def add-form-key
  {:spirits :show-spirit-form?
   :recipes :show-recipe-form?
   :inventory :show-inventory-form?})

(defn bar-page
  [app-state]
  (let [active-tab (get-in @app-state [:bar :active-tab] :recipes)
        tab-index (get tab-values active-tab 0)]
    [box
     [box
      {:sx {:display "flex"
            :alignItems "center"
            :mb 2
            :borderBottom "1px solid rgba(0,0,0,0.12)"}}
      [button
       {:size "small"
        :title "Wine Cellar"
        :sx {:color "text.secondary" :minWidth 0 :p 0.5 :mr 1}
        :on-click #(nav/go-wines!)} [arrow-back {:fontSize "small"}]]
      [tabs
       {:value tab-index
        :on-change (fn [_ v]
                     (swap! app-state assoc-in
                       [:bar :active-tab]
                       (get tab-keys v :recipes)))
        :sx {:flex 1}} [tab {:label "Recipes"}] [tab {:label "Spirits"}]
       [tab {:label "Mixers"}]]
      (when (= active-tab :recipes)
        [button
         {:size "small"
          :title "Import recipe from photo"
          :data-testid "import-recipe-photo"
          :sx {:color "text.secondary" :minWidth 0 :p 0.5 :ml 1}
          :on-click
          #(swap! app-state assoc-in [:bar :photo-import :open?] true)}
         [camera-alt {:fontSize "small"}]])
      [button
       {:size "small"
        :sx {:color "text.secondary" :minWidth 0 :p 0.5 :ml 1}
        :on-click
        #(swap! app-state assoc-in [:bar (get add-form-key active-tab)] true)}
       [add {:fontSize "small"}]]]
     (case active-tab
       :spirits [spirits-tab app-state]
       :recipes [recipes-tab app-state]
       :inventory [inventory-tab app-state]
       [recipes-tab app-state]) [save-recipe-dialog app-state]
     [photo-import-dialog app-state]]))
