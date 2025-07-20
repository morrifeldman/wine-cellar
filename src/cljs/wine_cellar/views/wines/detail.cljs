(ns wine-cellar.views.wines.detail
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! go]]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent-mui.icons.arrow-back :refer [arrow-back]]
            [reagent-mui.icons.auto-awesome :refer [auto-awesome]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.modal :refer [modal]]
            [reagent-mui.material.backdrop :refer [backdrop]]
            [reagent-mui.icons.close :refer [close]]
            [reagent.core :as r]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]
            [wine-cellar.utils.formatting :refer
             [format-date-iso valid-name-producer?]]
            [wine-cellar.utils.vintage :as vintage]
            [wine-cellar.views.components :refer
             [editable-autocomplete-field editable-classification-field
              editable-text-field quantity-control]]
            [wine-cellar.views.components.image-upload :refer [image-upload]]
            [wine-cellar.views.tasting-notes.form :refer [tasting-note-form]]
            [wine-cellar.views.wines.varieties :refer [wine-varieties-list]]
            [wine-cellar.views.tasting-notes.list :refer [tasting-notes-list]]))

(defn editable-location
  [app-state wine]
  [editable-text-field
   {:value (:location wine)
    :on-save
    (fn [new-value]
      (let [clean-value (if (str/blank? new-value) nil new-value)]
        (api/update-wine app-state (:id wine) {:location clean-value})))
    :validate-fn (fn [value]
                   (cond (str/blank? value) nil ; Allow empty/clearing
                         (not (common/valid-location? value))
                         common/format-location-error
                         :else nil))
    :empty-text "Not specified"
    :compact? true
    :text-field-props {:helperText common/format-location-error}}])

