(ns wine-cellar.views.wines.form
  (:require
   [wine-cellar.views.components.form
    :refer [form-container form-actions form-row
            form-divider text-field currency-field
            number-field select-field
            smart-field smart-select-field]]
   [wine-cellar.utils.formatting
    :refer [valid-name-producer? unique-countries
            regions-for-country aocs-for-region
            classifications-for-aoc levels-for-classification]]
   [wine-cellar.api :as api]
   [wine-cellar.common :as common]))

(defn wine-form [app-state]
  (let [new-wine (:new-wine @app-state)
        classifications (:classifications @app-state)
        validate-wine (fn []
                        (cond
                          (not (valid-name-producer? new-wine))
                          "Either Wine Name or Producer must be provided"

                          (empty? (:country new-wine))
                          "Country is required"

                          (empty? (:region new-wine))
                          "Region is required"

                          (nil? (:vintage new-wine))
                          "Vintage is required"

                          (empty? (:styles new-wine))
                          "Style is required"

                          (nil? (:quantity new-wine))
                          "Quantity is required"

                          (nil? (:price new-wine))
                          "Price is required"

                          :else nil))
        submit-handler (fn []
                         (if-let [error (validate-wine)]
                           (swap! app-state assoc :error error)
                           (api/create-wine
                            app-state
                            (-> new-wine
                                (update :price js/parseFloat)
                                (update :vintage (fn [v] (js/parseInt v 10)))
                                (update :quantity (fn [q] (js/parseInt q 10)))
                                (assoc :create-classification-if-needed true)))))]
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

     ;; Classification dropdowns with free-solo mode
     [form-row

      ^{:key "select-country"}
      [smart-select-field app-state [:new-wine :country]
       :required true
       :free-solo true
       :options (unique-countries classifications)]

      [smart-select-field app-state [:new-wine :region]
       :required true
       :free-solo true
       :disabled (empty? (:country new-wine))
       :options (regions-for-country classifications (:country new-wine))]

      [smart-select-field app-state [:new-wine :aoc]
       :free-solo true
       :disabled (or (empty? (:country new-wine))
                     (empty? (:region new-wine)))
       :options (aocs-for-region classifications (:country new-wine) (:region new-wine))]]

     [form-row
      [smart-select-field app-state [:new-wine :classification]
       :free-solo true
       :disabled (or (empty? (:country new-wine))
                     (empty? (:region new-wine)))
       :options (classifications-for-aoc classifications
                                         (:country new-wine)
                                         (:region new-wine)
                                         (:aoc new-wine))]

      [smart-select-field app-state [:new-wine :level]
       :free-solo true
       :disabled (or (empty? (:country new-wine))
                     (empty? (:region new-wine))
                     (empty? (:classification new-wine)))
       :options (levels-for-classification
                 classifications
                 (:country new-wine)
                 (:region new-wine)
                 (:aoc new-wine)
                 (:classification new-wine))]

      [number-field
       {:label "Vintage"
        :required true
        :min 1900
        :max 2100
        :value (:vintage new-wine)
        :on-change #(swap! app-state assoc-in [:new-wine :vintage]
                           (js/parseInt %))}]]

     [form-divider "Tasting Window"]

     [form-row
      [number-field
       {:label "Drink From Year"
        :min 1900
        :max 2100
        :value (:drink_from_year new-wine)
        :helper-text "Year when the wine will be ready to drink"
        :on-change #(swap! app-state assoc-in [:new-wine :drink_from_year]
                           (when-not (empty? %) (js/parseInt % 10)))}]

      [number-field
       {:label "Drink Until Year"
        :min 1900
        :max 2100
        :value (:drink_until_year new-wine)
        :helper-text "Year when the wine should be consumed by"
        :on-change #(swap! app-state assoc-in [:new-wine :drink_until_year]
                           (when-not (empty? %) (js/parseInt % 10)))}]]

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
      {:submit-text "Add Wine"}]]))

