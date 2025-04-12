(ns wine-cellar.views.wines.detail
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [wine-cellar.views.components :refer
             [editable-text-field editable-autocomplete-field
              editable-classification-field quantity-control]]
            [wine-cellar.views.components.image-upload :refer [image-upload]]
            [wine-cellar.views.tasting-notes.form :refer [tasting-note-form]]
            [wine-cellar.views.tasting-notes.list :refer [tasting-notes-list]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.vintage :as vintage]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.icons.arrow-back :refer [arrow-back]]
            [wine-cellar.common :as common]
            [wine-cellar.utils.formatting :refer [valid-name-producer?]]))

(defn editable-location
  [app-state wine]
  [editable-text-field
   {:value (:location wine),
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:location new-value})),
    :validate-fn (fn [value]
                   (when-not (common/valid-location? value)
                     common/format-location-error)),
    :text-field-props {:helperText common/format-location-error}}])

(defn editable-purveyor
  [app-state wine]
  [editable-text-field
   {:value (:purveyor wine),
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:purveyor new-value})),
    :empty-text "Not specified"}])

(defn editable-price
  [app-state wine]
  [editable-text-field
   {:value (when-let [price (:price wine)] (gstring/format "%.2f" price)),
    :on-save
      (fn [new-value]
        (let [parsed-price (js/parseFloat new-value)]
          (when-not (js/isNaN parsed-price)
            (api/update-wine app-state (:id wine) {:price parsed-price})))),
    :validate-fn (fn [value]
                   (let [parsed (js/parseFloat value)]
                     (cond (str/blank? value) "Price cannot be empty"
                           (js/isNaN parsed) "Price must be a valid number"
                           (< parsed 0) "Price cannot be negative"
                           :else nil))),
    :empty-text "Not specified",
    :text-field-props {:type "number", :InputProps {:startAdornment "$"}}}])

(defn editable-name
  [app-state wine]
  [editable-text-field
   {:value (:name wine),
    :on-save (fn [new-value]
               (let [updated-wine (assoc wine :name new-value)]
                 (if (valid-name-producer? updated-wine)
                   (api/update-wine app-state (:id wine) {:name new-value})
                   (js/alert
                     "Either Wine Name or Producer must be provided")))),
    :empty-text "Add wine name"}])

(defn editable-producer
  [app-state wine]
  [editable-text-field
   {:value (:producer wine),
    :on-save (fn [new-value]
               (let [updated-wine (assoc wine :producer new-value)]
                 (if (valid-name-producer? updated-wine)
                   (api/update-wine app-state (:id wine) {:producer new-value})
                   (js/alert
                     "Either Wine Name or Producer must be provided")))),
    :empty-text "Add producer"}])

(defn editable-vintage
  [app-state wine]
  [editable-text-field
   {:value (str (:vintage wine)),
    :on-save
      (fn [new-value]
        (let [parsed-vintage (js/parseInt new-value 10)]
          (when-not (vintage/valid-vintage? parsed-vintage)
            (api/update-wine app-state (:id wine) {:vintage parsed-vintage})))),
    :validate-fn (fn [value]
                   (let [parsed (js/parseInt value 10)]
                     (vintage/valid-vintage? parsed))),
    :empty-text "Add vintage",
    :text-field-props {:type "number"}}])

(defn editable-country
  [app-state wine]
  [editable-classification-field
   {:value (:country wine),
    :field-type :country,
    :app-state app-state,
    :wine wine,
    :classifications (:classifications @app-state),
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:country new-value})),
    :validate-fn (fn [value]
                   (when (str/blank? value) "Country cannot be empty")),
    :empty-text "Add country"}])

(defn editable-region
  [app-state wine]
  [editable-classification-field
   {:value (:region wine),
    :field-type :region,
    :app-state app-state,
    :wine wine,
    :classifications (:classifications @app-state),
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:region new-value})),
    :validate-fn (fn [value]
                   (when (str/blank? value) "Region cannot be empty")),
    :empty-text "Add region"}])

(defn editable-aoc
  [app-state wine]
  [editable-classification-field
   {:value (:aoc wine),
    :field-type :aoc,
    :app-state app-state,
    :wine wine,
    :classifications (:classifications @app-state),
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:aoc new-value})),
    :empty-text "Add AOC/AVA"}])

(defn editable-classification
  [app-state wine]
  [editable-classification-field
   {:value (:classification wine),
    :field-type :classification,
    :app-state app-state,
    :wine wine,
    :classifications (:classifications @app-state),
    :on-save
      (fn [new-value]
        (api/update-wine app-state (:id wine) {:classification new-value})),
    :empty-text "Add classification"}])