(defn editable-purveyor
  [app-state wine]
  (let [all-wines (:wines @app-state)
        existing-purveyors (->> all-wines
                                (map :purveyor)
                                (filter #(and % (not (str/blank? %))))
                                (distinct)
                                (sort)
                                (vec))]
    [editable-autocomplete-field
     {:value (:purveyor wine)
      :options existing-purveyors
      :free-solo true
      :on-save (fn [new-value]
                 (api/update-wine app-state (:id wine) {:purveyor new-value}))
      :empty-text "Not specified"
      :compact? true}]))

(defn editable-purchase-date
  [app-state wine]
  [editable-text-field
   {:value (format-date-iso (:purchase_date wine))
    :on-save
    (fn [new-value]
      (api/update-wine app-state (:id wine) {:purchase_date new-value}))
    :empty-text "Add purchase date"
    :compact? true
    :text-field-props {:type "date"}}])

(defn editable-price
  [app-state wine]
  [editable-text-field
   {:value (when-let [price (:price wine)] (gstring/format "%.2f" price))
    :on-save
    (fn [new-value]
      (if (str/blank? new-value)
        ;; Clear the price when empty
        (api/update-wine app-state (:id wine) {:price nil})
        ;; Save the parsed price when not empty
        (let [parsed-price (js/parseFloat new-value)]
          (when-not (js/isNaN parsed-price)
            (api/update-wine app-state (:id wine) {:price parsed-price})))))
    :validate-fn (fn [value]
                   (let [parsed (js/parseFloat value)]
                     (cond (str/blank? value) nil ;; Allow empty value
                           (js/isNaN parsed) "Price must be a valid number"
                           (< parsed 0) "Price cannot be negative"
                           :else nil)))
    :empty-text "Not specified"
    :compact? true
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
    :empty-text "Add ABV"
    :compact? true
    :text-field-props {:type "number"
                       :step "0.1"
                       :InputProps {:endAdornment "%"}
                       :helperText "e.g., 13.5 for 13.5% ABV"}}])

(defn editable-disgorgement-year
  [app-state wine]
  [editable-text-field
   {:value (when-let [year (:disgorgement_year wine)] (str year))
    :on-save (fn [new-value]
               (let [parsed-year (when-not (str/blank? new-value)
                                   (js/parseInt new-value 10))]
                 (api/update-wine app-state
                                  (:id wine)
                                  {:disgorgement_year parsed-year})))
    :validate-fn (fn [value]
                   (if (str/blank? value)
                     nil ;; Allow empty value
                     (let [parsed (js/parseInt value 10)]
                       (cond (js/isNaN parsed) "Year must be a valid number"
                             (< parsed 1900) "Year must be 1900 or later"
                             (> parsed (.getFullYear (js/Date.)))
                             "Year cannot be in the future"
                             :else nil))))
    :empty-text "Add disgorgement year"
    :compact? true
    :text-field-props
    {:type "number"
     :helperText "Year when the wine was disgorged (for sparkling wines)"}}])

(defn editable-original-quantity
  [app-state wine]
  [editable-text-field
   {:value (when-let [qty (:original_quantity wine)] (str qty))
    :on-save
    (fn [new-value]
      (let [parsed-qty
            (if (str/blank? new-value) nil (js/parseInt new-value 10))
            current-qty (:quantity wine)]
        (cond
          ;; Allow clearing (nil)
          (nil? parsed-qty)
          (api/update-wine app-state (:id wine) {:original_quantity nil})
          ;; Check if valid and not less than current quantity
          (and (not (js/isNaN parsed-qty))
               (> parsed-qty 0)
               (>= parsed-qty current-qty))
          (api/update-wine app-state (:id wine) {:original_quantity parsed-qty})
          ;; Error case - show alert
          (< parsed-qty current-qty)
          (js/alert (str "Original quantity ("
                         parsed-qty
                         ") cannot be less than current quantity ("
                         current-qty
                         ")"))
          ;; Other validation errors will be caught by validate-fn
          :else nil)))
    :validate-fn (fn [value]
                   (cond (str/blank? value) nil ;; Allow empty value
                         :else
                         (let [parsed (js/parseInt value 10)
                               current-qty (:quantity wine)]
                           (cond (js/isNaN parsed) "Must be a valid number"
                                 (< parsed 1) "Must be at least 1"
                                 (< parsed current-qty)
                                 (str "Can't have originally bought " parsed
                                      " when you currently have " current-qty)
                                 :else nil))))
    :empty-text "Not specified"
    :compact? true
    :text-field-props {:type "number"}}])

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
      :empty-text "Generate AI wine summary"
      :text-field-props
      {:multiline true
       :rows 4
       :helperText
       "AI-generated wine profile, taste notes, and food pairings"}}]))

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
    :empty-text "Add vintage"
    :compact? true}])

(defn editable-level
  [app-state wine]
  [editable-autocomplete-field
   {:value (:level wine)
    :options (vec (sort common/wine-levels))
    :free-solo false
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:level new-value}))
    :empty-text "Add level"
    :compact? true}])

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
    :empty-text "Add vineyard"
    :compact? true}])

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
    :empty-text "Add classification"
    :compact? true}])

(defn editable-styles
  [app-state wine]
  [editable-autocomplete-field
   {:value (:style wine)
    :options (vec (sort common/wine-styles))
    :free-solo false
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:style new-value}))
    :validate-fn (fn [value] (when (str/blank? value) "Style must be provided"))
    :empty-text "Add style"
    :compact? true}])

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

(defn wine-identity-section
  [app-state wine]
  [box {:sx {:mb 3 :pb 2 :borderBottom "1px solid rgba(0,0,0,0.08)"}}
   ;; Producer + Name
   [grid {:container true :spacing 2}
    [grid {:item true :xs 12 :sm 6}
     [typography {:variant "body2" :color "text.secondary"} "Producer"]
     [editable-producer app-state wine]]
    [grid {:item true :xs 12 :sm 6}
     [typography {:variant "body2" :color "text.secondary"} "Wine Name"]
     [editable-name app-state wine]]]
   ;; Country + Region + AOC
   [grid {:container true :spacing 1 :sx {:mt 2}}
    [grid {:item true :xs 4 :sm 4}
     [typography {:variant "body2" :color "text.secondary"} "Country"]
     [editable-country app-state wine]]
    [grid {:item true :xs 4 :sm 4}
     [typography {:variant "body2" :color "text.secondary"} "Region"]
     [editable-region app-state wine]]
    [grid {:item true :xs 4 :sm 4}
     [typography {:variant "body2" :color "text.secondary"} "AOC/AVA"]
     [editable-aoc app-state wine]]]])

