(ns wine-cellar.views.wines.form
  (:require [clojure.string :as str]
            [reagent-mui.icons.auto-awesome :refer [auto-awesome]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent.core :as r]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]
            [wine-cellar.utils.formatting :refer
             [aocs-for-region classifications-for-aoc levels-for-classification
              regions-for-country unique-countries unique-purveyors
              valid-name-producer? vineyards-for-region]]
            [wine-cellar.utils.vintage :as vintage]
            [wine-cellar.views.components.form :refer
             [currency-field date-field form-actions form-container form-divider
              form-row number-field select-field smart-select-field text-field
              year-field]]
            [wine-cellar.views.components.image-upload :refer [image-upload]]))

(defn vintage
  [app-state new-wine]
  [year-field
   {:label "Vintage"
    :required false
    :free-solo true
    :value (:vintage new-wine)
    :error (when (:vintage new-wine)
             (boolean (vintage/valid-vintage? (:vintage new-wine))))
    :helper-text (if (:vintage new-wine)
                   (vintage/valid-vintage? (:vintage new-wine))
                   "Leave empty for non-vintage (NV) wines")
    :options (concat ["NV"] (vintage/default-vintage-years))
    :on-change #(swap! app-state assoc-in
                  [:new-wine :vintage]
                  (cond (empty? %) nil
                        (= % "NV") nil
                        :else (js/parseInt % 10)))}])

(defn drink-from-year
  [app-state new-wine]
  (let [drink-from-year (:drink_from_year new-wine)
        drink-until-year (:drink_until_year new-wine)
        invalid? (when (and drink-from-year drink-until-year)
                   (vintage/valid-tasting-window? drink-from-year
                                                  drink-until-year))]
    [year-field
     {:label "Drink From Year"
      :free-solo true
      :value drink-from-year
      :options (vintage/default-drink-from-years)
      :error (boolean invalid?)
      :helper-text (or invalid? "Year when the wine is/was ready to drink")
      :on-change #(swap! app-state assoc-in
                    [:new-wine :drink_from_year]
                    (when-not (empty? %) (js/parseInt % 10)))}]))

(defn drink-until-year
  [app-state new-wine]
  (let [drink-from-year (:drink_from_year new-wine)
        drink-until-year (:drink_until_year new-wine)
        invalid? (when (and drink-from-year drink-until-year)
                   (vintage/valid-tasting-window? drink-from-year
                                                  drink-until-year))]
    [year-field
     {:label "Drink Until Year"
      :free-solo true
      :value drink-until-year
      :options (vintage/default-drink-until-years)
      :error (boolean invalid?)
      :helper-text (or invalid? "Year when the wine should be consumed by")
      :on-change #(swap! app-state assoc-in
                    [:new-wine :drink_until_year]
                    (when-not (empty? %) (js/parseInt % 10)))}]))