(defn editable-styles
  [app-state wine]
  [editable-autocomplete-field
   {:value (:style wine),
    :options (vec (sort common/wine-styles)),
    :free-solo false,
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:style new-value})),
    :validate-fn (fn [value]
                   (when (str/blank? value) "Style must be provided")),
    :empty-text "Add style"}])

(defn editable-drink-from-year
  [app-state wine]
  [editable-text-field
   {:value (when-let [year (:drink_from_year wine)] (str year)),
    :on-save
      (fn [new-value]
        (let [parsed-year (when-not (str/blank? new-value)
                            (js/parseInt new-value 10))
              drink-until-year (:drink_until_year wine)]
          ;; Check cross-field validation
          (if (and parsed-year
                   drink-until-year
                   (> parsed-year drink-until-year))
            (js/alert
              "Drink from year must be less than or equal to drink until year")
            (api/update-wine app-state
                             (:id wine)
                             {:drink_from_year parsed-year})))),
    :validate-fn
      (fn [value]
        (if (str/blank? value)
          nil ;; Allow empty value
          (let [parsed (js/parseInt value 10)
                drink-until-year (:drink_until_year wine)]
            (or
              (vintage/valid-tasting-year? parsed)
              (when (and parsed drink-until-year (> parsed drink-until-year))
                "Drink from year must be less than or equal to drink until year"))))),
    :empty-text "Add drink from year",
    :text-field-props {:type "number",
                       :helperText
                         "Year when the wine is/was ready to drink"}}])

(defn editable-drink-until-year
  [app-state wine]
  [editable-text-field
   {:value (when-let [year (:drink_until_year wine)] (str year)),
    :on-save
      (fn [new-value]
        (let [parsed-year (when-not (str/blank? new-value)
                            (js/parseInt new-value 10))
              drink-from-year (:drink_from_year wine)]
          ;; Check cross-field validation
          (if (and parsed-year drink-from-year (< parsed-year drink-from-year))
            (js/alert
              "Drink until year must be greater than or equal to drink from year")
            (api/update-wine app-state
                             (:id wine)
                             {:drink_until_year parsed-year})))),
    :validate-fn
      (fn [value]
        (if (str/blank? value)
          nil ;; Allow empty value
          (let [parsed (js/parseInt value 10)
                drink-from-year (:drink_from_year wine)]
            (or
              (vintage/valid-tasting-year? parsed)
              (when (and parsed drink-from-year (< parsed drink-from-year))
                "Drink until year must be greater than or equal to drink from year"))))),
    :empty-text "Add drink until year",
    :text-field-props {:type "number",
                       :helperText
                         "Year when the wine should be consumed by"}}])

