(ns wine-cellar.views.wines.detail
  (:require
    [clojure.string :as str]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent-mui.icons.arrow-back :refer [arrow-back]]
    [reagent-mui.icons.auto-awesome :refer [auto-awesome]]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.button :refer [button]]
    [reagent-mui.material.circular-progress :refer [circular-progress]]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.paper :refer [paper]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent.core :as r]
    [wine-cellar.api :as api]
    [wine-cellar.common :as common]
    [wine-cellar.utils.formatting :refer [format-date-iso valid-name-producer?]]
    [wine-cellar.utils.vintage :as vintage]
    [wine-cellar.views.components :refer
     [editable-autocomplete-field editable-classification-field
      editable-text-field quantity-control]]
    [wine-cellar.views.components.image-upload :refer [image-upload]]
    [wine-cellar.views.tasting-notes.form :refer [tasting-note-form]]
    [wine-cellar.views.wines.varieties :refer [wine-varieties-component]]
    [wine-cellar.views.tasting-notes.list :refer [tasting-notes-list]]))

(defn editable-location
  [app-state wine]
  [editable-text-field
   {:value (:location wine)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:location new-value}))
    :validate-fn (fn [value]
                   (when-not (common/valid-location? value)
                     common/format-location-error))
    :text-field-props {:helperText common/format-location-error}}])

(defn editable-purveyor
  [app-state wine]
  [editable-text-field
   {:value (:purveyor wine)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:purveyor new-value}))
    :empty-text "Not specified"}])

(defn editable-purchase-date
  [app-state wine]
  [editable-text-field
   {:value (format-date-iso (:purchase_date wine))
    :on-save
    (fn [new-value]
      (api/update-wine app-state (:id wine) {:purchase_date new-value}))
    :empty-text "Add purchase date"
    :text-field-props {:type "date"}}])

(defn editable-price
  [app-state wine]
  [editable-text-field
   {:value (when-let [price (:price wine)] (gstring/format "%.2f" price))
    :on-save
    (fn [new-value]
      (let [parsed-price (js/parseFloat new-value)]
        (when-not (js/isNaN parsed-price)
          (api/update-wine app-state (:id wine) {:price parsed-price}))))
    :validate-fn (fn [value]
                   (let [parsed (js/parseFloat value)]
                     (cond (str/blank? value) "Price cannot be empty"
                           (js/isNaN parsed) "Price must be a valid number"
                           (< parsed 0) "Price cannot be negative"
                           :else nil)))
    :empty-text "Not specified"
    :text-field-props {:type "number" :InputProps {:startAdornment "$"}}}])

(defn editable-alcohol-percentage
  [app-state wine]
  [editable-text-field
   {:value (when-let [percentage (:alcohol_percentage wine)]
             (gstring/format "%.1f" percentage))
    :on-save (fn [new-value]
               (let [parsed-percentage (js/parseFloat new-value)]
                 (when-not (js/isNaN parsed-percentage)
                   (api/update-wine app-state
                                    (:id wine)
                                    {:alcohol_percentage parsed-percentage}))))
    :validate-fn (fn [value]
                   (let [parsed (js/parseFloat value)]
                     (cond (str/blank? value) nil ;; Allow empty value
                           (js/isNaN parsed)
                           "Alcohol percentage must be a valid number"
                           (< parsed 0) "Alcohol percentage cannot be negative"
                           (> parsed 100) "Alcohol percentage cannot exceed 100"
                           :else nil)))
    :empty-text "Add alcohol percentage"
    :text-field-props {:type "number"
                       :step "0.1"
                       :InputProps {:endAdornment "%"}
                       :helperText "e.g., 13.5 for 13.5% ABV"}}])

(defn editable-name
  [app-state wine]
  [editable-text-field
   {:value (:name wine)
    :on-save (fn [new-value]
               (let [updated-wine (assoc wine :name new-value)]
                 (if (valid-name-producer? updated-wine)
                   (api/update-wine app-state (:id wine) {:name new-value})
                   (js/alert "Either Wine Name or Producer must be provided"))))
    :empty-text "Add wine name"}])

