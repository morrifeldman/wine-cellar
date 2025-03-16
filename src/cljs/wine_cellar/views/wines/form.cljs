(ns wine-cellar.views.wines.form
  (:require
   [wine-cellar.views.components.form :refer [form-container form-actions form-row
                                              form-divider text-field currency-field
                                              number-field select-field
                                              smart-field smart-select-field]]
   [wine-cellar.views.classifications.form :refer [classification-form]]
   [wine-cellar.utils.formatting :refer [valid-name-producer? unique-countries
                                         regions-for-country aocs-for-region
                                         classifications-for-aoc levels-for-classification]]
   [wine-cellar.api :as api]
   [wine-cellar.common :as common]
   [reagent-mui.material.button :refer [button]]
   [reagent-mui.material.grid :refer [grid]]
   [reagent-mui.material.box :refer [box]]))

(defn wine-form [app-state]
  (let [new-wine (:new-wine @app-state)
        classifications (:classifications @app-state)
        submit-handler (fn []
                         (if (valid-name-producer? new-wine)
                           (api/create-wine
                            app-state
                            (-> new-wine
                                (update :price js/parseFloat)
                                (update :vintage (fn [v] (js/parseInt v 10)))
                                (update :quantity (fn [q] (js/parseInt q 10)))))
                           (swap! app-state assoc
                                  :error
                                  "Either Wine Name or Producer must be provided")))]
    [form-container
     {:title "Add New Wine"
      :on-submit submit-handler}

     ;; Basic Information Section
     [form-divider "Basic Information"]

     [form-row
      [text-field
       {:label "Name"
        :value (:name new-wine)
        :helper-text "Either Name or Producer required"
        :on-change #(swap! app-state assoc-in [:new-wine :name] %)}]

      [text-field
       {:label "Producer"
        :value (:producer new-wine)
        :helper-text "Either Name or Producer required"
        :on-change #(swap! app-state assoc-in [:new-wine :producer] %)}]

      [select-field
       {:label "Style"
        :value (:styles new-wine)
        :required true
        :multiple true
        :options common/wine-styles
        :on-change #(swap! app-state assoc-in [:new-wine :styles] %)}]]

     ;; Wine Classification Section
     [form-divider "Wine Classification"]

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

     ;; Classification dropdowns
     [form-row
      [smart-select-field app-state [:new-wine :country]
       :required true
       :options (unique-countries classifications)]

      [smart-select-field app-state [:new-wine :region]
       :required true
       :disabled (empty? (:country new-wine))
       :options (regions-for-country classifications (:country new-wine))]

      [smart-select-field app-state [:new-wine :aoc]
       :disabled (or (empty? (:country new-wine))
                     (empty? (:region new-wine)))
       :options (aocs-for-region classifications
                                 (:country new-wine)
                                 (:region new-wine))]]

     [form-row
      [smart-select-field app-state [:new-wine :classification]
       :disabled (or (empty? (:country new-wine))
                     (empty? (:region new-wine))
                     (empty? (:aoc new-wine)))
       :options (classifications-for-aoc classifications
                                         (:country new-wine)
                                         (:region new-wine)
                                         (:aoc new-wine))]

      [smart-select-field app-state [:new-wine :level]
       :disabled (or (empty? (:country new-wine))
                     (empty? (:region new-wine))
                     (empty? (:aoc new-wine))
                     (empty? (:classification new-wine)))
       :options (levels-for-classification
                 classifications
                 (:country new-wine)
                 (:region new-wine)
                 (:aoc new-wine)
                 (:classification new-wine))
       :on-change #(swap! app-state assoc-in [:new-wine :level]
                          (when-not (empty? %) %))]

      [number-field
       {:label "Vintage"
        :required true
        :min 1900
        :max 2100
        :value (:vintage new-wine)
        :on-change #(swap! app-state assoc-in [:new-wine :vintage]
                           (js/parseInt %))}]]

     ;; Additional Information Section
     [form-divider "Additional Information"]

     [form-row
      [smart-field app-state [:new-wine :location] :required true]

      [number-field
       {:label "Quantity"
        :required true
        :min 0
        :value (:quantity new-wine)
        :on-change #(swap! app-state assoc-in [:new-wine :quantity]
                           (js/parseInt %))}]

      [currency-field
       {:label "Price"
        :required true
        :value (if (string? (:price new-wine))
                 (:price new-wine)
                 (str (:price new-wine)))
        :on-change #(swap! app-state assoc-in [:new-wine :price] %)}]]

     ;; Form actions
     [form-actions
      {:on-submit submit-handler
       :submit-text "Add Wine"}]]))
