(ns wine-cellar.views.classifications.form
  (:require [wine-cellar.views.components.form :refer
             [form-container form-actions]]
            [wine-cellar.views.components.classification-fields :refer
             [classification-fields]]
            [wine-cellar.api :as api]))

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
     ;; Form buttons
     [form-actions
      {:on-submit #(api/create-classification app-state
                                              (:new-classification @app-state))
       :on-cancel #(swap! app-state assoc :creating-classification? false)
       :submit-text "Create Classification"
       :cancel-text "Cancel"
       :disabled (or (empty? (:country new-class))
                     (empty? (:region new-class)))}]]))