(defn editable-producer
  [app-state wine]
  [editable-text-field
   {:value (:producer wine)
    :on-save (fn [new-value]
               (let [updated-wine (assoc wine :producer new-value)]
                 (if (valid-name-producer? updated-wine)
                   (api/update-wine app-state (:id wine) {:producer new-value})
                   (js/alert "Either Wine Name or Producer must be provided"))))
    :empty-text "Add producer"}])

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
    :empty-text "Add vintage"}])

(defn editable-country
  [app-state wine]
  [editable-classification-field
   {:value (:country wine)
    :field-type :country
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:country new-value}))
    :validate-fn (fn [value]
                   (when (str/blank? value) "Country cannot be empty"))
    :empty-text "Add country"}])

(defn editable-region
  [app-state wine]
  [editable-classification-field
   {:value (:region wine)
    :field-type :region
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:region new-value}))
    :validate-fn (fn [value] (when (str/blank? value) "Region cannot be empty"))
    :empty-text "Add region"}])

(defn editable-aoc
  [app-state wine]
  [editable-classification-field
   {:value (:aoc wine)
    :field-type :aoc
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:aoc new-value}))
    :empty-text "Add AOC/AVA"}])

(defn editable-vineyard
  [app-state wine]
  [editable-classification-field
   {:value (:vineyard wine)
    :field-type :vineyard
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:vineyard new-value}))
    :empty-text "Add vineyard"}])

(defn editable-classification
  [app-state wine]
  [editable-classification-field
   {:value (:classification wine)
    :field-type :classification
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save
    (fn [new-value]
      (api/update-wine app-state (:id wine) {:classification new-value}))
    :empty-text "Add classification"}])

(defn editable-styles
  [app-state wine]
  [editable-autocomplete-field
   {:value (:style wine)
    :options (vec (sort common/wine-styles))
    :free-solo false
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:style new-value}))
    :validate-fn (fn [value] (when (str/blank? value) "Style must be provided"))
    :empty-text "Add style"}])

(defn editable-drink-from-year
  [app-state wine]
  [editable-text-field
   {:value (when-let [year (:drink_from_year wine)] (str year))
    :on-save (fn [new-value]
               (let [parsed-year (when-not (str/blank? new-value)
                                   (js/parseInt new-value 10))
                     drink-until-year (:drink_until_year wine)]
                 ;; Check cross-field validation
                 (if-let [window-error (vintage/valid-tasting-window?
                                        parsed-year
                                        drink-until-year)]
                   (js/alert window-error)
                   (api/update-wine app-state
                                    (:id wine)
                                    {:drink_from_year parsed-year}))))
    :validate-fn (fn [value]
                   (if (str/blank? value)
                     nil ;; Allow empty value
                     (let [parsed (js/parseInt value 10)
                           drink-until-year (:drink_until_year wine)]
                       (vintage/valid-tasting-window? parsed
                                                      drink-until-year))))
    :empty-text "Add drink from year"
    :text-field-props
    {:type "number" :helperText "Year when the wine is/was ready to drink"}}])

(defn editable-drink-until-year
  [app-state wine]
  [editable-text-field
   {:value (when-let [year (:drink_until_year wine)] (str year))
    :on-save (fn [new-value]
               (let [parsed-year (when-not (str/blank? new-value)
                                   (js/parseInt new-value 10))
                     drink-from-year (:drink_from_year wine)]
                 ;; Check cross-field validation
                 (if-let [window-error (vintage/valid-tasting-window?
                                        drink-from-year
                                        parsed-year)]
                   (js/alert window-error)
                   (api/update-wine app-state
                                    (:id wine)
                                    {:drink_until_year parsed-year}))))
    :validate-fn (fn [value]
                   (if (str/blank? value)
                     nil ;; Allow empty value
                     (let [parsed (js/parseInt value 10)
                           drink-from-year (:drink_from_year wine)]
                       (vintage/valid-tasting-window? drink-from-year parsed))))
    :empty-text "Add drink until year"
    :text-field-props
    {:type "number" :helperText "Year when the wine should be consumed by"}}])

