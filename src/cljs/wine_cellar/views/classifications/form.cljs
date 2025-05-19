(ns wine-cellar.views.classifications.form
  (:require [wine-cellar.views.components.form :refer
             [form-container form-actions form-row select-field]]
            [wine-cellar.views.components.classification-fields :refer
             [classification-fields]]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]))

(defn classification-form
  [app-state]
  (tap> ["classification-form"])
  (let [new-class (or (:new-classification @app-state) {})
        classifications (:classifications @app-state)]
    [form-container
     {:title "Add New Classification"
      :on-submit #(api/create-classification app-state
                                             (:new-classification @app-state))}
     ;; Use the shared classification fields component
     [classification-fields app-state [:new-classification] classifications]
     ;; Allowed levels field
     [form-row
      [select-field
       {:label "Allowed Levels"
        :multiple true
        :value (:levels new-class [])
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