(defn image-zoom-modal
  [app-state image-data image-title]
  [modal
   {:open (boolean (get @app-state :zoomed-image))
    :onClose #(swap! app-state dissoc :zoomed-image)
    :closeAfterTransition true}
   [backdrop
    {:sx {:color "white"} :open (boolean (get @app-state :zoomed-image))}
    [box
     {:sx {:position "absolute"
           :top "50%"
           :left "50%"
           :transform "translate(-50%, -50%)"
           :width "90vw"
           :height "90vh"
           :bgcolor "background.paper"
           :borderRadius 2
           :boxShadow 24
           :p 2
           :display "flex"
           :flexDirection "column"
           :outline "none"}}
     ;; Header with title and close button
     [box
      {:sx {:display "flex"
            :justifyContent "space-between"
            :alignItems "center"
            :mb 2}} [typography {:variant "h6"} image-title]
      [button
       {:onClick #(swap! app-state dissoc :zoomed-image)
        :sx {:minWidth "auto" :p 1}} [close]]]
     ;; Image container
     [box
      {:sx {:flex 1
            :display "flex"
            :justifyContent "center"
            :alignItems "center"
            :overflow "auto"}}
      [box
       {:component "img"
        :src image-data
        :sx {:maxWidth "100%"
             :maxHeight "100%"
             :objectFit "contain"
             :borderRadius 1}}]]]]])

(defn clickable-wine-image
  [image-data label-type title on-image-change on-image-remove app-state]
  [box {:sx {:position "relative"}}
   [image-upload
    {:image-data image-data
     :label-type label-type
     :on-image-change on-image-change
     :on-image-remove on-image-remove}]
   ;; Click overlay for zoom (only when image exists)
   (when image-data
     [box
      {:sx {:position "absolute"
            :top 0
            :left 0
            :right 0
            :height "300px"
            :cursor "zoom-in"
            :display "flex"
            :alignItems "center"
            :justifyContent "center"
            :bgcolor "rgba(0,0,0,0)"
            :transition "background-color 0.2s"
            :pointerEvents "auto"
            ":hover" {:bgcolor "rgba(0,0,0,0.1)"}}
       :onClick #(do (.stopPropagation %)
                     (swap! app-state assoc
                       :zoomed-image
                       {:data image-data :title title}))}
      [box
       {:sx {:opacity 0
             :transition "opacity 0.2s"
             :bgcolor "rgba(0,0,0,0.7)"
             :color "white"
             :px 2
             :py 1
             :borderRadius 1
             :fontSize "0.875rem"
             :pointerEvents "none"
             ":hover" {:opacity 1}}} "Click to zoom"]])])

(defn wine-images-section
  [app-state wine]
  [:<>
   ;; Image zoom modal
   (when-let [zoomed (get @app-state :zoomed-image)]
     [image-zoom-modal app-state (:data zoomed) (:title zoomed)])
   ;; Front Wine Label Image
   [grid {:item true :xs 12 :md 6}
    [paper {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
     [typography {:variant "body2" :color "text.secondary"} "Front Label"]
     [clickable-wine-image (:label_image wine) "front" "Front Wine Label"
      #(api/update-wine-image app-state (:id wine) %)
      #(api/update-wine-image
        app-state
        (:id wine)
        (assoc wine :label_image nil :label_thumbnail nil)) app-state]]]
   ;; Back Wine Label Image
   [grid {:item true :xs 12 :md 6}
    [paper {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
     [typography {:variant "body2" :color "text.secondary"} "Back Label"]
     [clickable-wine-image (:back_label_image wine) "back" "Back Wine Label"
      #(api/update-wine-image app-state (:id wine) %)
      #(api/update-wine-image app-state
                              (:id wine)
                              (assoc wine :back_label_image nil)) app-state]]]])