(defn wine-detail
  [app-state wine]
  [paper
   {:elevation 2
    :sx
    {:p 4
     :mb 3
     :borderRadius 2
     :position "relative"
     :overflow "hidden"
     :backgroundImage
     "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))"}}
   ;; Wine title and basic info
   [box {:sx {:mb 3 :pb 2 :borderBottom "1px solid rgba(0,0,0,0.08)"}}
    [grid {:container true :spacing 2}
     [grid {:item true :xs 12}
      [typography {:variant "body2" :color "text.secondary"} "Producer"]
      [editable-producer app-state wine]]
     [grid {:item true :xs 12}
      [typography {:variant "body2" :color "text.secondary"} "Wine Name"]
      [editable-name app-state wine]]]
    [grid {:container true :spacing 2 :sx {:mt 1}}
     [grid {:item true :xs 4}
      [typography {:variant "body2" :color "text.secondary"} "Vintage"]
      [editable-vintage app-state wine]]
     [grid {:item true :xs 4}
      [typography {:variant "body2" :color "text.secondary"} "Country"]
      [editable-country app-state wine]]
     [grid {:item true :xs 4}
      [typography {:variant "body2" :color "text.secondary"} "Region"]
      [editable-region app-state wine]]]]
   [grid {:container true :spacing 3 :sx {:mb 4}}
    ;; Front Wine Label Image
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Front Label"]
      [image-upload
       {:image-data (:label_image wine)
        :label-type "front"
        :on-image-change #(api/update-wine-image app-state (:id wine) %)
        :on-image-remove
        #(api/update-wine-image
          app-state
          (:id wine)
          (assoc wine :label_image nil :label_thumbnail nil))}]]]
    ;; Back Wine Label Image
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Back Label"]
      [image-upload
       {:image-data (:back_label_image wine)
        :label-type "back"
        :on-image-change #(api/update-wine-image app-state (:id wine) %)
        :on-image-remove #(api/update-wine-image
                           app-state
                           (:id wine)
                           (assoc wine :back_label_image nil))}]]]
    ;; AOC/AVA
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "AOC/AVA"]
      [editable-aoc app-state wine]]]
    ;; Vineyard
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Vineyard"]
      [editable-vineyard app-state wine]]]
    ;; Classification
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Classification"]
      [editable-classification app-state wine]]]
    ;; Styles
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Style"]
      [editable-styles app-state wine]]]
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Grape Varieties"]
      [wine-varieties-component app-state (:id wine)]]]
    ;; Location
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Location"]
      [editable-location app-state wine]]]
    ;; Quantity
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Quantity"]
      [box {:display "flex" :alignItems "center" :mt 1}
       [quantity-control app-state (:id wine) (:quantity wine)]]]]
    ;; Price
    (when (:price wine)
      [grid {:item true :xs 12 :md 6}
       [paper
        {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
        [typography {:variant "body2" :color "text.secondary"} "Price"]
        [editable-price app-state wine]]])
    ;; Alcohol Percentage
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"}
       "Alcohol Percentage"] [editable-alcohol-percentage app-state wine]]]
    ;; Purveyor
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Purchased From"]
      [editable-purveyor app-state wine]]]
    ;; Purchase Date
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Purchase Date"]
      [editable-purchase-date app-state wine]]]
    ;; Tasting Window
    [grid {:item true :xs 12 :md 6}
     [paper
      {:elevation 0 :sx {:p 2 :btcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
      [typography {:variant "body2" :color "text.secondary"} "Tasting Window"]
      [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
       [box {:sx {:display "flex" :alignItems "center"}}
        [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}}
         "From:"] [editable-drink-from-year app-state wine]]
       [box {:sx {:display "flex" :alignItems "center"}}
        [typography {:variant "body2" :color "text.secondary" :sx {:mr 1}}
         "Until:"] [editable-drink-until-year app-state wine]]
       (let [status (vintage/tasting-window-status wine)]
         [typography
          {:variant "body2"
           :color (vintage/tasting-window-color status)
           :sx {:mt 1 :fontStyle "italic"}}
          (vintage/format-tasting-window-text wine)])
       [box {:sx {:mt 2}}
        [button
         {:variant "outlined"
          :color "secondary"
          :size "small"
          :disabled (:suggesting-drinking-window? @app-state)
          :startIcon (r/as-element [auto-awesome])
          :onClick (fn []
                     (-> (api/suggest-drinking-window app-state wine)
                         (.then (fn [{:keys [drink_from_year drink_until_year
                                             confidence reasoning]
                                      :as suggestion}]
                                  ;; Update the wine with the suggested
                                  ;; drinking window
                                  (swap! app-state assoc
                                    :window-suggestion
                                    (assoc suggestion
                                           :message
                                           (str "Drinking window suggested: "
                                                drink_from_year
                                                " to " drink_until_year
                                                " (" confidence
                                                " confidence)\n\n"
                                                reasoning)))))
                         (.catch (fn [error]
                                   (swap! app-state assoc
                                     :error
                                     (str "Failed to suggest drinking window: "
                                          error))))))}
         (if (:suggesting-drinking-window? @app-state)
           [box {:sx {:display "flex" :alignItems "center"}}
            [circular-progress {:size 20 :sx {:mr 1}}] "Suggesting..."]
           "Suggest Drinking Window")]
        [typography {:variant "body2" :sx {:mt 1}}
         (get-in @app-state [:window-suggestion :message])]
        ;; Buttons to apply the suggestion
        (when-let [suggestion (get @app-state :window-suggestion)]
          [box {:sx {:mt 2 :display "flex" :gap 1 :flexWrap "wrap"}}
           [button
            {:variant "contained"
             :color "secondary"
             :size "small"
             :onClick (fn []
                        (let [{:keys [drink_from_year drink_until_year]}
                              suggestion]
                          (api/update-wine app-state
                                           (:id wine)
                                           {:drink_from_year drink_from_year
                                            :drink_until_year drink_until_year})
                          ;; Clear the suggestion after applying
                          (swap! app-state dissoc :window-suggestion)))}
            "Apply Suggestion"]
           [button
            {:variant "outlined"
             :color "secondary"
             :size "small"
             :onClick (fn []
                        (let [{:keys [drink_from_year]} suggestion]
                          (api/update-wine app-state
                                           (:id wine)
                                           {:drink_from_year drink_from_year})
                          ;; Keep the suggestion in case they want to apply
                          ;; the until year later
                        ))} "Apply From Year"]
           [button
            {:variant "outlined"
             :color "secondary"
             :size "small"
             :onClick (fn []
                        (let [{:keys [drink_until_year]} suggestion]
                          (api/update-wine app-state
                                           (:id wine)
                                           {:drink_until_year drink_until_year})
                          ;; Keep the suggestion in case they want to apply
                          ;; the from year later
                        ))} "Apply Until Year"]
           [button
            {:variant "text"
             :color "secondary"
             :size "small"
             :onClick (fn []
                        ;; Clear the suggestion without applying
                        (swap! app-state dissoc :window-suggestion))}
            "Dismiss"]])]]]]
    ;; Tasting notes section
    [box {:sx {:mt 4}}
     [typography
      {:variant "h5"
       :component "h3"
       :sx {:mb 3
            :pb 1
            :borderBottom "1px solid rgba(0,0,0,0.08)"
            :color "primary.main"}} "Tasting Notes"]
     [tasting-notes-list app-state (:id wine)]
     (when-not (:editing-note-id @app-state)
       [tasting-note-form app-state (:id wine)])]]])

(defn wine-details-section
  [app-state]
  (when-let [selected-wine-id (:selected-wine-id @app-state)]
    (when-let [selected-wine (first (filter #(= (:id %) selected-wine-id)
                                            (:wines @app-state)))]
      ;; No longer fetch wine details here - it should be done when
      ;; clicking the "View" button
      [box {:sx {:mb 3}} [wine-detail app-state selected-wine]
       [button
        {:variant "contained"
         :color "primary"
         :start-icon (r/as-element [arrow-back])
         :sx {:mt 2}
         :onClick #(do
                     ;; Clean up the selected wine's image data when
                     ;; navigating away
                     (swap! app-state update
                       :wines
                       (fn [wines]
                         (map (fn [wine]
                                (if (= (:id wine) selected-wine-id)
                                  (-> wine
                                      (dissoc :label_image)
                                      (dissoc :back_label_image))
                                  wine))
                              wines)))
                     ;; Remove selected wine ID and tasting notes
                     (swap! app-state dissoc
                       :selected-wine-id :tasting-notes
                       :editing-note-id :window-suggestion)
                     (swap! app-state assoc :new-tasting-note {}))}
        "Back to List"]])))
