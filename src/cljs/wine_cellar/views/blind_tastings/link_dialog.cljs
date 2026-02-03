(ns wine-cellar.views.blind-tastings.link-dialog
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.list :refer [list]]
            [reagent-mui.material.list-item :refer [list-item]]
            [reagent-mui.material.list-item-button :refer [list-item-button]]
            [reagent-mui.material.list-item-text :refer [list-item-text]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.icons.check :refer [check]]
            [clojure.string :as str]
            [wine-cellar.api :as api]))

(defn- wine-display-name
  [wine]
  (let [producer (:producer wine)
        name (:name wine)
        vintage (:vintage wine)
        region (:region wine)]
    (str (when producer producer)
         (when (and producer name) " ")
         (when name name)
         (when vintage (str " " vintage))
         (when region (str " (" region ")")))))

(defn- filter-wines
  [wines search-text]
  (if (str/blank? search-text)
    wines
    (let [lower-search (str/lower-case search-text)]
      (filter (fn [wine]
                (let [searchable (str/lower-case (str (:producer wine)
                                                      " " (:name wine)
                                                      " " (:region wine)
                                                      " " (:country wine)
                                                      " " (:vintage wine)))]
                  (str/includes? searchable lower-search)))
              wines))))

(defn link-blind-tasting-dialog
  [app-state]
  (r/with-let
   [search-text (r/atom "") selected-wine-id (r/atom nil)]
   (let [blind-state (:blind-tastings @app-state)
         open? (:show-link-dialog? blind-state)
         linking-note-id (:linking-note-id blind-state)
         wines (:wines @app-state)
         filtered-wines (filter-wines wines @search-text)
         linking? (some? linking-note-id)
         close! #(do (reset! search-text "")
                     (reset! selected-wine-id nil)
                     (swap! app-state update
                       :blind-tastings
                       (fn [s]
                         (-> s
                             (assoc :show-link-dialog? false)
                             (assoc :linking-note-id nil)))))
         handle-link #(when (and linking-note-id @selected-wine-id)
                        (api/link-blind-tasting app-state
                                                linking-note-id
                                                @selected-wine-id))]
     [dialog {:open open? :onClose close! :maxWidth "sm" :fullWidth true}
      [dialog-title "Link to Wine"]
      [dialog-content {:sx {:pt 2}}
       [typography {:variant "body2" :color "text.secondary" :sx {:mb 2}}
        "Search for and select the wine to link this blind tasting to."]
       [mui-text-field/text-field
        {:label "Search Wines"
         :fullWidth true
         :size "small"
         :value @search-text
         :autoFocus true
         :onChange #(reset! search-text (.. % -target -value))
         :sx {:mb 2}}]
       [paper {:variant "outlined" :sx {:maxHeight 300 :overflow "auto"}}
        (if (empty? filtered-wines)
          [box {:sx {:p 3 :textAlign "center"}}
           [typography {:color "text.secondary"}
            (if (str/blank? @search-text)
              "No wines in your cellar"
              "No wines match your search")]]
          [list {:dense true}
           (for [wine (take 50 filtered-wines)]
             ^{:key (:id wine)}
             [list-item {:disablePadding true}
              [list-item-button
               {:selected (= @selected-wine-id (:id wine))
                :onClick #(reset! selected-wine-id (:id wine))}
               [list-item-text
                {:primary (wine-display-name wine)
                 :secondary (str (:country wine)
                                 (when (:style wine)
                                   (str " • " (:style wine))))}]
               (when (= @selected-wine-id (:id wine))
                 [check {:color "primary"}])]])])]]
      [dialog-actions [button {:onClick close!} "Cancel"]
       [button
        {:variant "contained"
         :disabled (or (nil? @selected-wine-id) linking?)
         :onClick handle-link}
        (if linking?
          [box {:sx {:display "flex" :alignItems "center" :gap 1}}
           [circular-progress {:size 16}] "Linking..."]
          "Link Wine")]]])))
