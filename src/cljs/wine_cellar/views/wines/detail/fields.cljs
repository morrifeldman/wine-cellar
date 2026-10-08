(ns wine-cellar.views.wines.detail.fields
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent-mui.material.box :refer [box]]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]
            [wine-cellar.utils.formatting :refer [valid-name-producer?]]
            [wine-cellar.theme :as theme]
            [wine-cellar.utils.vintage :as vintage]
            [wine-cellar.views.components :refer
             [editable-autocomplete-field editable-classification-field
              editable-text-field]]))

(defn- range-validator
  "Blank passes; anything else must parse and sit within lo..hi."
  [label parser lo hi]
  (fn [value]
    (let [v (str/trim (or value ""))]
      (when-not (str/blank? v)
        (let [n (parser v)]
          (cond (js/isNaN n) (str label " must be a number")
                (< n lo) (str label " must be at least " lo)
                (> n hi) (str label " must be at most " hi)))))))

(defn- numeric-editor
  [app-state wine field
   {:keys [label lo hi display-fn parser round? allow-blank? format-fn
           empty-text text-field-props]
    :or {parser js/parseFloat}}]
  [editable-text-field
   {:value (when-let [v (get wine field)]
             (if display-fn (display-fn v) (str v)))
    :on-save (fn [new-value]
               (let [trimmed (str/trim (or new-value ""))]
                 (if (str/blank? trimmed)
                   (when allow-blank?
                     (api/update-wine app-state (:id wine) {field nil}))
                   (let [parsed (parser trimmed)]
                     (when-not (js/isNaN parsed)
                       (api/update-wine
                        app-state
                        (:id wine)
                        {field (if round? (js/Math.round parsed) parsed)}))))))
    :validate-fn (range-validator label parser lo hi)
    :format-fn format-fn
    :empty-text empty-text
    :compact? true
    :inline? true
    :text-field-props text-field-props}])

(defn editable-alcohol-percentage
  [app-state wine]
  [numeric-editor app-state wine :alcohol_percentage
   {:label "Alcohol percentage"
    :lo 0
    :hi 100
    :display-fn #(gstring/format "%.1f" %)
    :format-fn #(str % "% ABV")
    :empty-text "Add ABV"
    :text-field-props {:type "number"
                       :step "0.1"
                       :InputProps {:endAdornment "%"}
                       :helperText "e.g., 13.5 for 13.5% ABV"}}])

(defn editable-dosage
  [app-state wine]
  [numeric-editor app-state wine :dosage
   {:label "Dosage"
    :lo 0
    :hi 200
    :display-fn #(str (js/Math.round %))
    :round? true
    :allow-blank? true
    :format-fn #(str "Dosage " % " g/L")
    :empty-text "Add dosage"
    :text-field-props
    {:type "number" :step "1" :InputProps {:endAdornment "g/L"}}}])

(defn editable-disgorgement-year
  [app-state wine]
  [numeric-editor app-state wine :disgorgement_year
   {:label "Year"
    :lo 1900
    :hi (.getFullYear (js/Date.))
    :parser #(js/parseInt % 10)
    :allow-blank? true
    :format-fn #(str "Disgorged in " %)
    :empty-text "Add disgorgement year"
    :text-field-props
    {:type "number"
     :helperText "Year when the wine was disgorged (for sparkling wines)"}}])

(defn editable-tasting-window-commentary
  [app-state wine]
  [editable-text-field
   {:value (:tasting_window_commentary wine)
    :on-save (fn [new-value]
               (api/update-wine app-state
                                (:id wine)
                                {:tasting_window_commentary new-value}))
    :empty-text "Add tasting window commentary"
    :text-field-props {:multiline true
                       :rows 4
                       :helperText "Commentary about the drinking window"}}])

(defn editable-ai-summary
  [app-state wine]
  (let [force-edit-key (get-in @app-state [:force-edit-ai-summary (:id wine)])]
    ^{:key (str "ai-summary-" (:id wine)
                "-" (if force-edit-key (.getTime (js/Date.)) "view"))}
    [editable-text-field
     {:value (:ai_summary wine)
      :force-edit-mode? (boolean force-edit-key)
      :on-save
      (fn [new-value]
        ;; Clear the force edit mode when saving
        (swap! app-state update :force-edit-ai-summary dissoc (:id wine))
        (api/update-wine app-state (:id wine) {:ai_summary new-value}))
      :on-cancel
      (fn []
        ;; Clear the force edit mode when canceling
        (swap! app-state update :force-edit-ai-summary dissoc (:id wine)))
      :empty-text "Add wine summary"
      :text-field-props
      {:multiline true
       :rows 4
       :helperText
       "AI-generated wine profile, taste notes, and food pairings"}}]))

