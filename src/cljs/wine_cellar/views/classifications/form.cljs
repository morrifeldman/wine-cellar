(ns wine-cellar.views.classifications.form
  (:require [wine-cellar.views.components.form :refer
             [form-container form-actions form-row smart-field select-field]]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]))

(defn classification-form
  [app-state]
  (let [new-class (or (:new-classification @app-state) {})]
    [form-container
     {:title "Add New Classification"
      :on-submit #(api/create-classification app-state
                                             (:new-classification @app-state))}
     ;; Required fields
     [form-row
      [smart-field app-state [:new-classification :country] :required true]
      [smart-field app-state [:new-classification :region] :required true]
      [smart-field app-state [:new-classification :aoc] :label
       "AOC (optional)"]]
     ;; Optional fields
     [form-row
      [smart-field app-state [:new-classification :classification] :label
       "Classification (optional)"]
      [smart-field app-state [:new-classification :vineyard] :label
       "Vineyard (optional)"]
      [select-field
       {:label "Allowed Levels"
        :value (:levels new-class [])
        :required false
        :options common/wine-levels
        :on-change
        #(swap! app-state assoc-in [:new-classification :levels] %)}]]
     ;; Form buttons
     [form-actions
      {:on-submit #(api/create-classification app-state
                                              (:new-classification @app-state))
       :on-cancel #(swap! app-state assoc :creating-classification? false)
       :submit-text "Create Classification"
       :cancel-text "Cancel"
       :disabled (or (empty? (:country new-class))
                     (empty? (:region new-class)))}]]))
