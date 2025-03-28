(ns wine-cellar.views.wines.form
  (:require
   [clojure.string :as str]
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

(def vintage-range-years 10)  ;; How many recent years to show
(def vintage-range-offset 2)  ;; How many years back from current year to start

;; Configuration for drinking window years
(def drink-from-future-years 10)  ;; How many future years to show in "Drink From" dropdown
(def drink-from-past-years 5)     ;; How many past years to show in "Drink From" dropdown
(def drink-until-years 20)        ;; How many future years to show in "Drink Until" dropdown

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

                          (and (:level new-wine)
                               (seq (:level new-wine))
                               (not (contains? common/wine-levels (:level new-wine))))
                          (str "Level must be one of: " (clojure.string/join ", " (sort common/wine-levels)))

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
                 (:classification new-wine))
       :helper-text (str "Must be one of: " (str/join ", " (sort common/wine-levels)))
       :on-blur #(when (and (:level new-wine)
                            (seq (:level new-wine))
                            (not (contains? common/wine-levels (:level new-wine))))
                   (swap! app-state assoc :error
                          (str "Level must be one of: " (clojure.string/join ", " (sort common/wine-levels)))))]

      [select-field
       {:label "Vintage"
        :required true
        :free-solo true
        :value (when (:vintage new-wine) (str (:vintage new-wine)))
        :options (concat
                     ;; Recent years starting a few years back
                  (map str (range (- (js/parseInt (.getFullYear (js/Date.))) vintage-range-offset)
                                  (- (- (js/parseInt (.getFullYear (js/Date.))) vintage-range-offset) vintage-range-years)
                                  -1))
                     ;; Then decades for older wines
                  (map #(str (+ 1900 (* % 10))) (range 9 -1 -1)))
        :on-change #(swap! app-state assoc-in [:new-wine :vintage]
                           (when-not (empty? %) (js/parseInt % 10)))}]]

     [form-divider "Tasting Window"]

     [form-row
      [select-field
       {:label "Drink From Year"
        :free-solo true
        :value (when (:drink_from_year new-wine) (str (:drink_from_year new-wine)))
        :options (concat
             ;; Current year and future years for aging potential (show these first)
                  (map str (range (js/parseInt (.getFullYear (js/Date.)))
                                  (+ (js/parseInt (.getFullYear (js/Date.))) drink-from-future-years)))
             ;; Add past years for already-drinkable wines
                  (map str (range (- (js/parseInt (.getFullYear (js/Date.))) 1)
                                  (- (js/parseInt (.getFullYear (js/Date.))) (inc drink-from-past-years))
                                  -1)))
        :helper-text "Year when the wine is/was ready to drink"
        :on-change #(swap! app-state assoc-in [:new-wine :drink_from_year]
                           (when-not (empty? %) (js/parseInt % 10)))}]

      [select-field
       {:label "Drink Until Year"
        :free-solo true
        :value (when (:drink_until_year new-wine) (str (:drink_until_year new-wine)))
        :options (concat
            ;; Current year and future years for aging potential
                  (map str (range (js/parseInt (.getFullYear (js/Date.)))
                                  (+ (js/parseInt (.getFullYear (js/Date.))) drink-until-years))))
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
      {:submit-text "Add Wine"
       :cancel-text "Cancel"
       :on-cancel #(swap! app-state assoc :show-wine-form? false)}]]))