(defn wine-form
  [app-state]
  (let [new-wine (:new-wine @app-state)
        classifications (:classifications @app-state)
        submitting? (:submitting-wine? @app-state)
        validate-wine
        (fn []
          (cond (not (valid-name-producer? new-wine))
                "Either Wine Name or Producer must be provided"
                (empty? (:country new-wine)) "Country is required"
                (empty? (:region new-wine)) "Region is required"
                (and (:vintage new-wine)
                     (not (nil? (vintage/valid-vintage? (:vintage new-wine)))))
                (vintage/valid-vintage? (:vintage new-wine))
                (empty? (:style new-wine)) "Style is required"
                (nil? (:quantity new-wine)) "Quantity is required"
                (nil? (:price new-wine)) "Price is required"
                (or (empty? (:location new-wine))
                    (not (common/valid-location? (:location new-wine))))
                common/format-location-error
                (and (:level new-wine)
                     (seq (:level new-wine))
                     (not (contains? common/wine-levels (:level new-wine))))
                (str "Level must be one of: "
                     (str/join ", " (sort common/wine-levels)))
                :else nil))
        submit-handler
        (fn []
          (if-let [error (validate-wine)]
            (swap! app-state assoc :error error)
            (do (swap! app-state assoc :submitting-wine? true)
                (api/create-wine
                 app-state
                 (-> new-wine
                     (update :price js/parseFloat)
                     (update :vintage (fn [v] (js/parseInt v 10)))
                     (update :quantity (fn [q] (js/parseInt q 10)))
                     (assoc :create-classification-if-needed true))))))]
    [form-container {:title "Add New Wine" :on-submit submit-handler}
     ;; Basic Information Section
     [form-divider "Basic Information"]
     [form-row
      [text-field
       {:label "Producer"
        :value (:producer new-wine)
        :helper-text "Either Name or Producer required"
        :on-change #(swap! app-state assoc-in [:new-wine :producer] %)}]
      [text-field
       {:label "Name"
        :value (:name new-wine)
        :helper-text "Either Name or Producer required"
        :on-change #(swap! app-state assoc-in [:new-wine :name] %)}]
      [select-field
       {:label "Style"
        :value (:style new-wine)
        :required true
        :multiple false
        :options common/wine-styles
        :on-change #(swap! app-state assoc-in [:new-wine :style] %)}]]
     ;; Wine Classification Section
     [form-divider "Grape Varieties"]
     [form-row
      [typography {:variant "body2" :color "text.secondary"}
       "You can add grape varieties after creating the wine."]]
     [form-divider "Wine Classification"]
     ;; Classification dropdowns with free-solo mode
     [form-row
      ^{:key "select-country"}
      [smart-select-field app-state [:new-wine :country] :required true
       :free-solo true :options (unique-countries classifications)]
      [smart-select-field app-state [:new-wine :region] :required true
       :free-solo true :disabled (empty? (:country new-wine)) :options
       (regions-for-country classifications (:country new-wine))]
      [smart-select-field app-state [:new-wine :aoc] :free-solo true :disabled
       (or (empty? (:country new-wine)) (empty? (:region new-wine))) :options
       (aocs-for-region classifications
                        (:country new-wine)
                        (:region new-wine))]]
     [form-row
      [smart-select-field app-state [:new-wine :vineyard] :free-solo true
       :disabled (or (empty? (:country new-wine)) (empty? (:region new-wine)))
       :options
       (vineyards-for-region classifications
                             (:country new-wine)
                             (:region new-wine)) :label "Vineyard"]
      [smart-select-field app-state [:new-wine :classification] :free-solo true
       :disabled (or (empty? (:country new-wine)) (empty? (:region new-wine)))
       :options
       (classifications-for-aoc classifications
                                (:country new-wine)
                                (:region new-wine)
                                (:aoc new-wine))]
      [smart-select-field app-state [:new-wine :level] :free-solo true :disabled
       (or (empty? (:country new-wine)) (empty? (:region new-wine))) :options
       (levels-for-classification classifications
                                  (:country new-wine)
                                  (:region new-wine)
                                  (:aoc new-wine)
                                  (:classification new-wine)) :helper-text
       (str "Must be one of: " (str/join ", " (sort common/wine-levels)))
       :on-blur
       #(when (and (:level new-wine)
                   (seq (:level new-wine))
                   (not (contains? common/wine-levels (:level new-wine))))
          (swap! app-state assoc
            :error
            (str "Level must be one of: "
                 (str/join ", " (sort common/wine-levels)))))]]
     [form-divider "Vintage"] [form-row [vintage app-state new-wine]]
     [form-row [drink-from-year app-state new-wine]
      [drink-until-year app-state new-wine]]
     [form-row
      [box {:sx {:sx {:mt 2}}}
       [button
        {:variant "outlined"
         :color "secondary"
         :size "small"
         :disabled (or (:suggesting-drinking-window? @app-state)
                       (not (and (:producer new-wine)
                                 (:country new-wine)
                                 (:region new-wine)
                                 (:style new-wine))))
         :startIcon (r/as-element [auto-awesome])
         :onClick (fn []
                    (-> (api/suggest-drinking-window app-state new-wine)
                        (.then
                         (fn [result]
                           ;; Update the new wine with the suggested
                           ;; drinking window
                           (swap! app-state update
                             :new-wine
                             merge
                             {:drink_from_year (:drink_from_year result)
                              :drink_until_year (:drink_until_year result)})
                           ;; Show success message with reasoning
                           (swap! app-state assoc
                             :window-reason
                             (str "Drinking window suggested: "
                                  (:drink_from_year result)
                                  " to " (:drink_until_year result)
                                  " (" (:confidence result)
                                  " confidence)\n\n" (:reasoning result)))))
                        (.catch (fn [error]
                                  (swap! app-state assoc
                                    :error
                                    (str "Failed to suggest drinking window: "
                                         error))))))}
        (if (:suggesting-drinking-window? @app-state)
          [box {:sx {:display "flex" :alignItems "center"}}
           [circular-progress {:size 20 :sx {:mr 1}}] "Suggesting..."]
          "Suggest Drinking Window")]
       [typography {:variant "body2" :sx {:mt 1}} (:window-reason @app-state)]]]
     ;; Wine Label Images Section
     [form-divider "Wine Label Images"]
     [form-row
      [box {:sx {:width "100%" :display "flex" :flexDirection "column" :gap 2}}
       [image-upload
        {:image-data (:label_image new-wine)
         :label-type "front"
         :on-image-change #(swap! app-state update :new-wine merge %)
         :on-image-remove #(swap! app-state update
                             :new-wine
                             (fn [wine]
                               (-> wine
                                   (dissoc :label_image)
                                   (dissoc :label_thumbnail))))}]
       (when (and (:label_image new-wine) (not (:analyzing-label? @app-state)))
         [button
          {:variant "contained"
           :color "secondary"
           :size "small"
           :disabled (or submitting? (not (:label_image new-wine)))
           :onClick
           (fn []
             (let [image-data {:label_image (:label_image new-wine)
                               :back_label_image (:back_label_image new-wine)}]
               (-> (api/analyze-wine-label app-state image-data)
                   (.then (fn [result]
                            (swap! app-state update :new-wine merge result)))
                   (.catch (fn [error]
                             (swap! app-state assoc
                               :error
                               (str "Failed to analyze label: " error)))))))
           :startIcon (r/as-element [auto-awesome])} "Analyze Label"])
       (when (:analyzing-label? @app-state)
         [box {:sx {:display "flex" :alignItems "center" :mt 1}}
          [circular-progress {:size 24 :sx {:mr 1}}]
          [typography {:variant "body2"} "Analyzing label..."]])]]
     [form-row
      [image-upload
       {:image-data (:back_label_image new-wine)
        :label-type "back"
        :on-image-change #(swap! app-state update :new-wine merge %)
        :on-image-remove #(swap! app-state update
                            :new-wine
                            (fn [wine] (dissoc wine :back_label_image)))}]]
     ;; Additional Information Section
     [form-divider "Additional Information"]
     [form-row
      [text-field
       {:label "Location"
        :required true
        :value (:location new-wine)
        :helper-text common/format-location-error
        :error (and (:location new-wine)
                    (not (common/valid-location? (:location new-wine))))
        :on-change #(swap! app-state assoc-in [:new-wine :location] %)
        :on-blur
        #(when (and (:location new-wine)
                    (not (common/valid-location? (:location new-wine))))
           (swap! app-state assoc :error common/format-location-error))}]
      [number-field
       {:label "Quantity"
        :required true
        :min 0
        :value (:quantity new-wine)
        :on-change
        #(swap! app-state assoc-in [:new-wine :quantity] (js/parseInt %))}]
      [currency-field
       {:label "Price"
        :required true
        :value (if (string? (:price new-wine))
                 (:price new-wine)
                 (str (:price new-wine)))
        :on-change #(swap! app-state assoc-in [:new-wine :price] %)}]]
     [form-row
      [number-field
       {:label "Alcohol %"
        :required false
        :min 0
        :max 100
        :step 0.1
        :value (:alcohol_percentage new-wine)
        :helper-text "e.g., 13.5 for 13.5% ABV"
        :on-change #(swap! app-state assoc-in
                      [:new-wine :alcohol_percentage]
                      (when-not (empty? %) (js/parseFloat %)))}]]
     [form-row
      [smart-select-field app-state [:new-wine :purveyor] :free-solo true :label
       "Purchased From" :options (unique-purveyors (:wines @app-state))]
      [date-field
       {:label "Purchase Date"
        :value (:purchase_date new-wine)
        :on-change #(swap! app-state assoc-in [:new-wine :purchase_date] %)}]]
     ;; Form actions
     [form-actions
      {:submit-text "Add Wine"
       :cancel-text "Cancel"
       :loading? submitting?
       :on-cancel
       #(swap! app-state assoc :show-wine-form? false :new-wine {})}]]))
