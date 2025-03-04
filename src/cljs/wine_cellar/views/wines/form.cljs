(ns wine-cellar.views.wines.form
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [wine-cellar.views.components :refer [smart-field smart-select-field multi-select-field
                                                  form-section form-field-style]]
            [wine-cellar.views.classifications.form :refer [classification-form]]
            [wine-cellar.utils.formatting :refer [valid-name-producer? unique-countries
                                                  regions-for-country aocs-for-region
                                                  classifications-for-aoc levels-for-classification]]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]))

(defn wine-form [app-state]
  (let [new-wine (:new-wine @app-state)
        classifications (:classifications @app-state)]
    [paper {:elevation 3 :sx {:p 3 :mb 3}}
     [typography {:variant "h5" :component "h2" :sx {:mb 3}} "Add New Wine"]
     [:form {:on-submit (fn [e]
                          (.preventDefault e)
                          (if (valid-name-producer? new-wine)
                            (api/create-wine
                             app-state
                             (-> new-wine
                                 (update :price js/parseFloat)
                                 (update :vintage #(js/parseInt % 10))
                                 (update :quantity #(js/parseInt % 10))))
                            (swap! app-state assoc
                                   :error
                                   "Either Wine Name or Producer must be provided")))}

      [grid {:container true :spacing 2}

       ;; Basic Information Section
       [form-section "Basic Information"]

       [grid {:item true :xs 12 :md 4}
        [text-field
         {:label "Name"
          :value (:name new-wine)
          :margin "normal"
          :fullWidth false
          :variant "outlined"
          :sx form-field-style
          :helperText "Either Name or Producer required"
          :on-change #(swap! app-state assoc-in [:new-wine :name]
                             (.. % -target -value))}]]

       [grid {:item true :xs 12 :md 4}
        [text-field
         {:label "Producer"
          :value (:producer new-wine)
          :margin "normal"
          :fullWidth false
          :variant "outlined"
          :sx form-field-style
          :helperText "Either Name or Producer required"
          :on-change #(swap! app-state assoc-in [:new-wine :producer]
                             (.. % -target -value))}]]

;; Styles is special due to multi-select
       [grid {:item true :xs 12 :md 4}
        [multi-select-field {:label "Style"
                             :value (:styles new-wine)
                             :required true
                             :options common/wine-styles
                             :on-change #(swap! app-state assoc-in [:new-wine :styles] %)}]]

       ;; Wine Classification Section
       [form-section "Wine Classification"]

       [grid {:item true :xs 12}
        [box {:display "flex" :justifyContent "flex-end"}
         [button
          {:variant "outlined"
           :color "secondary"
           :size "small"
           :onClick #(do
                       (swap! app-state assoc :creating-classification? true)
                       (swap! app-state assoc :new-classification {:levels []}))}
          "Create New Classification"]]]

       ;; Show classification form when needed
       (when (:creating-classification? @app-state)
         [grid {:item true :xs 12}
          [classification-form app-state]])

       ;; Country dropdown (still needs custom logic)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :country]
         :required true
         :options (map #(vector % %) (unique-countries classifications))]]

       ;; Region dropdown (dependent on country)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :region]
         :required true
         :disabled (empty? (:country new-wine))
         :options (map #(vector % %)
                       (regions-for-country classifications (:country new-wine)))]]

       ;; AOC dropdown (dependent on region)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :aoc]
         :disabled (or (empty? (:country new-wine))
                       (empty? (:region new-wine)))
         :options (map #(vector % %)
                       (aocs-for-region classifications
                                        (:country new-wine)
                                        (:region new-wine)))]]

       ;; Classification dropdown (dependent on AOC)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :classification]
         :disabled (or (empty? (:country new-wine))
                       (empty? (:region new-wine))
                       (empty? (:aoc new-wine)))
         :options (map #(vector % %)
                       (classifications-for-aoc classifications
                                                (:country new-wine)
                                                (:region new-wine)
                                                (:aoc new-wine)))]]

       ;; Level dropdown (dependent on classification)
       [grid {:item true :xs 12 :md 4}
        [smart-select-field app-state [:new-wine :level]
         :disabled (or (empty? (:country new-wine))
                       (empty? (:region new-wine))
                       (empty? (:aoc new-wine))
                       (empty? (:classification new-wine)))
         :options (map #(vector % %)
                       (levels-for-classification
                        classifications
                        (:country new-wine)
                        (:region new-wine)
                        (:aoc new-wine)
                        (:classification new-wine)))
         :on-change #(swap! app-state assoc-in [:new-wine :level]
                            (when-not (empty? %) %))]]

       ;; Vintage with min/max constraints
       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :vintage]
         :type "number"
         :required true
         :min 1900
         :max 2100
         :on-change #(swap! app-state assoc-in [:new-wine :vintage]
                            (js/parseInt %))]]

       ;; Additional Information Section
       [form-section "Additional Information"]

       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :location]
         :required true]]

       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :quantity]
         :type "number"
         :required true
         :min 0
         :on-change #(swap! app-state assoc-in [:new-wine :quantity]
                            (js/parseInt %))]]

;; Price with decimal step
       [grid {:item true :xs 12 :md 4}
        [smart-field app-state [:new-wine :price]
         :type "number"
         :required true
         :step "0.01"
         :min "0"
         :value (if (string? (:price new-wine))
                  (:price new-wine)
                  (str (:price new-wine)))]]

       [grid {:item true :xs 12}
        [box {:sx {:display "flex" :justifyContent "flex-end" :mt 2}}
         [button
          {:type "submit"
           :variant "contained"
           :color "primary"}
          "Add Wine"]]]]]]))