(defn stacked-fields-column
  [fields]
  [grid {:container true :spacing 1 :direction "column"}
   (for [[idx field] (map-indexed vector fields)]
     ^{:key idx}
     [grid {:item true}
      [paper
       {:elevation 0
        :sx {:p 2
             :bgcolor "rgba(0,0,0,0.02)"
             :borderRadius 1
             :mb (if (< idx (dec (count fields))) 1 0)}} field]])])

(defn field-card
  [title content]
  [paper
   {:elevation 0
    :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1 :height "100%"}}
   [typography {:variant "body2" :color "text.secondary"} title] content])

(defn compact-field-group
  [fields]
  [paper
   {:elevation 0
    :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1 :height "100%"}}
   [grid {:container true :spacing 2}
    (for [[idx [title content]] (map-indexed vector fields)]
      ^{:key idx}
      [grid {:item true :xs 12}
       [typography {:variant "body2" :color "text.secondary" :sx {:mb 0.5}}
        title] content])]])

(defn inline-field-group
  [fields]
  [paper
   {:elevation 0
    :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1 :height "100%"}}
   [grid {:container true :spacing 1}
    (for [[idx [title content]] (map-indexed vector fields)]
      ^{:key idx}
      [grid {:item true :xs 12 :sm (int (/ 12 (count fields)))}
       [typography {:variant "body2" :color "text.secondary" :sx {:mb 0.5}}
        title] content])]])

(defn wine-classification-section
  [app-state wine]
  [:<>
   ;; Classification + Level + Vineyard
   [grid {:item true :xs 12 :md 6}
    [inline-field-group
     [["Classification" [editable-classification app-state wine]]
      ["Level" [editable-level app-state wine]]
      ["Vineyard" [editable-vineyard app-state wine]]]]]])

(defn wine-compact-details-section
  [app-state wine]
  [:<>
   ;; Grape Varieties (needs space for multiple varieties)
   [grid {:item true :xs 12 :md 6}
    [field-card "Grape Varieties" [wine-varieties-list app-state (:id wine)]]]
   ;; Wine Characteristics: Style + Alcohol %
   [grid {:item true :xs 12 :md 6}
    [inline-field-group
     [["Style" [editable-styles app-state wine]]
      ["Alcohol %" [editable-alcohol-percentage app-state wine]]]]]
   ;; Inventory Info: Location + Current Quantity + Original Quantity
   [grid {:item true :xs 12 :md 6}
    [inline-field-group
     [["Location" [editable-location app-state wine]]
      ["Current Qty"
       [box {:display "flex" :alignItems "center"}
        [quantity-control app-state (:id wine) (:quantity wine)
         (str (:quantity wine)) (:original_quantity wine)]]]
      ["Original Qty" [editable-original-quantity app-state wine]]]]]
   ;; Purchase Info: Price + Purchased From + Purchase Date
   [grid {:item true :xs 12 :md 6}
    [inline-field-group
     [["Price" [editable-price app-state wine]]
      ["Purchased From" [editable-purveyor app-state wine]]
      ["Purchase Date" [editable-purchase-date app-state wine]]]]]
   ;; Vintage Info: Vintage + Disgorgement Year
   [grid {:item true :xs 12 :md 6}
    [inline-field-group
     [["Vintage" [editable-vintage app-state wine]]
      ["Disgorgement Year" [editable-disgorgement-year app-state wine]]]]]])