;; Name and producer may each be blank, but not both.
(defn- identity-editor
  [app-state wine field opts]
  [editable-text-field
   (merge {:value (get wine field)
           :on-save (fn [new-value]
                      (if (valid-name-producer? (assoc wine field new-value))
                        (api/update-wine app-state (:id wine) {field new-value})
                        (swap! app-state assoc
                          :error
                          "Either Wine Name or Producer must be provided")))
           :inline? true}
          opts)])

(defn editable-name
  [app-state wine]
  [identity-editor app-state wine :name
   {:empty-text "Add wine name" :display-sx theme/detail-subtitle}])

(defn editable-producer
  [app-state wine]
  [identity-editor app-state wine :producer
   {:empty-text "Add producer" :display-sx theme/detail-title}])

(defn editable-vintage
  [app-state wine]
  [editable-autocomplete-field
   {:value (if (:vintage wine) (str (:vintage wine)) "NV")
    :options (concat ["NV"] (vintage/default-vintage-years))
    :free-solo true
    :on-save
    (fn [new-value]
      (let [vintage-value (cond (empty? new-value) nil
                                (= new-value "NV") nil
                                :else (js/parseInt new-value 10))]
        (api/update-wine app-state (:id wine) {:vintage vintage-value})))
    :validate-fn (fn [value]
                   (cond (empty? value) nil
                         (= value "NV") nil
                         :else (let [parsed (js/parseInt value 10)]
                                 (vintage/valid-vintage? parsed))))
    :empty-text "Add vintage"
    :compact? true
    :inline? true
    :display-sx {:fontFamily theme/serif
                 :fontSize "3rem"
                 :fontWeight 700
                 :lineHeight 1
                 :fontVariantNumeric "lining-nums"
                 :color "primary.light"}}])

(defn- autocomplete-editor
  [app-state wine field {:keys [options empty-text validate-fn free-solo?]}]
  [editable-autocomplete-field
   {:value (get wine field)
    :tooltip (get common/field-descriptions field)
    :options options
    :free-solo (boolean free-solo?)
    :on-save #(api/update-wine app-state (:id wine) {field %})
    :validate-fn validate-fn
    :empty-text empty-text
    :compact? true
    :inline? true}])

(defn editable-designation
  [app-state wine]
  [autocomplete-editor app-state wine :designation
   {:options (vec (sort common/wine-designations))
    :empty-text "Add designation"}])

(defn- classification-editor
  [app-state wine field {:keys [empty-text validate-fn compact?]}]
  [editable-classification-field
   {:value (get wine field)
    :field-type field
    :tooltip (get common/field-descriptions field)
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save #(api/update-wine app-state (:id wine) {field %})
    :validate-fn validate-fn
    :empty-text empty-text
    :compact? compact?
    :inline? true}])

(defn editable-country
  [app-state wine]
  [classification-editor app-state wine :country
   {:empty-text "Add country"
    :validate-fn #(when (str/blank? %) "Country cannot be empty")}])

(defn editable-region
  [app-state wine]
  [classification-editor app-state wine :region
   {:empty-text "Add region"
    :validate-fn #(when (str/blank? %) "Region cannot be empty")}])

(defn editable-appellation
  [app-state wine]
  [classification-editor app-state wine :appellation
   {:empty-text "Add Appellation"}])

(defn editable-appellation-tier
  [app-state wine]
  [editable-autocomplete-field
   {:value (:appellation_tier wine)
    :free-solo true
    :tooltip (:appellation_tier common/field-descriptions)
    :options (sort common/appellation-tiers)
    :option-label (fn [option]
                    (if-let [full-name (get common/appellation-tier-names
                                            option)]
                      (str option " - " full-name)
                      (str option)))
    :on-save
    (fn [new-value]
      (api/update-wine app-state (:id wine) {:appellation_tier new-value}))
    :empty-text "Add Tier"
    :compact? true
    :inline? true}])

(defn editable-vineyard
  [app-state wine]
  [classification-editor app-state wine :vineyard
   {:empty-text "Add vineyard" :compact? true}])

(defn editable-classification
  [app-state wine]
  [classification-editor app-state wine :classification
   {:empty-text "Add classification" :compact? true}])

(defn editable-styles
  [app-state wine]
  [autocomplete-editor app-state wine :style
   {:options (vec (sort common/wine-styles))
    :empty-text "Add style"
    :validate-fn #(when (str/blank? %) "Style must be provided")}])

(defn editable-closure-type
  [app-state wine]
  [autocomplete-editor app-state wine :closure_type
   {:options common/closure-type-options :empty-text "Select closure type"}])

(defn editable-bottle-format
  [app-state wine]
  [autocomplete-editor app-state wine :bottle_format
   {:options common/bottle-formats :empty-text "Select format"}])



(defn wine-identity-section
  [app-state wine]
  [box {:sx {:mt 3 :mb 1 :display "flex" :flexDirection "column" :gap 0.5}}
   [editable-vintage app-state wine] [editable-producer app-state wine]
   [editable-name app-state wine]])
