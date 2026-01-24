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
             [appellations-for-region classifications-for-appellation
              designations-for-classification regions-for-country
              unique-countries unique-purveyors valid-name-producer?
              vineyards-for-region]]
            [wine-cellar.utils.vintage :as vintage]
            [wine-cellar.views.components.form :refer
             [currency-field date-field form-actions form-container form-divider
              form-row number-field select-field smart-select-field text-field
              uncontrolled-text-area-field year-field]]
            [wine-cellar.views.components.image-upload :refer [image-upload]]
            [wine-cellar.views.components.ai-provider-toggle :refer
             [provider-toggle-button]]))

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

(defn disgorgement-year
  [app-state new-wine]
  [year-field
   {:label "Disgorgement Year"
    :free-solo true
    :value (:disgorgement_year new-wine)
    :options (if-let [vintage (:vintage new-wine)]
               (filter #(>= % vintage) (vintage/default-vintage-years 0))
               (vintage/default-vintage-years))
    :helper-text "Year when the sparkling wine was disgorged"
    :on-change #(swap! app-state assoc-in
                  [:new-wine :disgorgement_year]
                  (when-not (empty? %) (js/parseInt % 10)))}])

(defn dosage
  [app-state new-wine]
  [number-field
   {:label "Dosage (g/L)"
    :required false
    :min 0
    :step 1
    :value (when-let [dosage (:dosage new-wine)] (str dosage))
    :helper-text "Sugar added at disgorgement, in grams per liter"
    :on-change #(swap! app-state assoc-in
                  [:new-wine :dosage]
                  (if (or (nil? %) (str/blank? %))
                    nil
                    (let [parsed (js/parseFloat %)]
                      (if (js/isNaN parsed) nil (js/Math.round parsed)))))}])

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
        window-commentary-ref (r/atom nil)
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
                (not (common/valid-location? (:location new-wine)))
                common/format-location-error
                (and (:designation new-wine)
                     (seq (:designation new-wine))
                     (not (contains? common/wine-designations
                                     (:designation new-wine))))
                (str "Designation must be one of: "
                     (str/join ", " (sort common/wine-designations)))
                :else nil))
        submit-handler
        (fn []
          (if-let [error (validate-wine)]
            (swap! app-state assoc :error error)
            (do (swap! app-state assoc :submitting-wine? true)
                ;; Extract tasting window commentary from ref
                (let [commentary (when @window-commentary-ref
                                   (.-value @window-commentary-ref))]
                  (api/create-wine
                   app-state
                   (-> new-wine
                       (update :price js/parseFloat)
                       (update :vintage (fn [v] (js/parseInt v 10)))
                       (update :quantity (fn [q] (js/parseInt q 10)))
                       (update :original_quantity
                               (fn [_] (js/parseInt (:quantity new-wine) 10)))
                       (assoc :tasting_window_commentary commentary)
                       (assoc :create-classification-if-needed true)))))))]
    [form-container {:title "Add New Wine" :on-submit submit-handler}
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
       (let [has-label (:label_image new-wine)
             analyzing? (:analyzing-label? @app-state)]
         (when has-label
           [box
            {:sx {:display "flex"
                  :alignItems "center"
                  :flexWrap "wrap"
                  :gap 1
                  :justifyContent "center"}}
            [button
             {:variant "contained"
              :color "secondary"
              :size "small"
              :disabled (or submitting? analyzing?)
              :onClick
              (fn []
                (let [image-data {:label_image (:label_image new-wine)
                                  :back_label_image (:back_label_image
                                                     new-wine)}]
                  (-> (api/analyze-wine-label app-state image-data)
                      (.then (fn [result]
                               (swap! app-state update :new-wine merge result)))
                      (.catch (fn [error]
                                (swap! app-state assoc
                                  :error
                                  (str "Failed to analyze label: " error)))))))
              :startIcon (when-not analyzing? (r/as-element [auto-awesome]))}
             (if analyzing?
               [box {:sx {:display "flex" :alignItems "center"}}
                [circular-progress {:size 20 :sx {:mr 1}}] "Analyzing..."]
               "Analyze Label")]
            [provider-toggle-button app-state
             {:mobile-min-width "auto"
              :sx {:minWidth "auto" :px 1 :py 0.25}}]]))]]
     [form-row
      [image-upload
       {:image-data (:back_label_image new-wine)
        :label-type "back"
        :on-image-change #(swap! app-state update :new-wine merge %)
        :on-image-remove #(swap! app-state update
                            :new-wine
                            (fn [wine] (dissoc wine :back_label_image)))}]]
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
        :sx {"& .MuiAutocomplete-popupIndicator" {:color "text.secondary"}}
        :on-change #(swap! app-state assoc-in [:new-wine :style] %)}]]
     [form-row
      [select-field
       {:label "Closure"
        :value (:closure_type new-wine)
        :required false
        :multiple false
        :options common/closure-type-options
        :on-change #(swap! app-state assoc-in [:new-wine :closure_type] %)}]
      [select-field
       {:label "Bottle Format"
        :value (:bottle_format new-wine)
        :required false
        :multiple false
        :options common/bottle-formats
        :on-change #(swap! app-state assoc-in [:new-wine :bottle_format] %)}]]
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
       :free-solo true :disabled (empty? (:country new-wine)) :tooltip
       (:region common/field-descriptions) :options
       (regions-for-country classifications (:country new-wine))]]
     [form-row
      [smart-select-field app-state [:new-wine :appellation] :free-solo true
       :disabled (or (empty? (:country new-wine)) (empty? (:region new-wine)))
       :label "Appellation" :tooltip (:appellation common/field-descriptions)
       :options
       (appellations-for-region classifications
                                (:country new-wine)
                                (:region new-wine))]
      [smart-select-field app-state [:new-wine :appellation_tier] :free-solo
       true :disabled
       (or (empty? (:country new-wine)) (empty? (:region new-wine))) :label
       "Appellation Tier" :tooltip (:appellation_tier common/field-descriptions)
       :options (sort common/appellation-tiers) :get-option-label
       (fn [option]
         (if-let [full-name (get common/appellation-tier-names option)]
           (str option " - " full-name)
           (str option)))]]
     [form-row
      [smart-select-field app-state [:new-wine :vineyard] :free-solo true
       :disabled (or (empty? (:country new-wine)) (empty? (:region new-wine)))
       :tooltip (:vineyard common/field-descriptions) :options
       (vineyards-for-region classifications
                             (:country new-wine)
                             (:region new-wine)) :label "Vineyard"]
      [smart-select-field app-state [:new-wine :classification] :free-solo true
       :disabled (or (empty? (:country new-wine)) (empty? (:region new-wine)))
       :tooltip (:classification common/field-descriptions) :options
       (classifications-for-appellation classifications
                                        (:country new-wine)
                                        (:region new-wine)
                                        (:appellation new-wine))]
      [smart-select-field app-state [:new-wine :designation] :free-solo true
       :disabled (or (empty? (:country new-wine)) (empty? (:region new-wine)))
       :tooltip (:designation common/field-descriptions) :options
       (designations-for-classification classifications
                                        (:country new-wine)
                                        (:region new-wine)
                                        (:appellation new-wine)
                                        (:classification new-wine)) :label
       "Designation" :helper-text
       (str "Must be one of: " (str/join ", " (sort common/wine-designations)))
       :on-blur
       #(when (and (:designation new-wine)
                   (seq (:designation new-wine))
                   (not (contains? common/wine-designations
                                   (:designation new-wine))))
          (swap! app-state assoc
            :error
            (str "Designation must be one of: "
                 (str/join ", " (sort common/wine-designations)))))]]
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
     [form-divider "Vintage"]
     [form-row [vintage app-state new-wine]
      [disgorgement-year app-state new-wine]]
     [form-row [dosage app-state new-wine]]
     [form-row
      (let [suggesting? (:suggesting-drinking-window? @app-state)
            ready? (and (:producer new-wine)
                        (:country new-wine)
                        (:region new-wine)
                        (:style new-wine))]
        [box {:sx {:mt 2 :display "flex" :flexDirection "column" :gap 1}}
         [box
          {:sx {:display "flex" :alignItems "center" :flexWrap "wrap" :gap 1}}
          [button
           {:variant "outlined"
            :color "secondary"
            :size "small"
            :disabled (or suggesting? (not ready?))
            :sx {"&.Mui-disabled" {:color "text.disabled"
                                   :borderColor "text.disabled"}}
            :startIcon (when-not suggesting? (r/as-element [auto-awesome]))
            :onClick
            (fn []
              (-> (api/suggest-drinking-window app-state new-wine)
                  (.then
                   (fn [result]
                     (let [window-reason (str "Drinking window suggested: "
                                              (:drink_from_year result)
                                              " to " (:drink_until_year result)
                                              " (" (:confidence result)
                                              " confidence)\n\n" (:reasoning
                                                                  result))]
                       (swap! app-state update
                         :new-wine
                         merge
                         {:drink_from_year (:drink_from_year result)
                          :drink_until_year (:drink_until_year result)
                          :tasting_window_commentary window-reason})
                       (swap! app-state assoc :window-reason window-reason))))
                  (.catch (fn [error]
                            (swap! app-state assoc
                              :error
                              (str "Failed to suggest drinking window: "
                                   error))))))}
           (if suggesting?
             [box {:sx {:display "flex" :alignItems "center"}}
              [circular-progress {:size 20 :sx {:mr 1}}] "Suggesting..."]
             "Suggest Drinking Window")]
          [provider-toggle-button app-state
           {:mobile-min-width "auto" :sx {:minWidth "auto" :px 1 :py 0.25}}]]
         [typography {:variant "body2" :sx {:mt 0.5}}
          (:window-reason @app-state)]])]
     [form-row [drink-from-year app-state new-wine]
      [drink-until-year app-state new-wine]]
     [form-row
      [uncontrolled-text-area-field
       {:label "Tasting Window Commentary"
        :rows 4
        :initial-value (:tasting_window_commentary new-wine)
        :input-ref window-commentary-ref}]]
     ;; Additional Information Section
     [form-divider "Additional Information"]
     [form-row
      [text-field
       {:label "Location"
        :required false
        :value (:location new-wine)
        :helper-text common/format-location-error
        :error (and (:location new-wine)
                    (not (common/valid-location? (:location new-wine))))
        :on-change #(swap! app-state assoc-in [:new-wine :location] %)}]
      [number-field
       {:label "Quantity"
        :required true
        :min 0
        :value (:quantity new-wine)
        :on-change
        #(swap! app-state assoc-in [:new-wine :quantity] (js/parseInt %))}]]
     [form-row
      [currency-field
       {:label "Price"
        :required false
        :value (if (string? (:price new-wine))
                 (:price new-wine)
                 (str (:price new-wine)))
        :on-change #(swap! app-state assoc-in [:new-wine :price] %)}]
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
       :on-cancel #(do (swap! app-state assoc
                         :show-wine-form? false
                         :new-wine {:bottle_format "Standard (750ml)"})
                       ;; Use replaceState to fix back button behavior
                       (.replaceState js/history nil "" "/"))}]]))