(defn wine-detail
  [app-state wine]
  [paper
   {:elevation 2,
    :sx
      {:p 4,
       :mb 3,
       :borderRadius 2,
       :position "relative",
       :overflow "hidden",
       :backgroundImage
         "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))"}}
   ;; Wine title and basic info
   [box {:sx {:mb 3, :pb 2, :borderBottom "1px solid rgba(0,0,0,0.08)"}}
    [grid {:container true, :spacing 2}
     [grid {:item true, :xs 12}
      [typography {:variant "body2", :color "text.secondary"} "Producer"]
      [editable-producer app-state wine]]
     [grid {:item true, :xs 12}
      [typography {:variant "body2", :color "text.secondary"} "Wine Name"]
      [editable-name app-state wine]]]
    [grid {:container true, :spacing 2, :sx {:mt 1}}
     [grid {:item true, :xs 4}
      [typography {:variant "body2", :color "text.secondary"} "Vintage"]
      [editable-vintage app-state wine]]
     [grid {:item true, :xs 4}
      [typography {:variant "body2", :color "text.secondary"} "Country"]
      [editable-country app-state wine]]
     [grid {:item true, :xs 4}
      [typography {:variant "body2", :color "text.secondary"} "Region"]
      [editable-region app-state wine]]]]
   [grid {:container true, :spacing 3, :sx {:mb 4}}
    ;; Wine Label Image
    [grid {:item true, :xs 12, :md 6}
     [paper
      {:elevation 0,
       :sx {:p 2, :bgcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
      [typography {:variant "body2", :color "text.secondary"} "Wine Label"]
      [image-upload
       {:image-data (:label_image wine),
        :on-image-change #(api/update-wine-image app-state (:id wine) %),
        :on-image-remove #(api/update-wine-image app-state (:id wine) nil)}]]]
    ;; AOC/AVA
    [grid {:item true, :xs 12, :md 6}
     [paper
      {:elevation 0,
       :sx {:p 2, :bgcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
      [typography {:variant "body2", :color "text.secondary"} "AOC/AVA"]
      [editable-aoc app-state wine]]]
    ;; Classification
    [grid {:item true, :xs 12, :md 6}
     [paper
      {:elevation 0,
       :sx {:p 2, :bgcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
      [typography {:variant "body2", :color "text.secondary"} "Classification"]
      [editable-classification app-state wine]]]
    ;; Styles
    [grid {:item true, :xs 12, :md 6}
     [paper
      {:elevation 0,
       :sx {:p 2, :bgcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
      [typography {:variant "body2", :color "text.secondary"} "Style"]
      [editable-styles app-state wine]]]
    ;; Location
    [grid {:item true, :xs 12, :md 6}
     [paper
      {:elevation 0,
       :sx {:p 2, :bgcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
      [typography {:variant "body2", :color "text.secondary"} "Location"]
      [editable-location app-state wine]]]
    ;; Quantity
    [grid {:item true, :xs 12, :md 6}
     [paper
      {:elevation 0,
       :sx {:p 2, :bgcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
      [typography {:variant "body2", :color "text.secondary"} "Quantity"]
      [box {:display "flex", :alignItems "center", :mt 1}
       [quantity-control app-state (:id wine) (:quantity wine)]]]]
    ;; Price
    (when (:price wine)
      [grid {:item true, :xs 12, :md 6}
       [paper
        {:elevation 0,
         :sx {:p 2, :bgcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
        [typography {:variant "body2", :color "text.secondary"} "Price"]
        [editable-price app-state wine]]])
    ;; Purveyor
    [grid {:item true, :xs 12, :md 6}
     [paper
      {:elevation 0,
       :sx {:p 2, :bgcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
      [typography {:variant "body2", :color "text.secondary"} "Purchased From"]
      [editable-purveyor app-state wine]]]
    ;; Tasting Window
    [grid {:item true, :xs 12, :md 6}
     [paper
      {:elevation 0,
       :sx {:p 2, :btcolor "rgba(0,0,0,0.02)", :borderRadius 1}}
      [typography {:variant "body2", :color "text.secondary"} "Tasting Window"]
      [box {:sx {:display "flex", :flexDirection "column", :gap 1}}
       [box {:sx {:display "flex", :alignItems "center"}}
        [typography {:variant "body2", :color "text.secondary", :sx {:mr 1}}
         "From:"] [editable-drink-from-year app-state wine]]
       [box {:sx {:display "flex", :alignItems "center"}}
        [typography {:variant "body2", :color "text.secondary", :sx {:mr 1}}
         "Until:"] [editable-drink-until-year app-state wine]]
       (let [status (vintage/tasting-window-status wine)]
         [typography
          {:variant "body2",
           :color (vintage/tasting-window-color status),
           :sx {:mt 1, :fontStyle "italic"}}
          (vintage/format-tasting-window-text wine)])]]]
    ;; Tasting notes section
    [box {:sx {:mt 4}}
     [typography
      {:variant "h5",
       :component "h3",
       :sx {:mb 3,
            :pb 1,
            :borderBottom "1px solid rgba(0,0,0,0.08)",
            :color "primary.main"}} "Tasting Notes"]
     [tasting-notes-list app-state (:id wine)]
     [tasting-note-form app-state (:id wine)]]]])

(defn wine-details-section
  [app-state]
  (when-let [selected-wine-id (:selected-wine-id @app-state)]
    (when-let [selected-wine (first (filter #(= (:id %) selected-wine-id)
                                      (:wines @app-state)))]
      ;; No longer fetch wine details here - it should be done when
      ;; clicking the "View" button
      [box {:sx {:mb 3}} [wine-detail app-state selected-wine]
       [button
        {:variant "contained",
         :color "primary",
         :start-icon (r/as-element [arrow-back]),
         :sx {:mt 2},
         :onClick #(do
                     ;; Clean up the selected wine's image data when
                     ;; navigating away
                     (swap! app-state update
                       :wines
                       (fn [wines]
                         (map (fn [wine]
                                (if (= (:id wine) selected-wine-id)
                                  (dissoc wine :label_image)
                                  wine))
                           wines)))
                     ;; Remove selected wine ID and tasting notes
                     (swap! app-state dissoc :selected-wine-id :tasting-notes)
                     (swap! app-state assoc :new-tasting-note {}))}
        "Back to List"]])))