(defn wine-tasting-window-suggestion-buttons
  [app-state wine]
  (when-let [suggestion (get @app-state :window-suggestion)]
    [box {:sx {:mt 2 :display "flex" :gap 1 :flexWrap "wrap"}}
     [button
      {:variant "contained"
       :color "secondary"
       :size "small"
       :onClick (fn []
                  (let [{:keys [drink_from_year drink_until_year message]}
                        suggestion]
                    (api/update-wine app-state
                                     (:id wine)
                                     {:drink_from_year drink_from_year
                                      :drink_until_year drink_until_year
                                      :tasting_window_commentary message})
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
                                     {:drink_from_year drink_from_year})))}
      "Apply From Year"]
     [button
      {:variant "outlined"
       :color "secondary"
       :size "small"
       :onClick (fn []
                  (let [{:keys [drink_until_year]} suggestion]
                    (api/update-wine app-state
                                     (:id wine)
                                     {:drink_until_year drink_until_year})))}
      "Apply Until Year"]
     [button
      {:variant "outlined"
       :color "secondary"
       :size "small"
       :onClick (fn []
                  (let [{:keys [message]} suggestion]
                    (api/update-wine app-state
                                     (:id wine)
                                     {:tasting_window_commentary message})))}
      "Apply Commentary"]
     [button
      {:variant "text"
       :color "secondary"
       :size "small"
       :onClick (fn [] (swap! app-state dissoc :window-suggestion))}
      "Dismiss"]]))

(defn wine-tasting-window-suggestion
  [app-state wine]
  [box {:sx {:mt 2}}
   [button
    {:variant "outlined"
     :color "secondary"
     :size "small"
     :disabled (:suggesting-drinking-window? @app-state)
     :startIcon (r/as-element [auto-awesome])
     :onClick
     (fn []
       (-> (api/suggest-drinking-window app-state wine)
           (.then (fn [{:keys [drink_from_year drink_until_year confidence
                               reasoning]
                        :as suggestion}]
                    (swap! app-state assoc
                      :window-suggestion
                      (assoc suggestion
                             :message
                             (str "Drinking window suggested: " drink_from_year
                                  " to " drink_until_year
                                  " (" confidence
                                  " confidence)\n\n" reasoning)))))
           (.catch (fn [error]
                     (swap! app-state assoc
                       :error
                       (str "Failed to suggest drinking window: " error))))))}
    (if (:suggesting-drinking-window? @app-state)
      [box {:sx {:display "flex" :alignItems "center"}}
       [circular-progress {:size 20 :sx {:mr 1}}] "Suggesting..."]
      "Suggest Drinking Window")]
   [typography {:variant "body2" :sx {:mt 1}}
    (get-in @app-state [:window-suggestion :message])]
   [wine-tasting-window-suggestion-buttons app-state wine]])

(defn wine-tasting-window-section
  [app-state wine]
  [grid {:item true :xs 12}
   [paper {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
    [typography {:variant "body2" :color "text.secondary"} "Tasting Window"]
    [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
     ;; From and Until on same row
     [grid {:container true :spacing 2}
      [grid {:item true :xs 6}
       [typography {:variant "body2" :color "text.secondary" :sx {:mb 0.5}}
        "From"] [editable-drink-from-year app-state wine]]
      [grid {:item true :xs 6}
       [typography {:variant "body2" :color "text.secondary" :sx {:mb 0.5}}
        "Until"] [editable-drink-until-year app-state wine]]]
     [box {:sx {:mt 1}} [editable-tasting-window-commentary app-state wine]]
     (let [status (vintage/tasting-window-status wine)]
       [typography
        {:variant "body2"
         :color (vintage/tasting-window-color status)
         :sx {:mt 1 :fontStyle "italic"}}
        (vintage/format-tasting-window-text wine)])
     [wine-tasting-window-suggestion app-state wine]]]])

(defn wine-ai-summary-section
  [app-state wine]
  [grid {:item true :xs 12}
   [paper {:elevation 0 :sx {:p 2 :bgcolor "rgba(0,0,0,0.02)" :borderRadius 1}}
    [typography {:variant "body2" :color "text.secondary"} "AI Wine Summary"]
    [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
     [editable-ai-summary app-state wine]
     [box {:sx {:mt 1}}
      [button
       {:variant "outlined"
        :color "secondary"
        :size "small"
        :disabled (:generating-ai-summary? @app-state)
        :startIcon (r/as-element [auto-awesome])
        :onClick
        (fn []
          (swap! app-state assoc :generating-ai-summary? true)
          (-> (api/generate-wine-summary app-state wine)
              (.then (fn [summary]
                       (swap! app-state update
                         :wines
                         (fn [wines]
                           (map #(if (= (:id %) (:id wine))
                                   (assoc % :ai_summary summary)
                                   %)
                                wines)))
                       (swap! app-state assoc-in
                         [:force-edit-ai-summary (:id wine)]
                         true)
                       (swap! app-state dissoc :generating-ai-summary?)))
              (.catch (fn [error]
                        (swap! app-state assoc
                          :error
                          (str "Failed to generate summary: " error))
                        (swap! app-state dissoc :generating-ai-summary?)))))}
       (if (:generating-ai-summary? @app-state)
         [box {:sx {:display "flex" :alignItems "center"}}
          [circular-progress {:size 20 :sx {:mr 1}}] "Generating..."]
         "Generate AI Summary")]]]]])

(defn wine-tasting-notes-section
  [app-state wine]
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
     [tasting-note-form app-state (:id wine)])])

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
   [wine-identity-section app-state wine]
   [grid {:container true :spacing 3 :sx {:mb 4}}
    [wine-images-section app-state wine]
    [wine-classification-section app-state wine]
    [wine-compact-details-section app-state wine]
    [wine-tasting-window-section app-state wine]
    [wine-ai-summary-section app-state wine]]
   [wine-tasting-notes-section app-state wine]])

(defn wine-loading-view
  "Show loading state while fetching wines"
  []
  [box
   {:sx {:display "flex"
         :justifyContent "center"
         :alignItems "center"
         :minHeight "400px"}} [circular-progress]
   [typography {:sx {:ml 2}} "Loading wine details..."]])


(defn delete-wine-confirmation-text
  "Generate confirmation text for wine deletion"
  [selected-wine]
  (str "Are you sure you want to delete "
       (or (:producer selected-wine) "")
       (when (and (:producer selected-wine) (:name selected-wine)) " ")
       (or (:name selected-wine) "")
       (when (:vintage selected-wine) (str " " (:vintage selected-wine)))
       "? This action cannot be undone."))

(defn delete-button-click-handler
  "Handle delete wine button click"
  [app-state selected-wine-id selected-wine]
  (fn []
    (when (js/confirm (delete-wine-confirmation-text selected-wine))
      (go
       ;; Delete the wine
       (<! (api/delete-wine app-state selected-wine-id))
       ;; Replace browser history to prevent back button to deleted wine
       (.replaceState js/history nil "" "/")
       ;; Navigate back to the list automatically
       (swap! app-state dissoc
         :selected-wine-id :tasting-notes
         :editing-note-id :window-suggestion)
       (swap! app-state assoc :new-tasting-note {})))))

(defn wine-action-buttons
  "Render the back and delete buttons for wine details"
  [app-state selected-wine-id selected-wine]
  [box {:sx {:mt 2 :display "flex" :gap 2 :justifyContent "space-between"}}
   [button
    {:variant "contained"
     :color "primary"
     :start-icon (r/as-element [arrow-back])
     :onClick #(do (tap> {:back-button-clicked true})
                   (api/exit-wine-detail-page app-state))} "Back to List"]
   [button
    {:variant "outlined"
     :color "error"
     :start-icon (r/as-element [delete])
     :onClick
     (delete-button-click-handler app-state selected-wine-id selected-wine)}
    "Delete Wine"]])

(defn wine-details-content
  "Render the wine details with action buttons"
  [app-state selected-wine-id selected-wine]
  [box {:sx {:mb 3}} [wine-detail app-state selected-wine]
   [wine-action-buttons app-state selected-wine-id selected-wine]])

(defn wine-details-section
  [app-state]
  (when-let [selected-wine-id (:selected-wine-id @app-state)]
    ;; If wines collection is empty, fetch all wines first
    (when (and (empty? (:wines @app-state)) (not (:loading? @app-state)))
      (api/fetch-wines app-state))
    (if (:loading? @app-state)
      [wine-loading-view]
      ;; Show wine details once loaded
      (when-let [selected-wine (first (filter #(= (:id %) selected-wine-id)
                                              (:wines @app-state)))]
        [wine-details-content app-state selected-wine-id selected-wine]))))
