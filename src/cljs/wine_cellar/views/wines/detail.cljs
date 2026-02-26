(ns wine-cellar.views.wines.detail
  (:require
    [clojure.string :as str]
    [cljs.core.async :refer [<! go]]
    [goog.string :as gstring]
    [goog.string.format]
    [goog.object :as gobj]
    [reagent-mui.icons.add :refer [add]]
    [reagent-mui.icons.arrow-back :refer [arrow-back]]
    [reagent-mui.icons.auto-awesome :refer [auto-awesome]]
    [reagent-mui.icons.delete :refer [delete]]
    [reagent-mui.icons.close :refer [close]]
    [reagent-mui.icons.public :refer [public] :rename {public globe}]
    [reagent-mui.icons.wine-bar :refer [wine-bar]]
    [reagent-mui.icons.inventory :refer [inventory]]
    [reagent-mui.icons.receipt :refer [receipt]]
    [reagent-mui.icons.schedule :refer [schedule]]
    [reagent-mui.icons.science :refer [science]]
    [reagent-mui.icons.history :refer [history] :rename {history history-icon}]
    [reagent-mui.icons.rate-review :refer [rate-review]]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.button :refer [button]]
    [reagent-mui.material.circular-progress :refer [circular-progress]]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.paper :refer [paper]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.text-field :refer [text-field]]
    [reagent-mui.material.tooltip :refer [tooltip]]
    [reagent-mui.material.modal :refer [modal]]
    [reagent-mui.material.backdrop :refer [backdrop]]
    [reagent-mui.material.divider :refer [divider]]
    [reagent-mui.material.autocomplete :refer [autocomplete]]
    [reagent-mui.material.dialog :refer [dialog]]
    [reagent-mui.material.dialog-title :refer [dialog-title]]
    [reagent-mui.material.dialog-content :refer [dialog-content]]
    [reagent-mui.material.dialog-actions :refer [dialog-actions]]
    ["@mui/material/TextField" :default TextField]
    [reagent-mui.material.table :refer [table]]
    [reagent-mui.material.table-body :refer [table-body]]
    [reagent-mui.material.table-cell :refer [table-cell]]
    [reagent-mui.material.table-row :refer [table-row]]
    [reagent.core :as r]
    [wine-cellar.api :as api]
    [wine-cellar.nav :as nav]
    [wine-cellar.common :as common]
    [wine-cellar.utils.formatting :refer [format-date-iso valid-name-producer?]]
    [wine-cellar.utils.vintage :as vintage]
    [wine-cellar.views.components :refer
     [editable-autocomplete-field editable-classification-field
      editable-text-field quantity-control]]
    [wine-cellar.views.components.image-upload :refer [image-upload]]
    [wine-cellar.views.tasting-notes.form :refer [tasting-note-form]]
    [wine-cellar.views.wines.varieties :refer [wine-varieties-list]]
    [wine-cellar.views.tasting-notes.list :refer [tasting-notes-list]]
    [wine-cellar.views.components.ai-provider-toggle :refer
     [provider-toggle-button]]
    [wine-cellar.views.components.technical-data :refer
     [technical-data-editor]]))



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
    :format-fn #(str % "% ABV")
    :empty-text "Add ABV"
    :compact? true
    :inline? true
    :text-field-props {:type "number"
                       :step "0.1"
                       :InputProps {:endAdornment "%"}
                       :helperText "e.g., 13.5 for 13.5% ABV"}}])

(defn editable-dosage
  [app-state wine]
  [editable-text-field
   {:value (when-let [dosage (:dosage wine)] (str (js/Math.round dosage)))
    :on-save (fn [new-value]
               (let [trimmed (str/trim new-value)]
                 (if (str/blank? trimmed)
                   (api/update-wine app-state (:id wine) {:dosage nil})
                   (let [parsed (js/parseFloat trimmed)]
                     (when-not (js/isNaN parsed)
                       (api/update-wine app-state
                                        (:id wine)
                                        {:dosage (js/Math.round parsed)}))))))
    :validate-fn (fn [value]
                   (let [trimmed (str/trim (or value ""))]
                     (cond (str/blank? trimmed) nil
                           :else (let [parsed (js/parseFloat trimmed)]
                                   (cond
                                     (js/isNaN parsed) "Dosage must be a number"
                                     (< parsed 0) "Dosage cannot be negative"
                                     (> parsed 200) "Dosage must be ≤ 200 g/L"
                                     :else nil)))))
    :format-fn #(str "Dosage " % " g/L")
    :empty-text "Add dosage"
    :compact? true
    :inline? true
    :text-field-props
    {:type "number" :step "1" :InputProps {:endAdornment "g/L"}}}])

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
    :format-fn #(str "Disgorged in " %)
    :empty-text "Add disgorgement year"
    :compact? true
    :inline? true
    :text-field-props
    {:type "number"
     :helperText "Year when the wine was disgorged (for sparkling wines)"}}])


(defn update-wine-metadata
  [app-state wine metadata-key new-value]
  (let [current-metadata (or (:metadata wine) {})
        updated-metadata (if (or (nil? new-value) (str/blank? (str new-value)))
                           (dissoc current-metadata metadata-key)
                           (assoc current-metadata metadata-key new-value))]
    (api/update-wine app-state (:id wine) {:metadata updated-metadata})))

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
    :empty-text "Add wine name"
    :inline? true
    :display-sx {:fontSize "1.2rem" :fontWeight 500}}])

(defn editable-producer
  [app-state wine]
  [editable-text-field
   {:value (:producer wine)
    :on-save (fn [new-value]
               (let [updated-wine (assoc wine :producer new-value)]
                 (if (valid-name-producer? updated-wine)
                   (api/update-wine app-state (:id wine) {:producer new-value})
                   (js/alert "Either Wine Name or Producer must be provided"))))
    :empty-text "Add producer"
    :inline? true
    :display-sx {:fontSize "1.2rem" :fontWeight 500}}])

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
    :display-sx {:fontSize "1.2rem" :fontWeight 500}}])

(defn editable-designation
  [app-state wine]
  [editable-autocomplete-field
   {:value (:designation wine)
    :tooltip (:designation common/field-descriptions)
    :options (vec (sort common/wine-designations))
    :free-solo false
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:designation new-value}))
    :empty-text "Add designation"
    :compact? true
    :inline? true}])

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
    :empty-text "Add country"
    :inline? true}])

(defn editable-region
  [app-state wine]
  [editable-classification-field
   {:value (:region wine)
    :field-type :region
    :tooltip (:region common/field-descriptions)
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:region new-value}))
    :validate-fn (fn [value] (when (str/blank? value) "Region cannot be empty"))
    :empty-text "Add region"
    :inline? true}])

(defn editable-appellation
  [app-state wine]
  [editable-classification-field
   {:value (:appellation wine)
    :field-type :appellation
    :tooltip (:appellation common/field-descriptions)
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:appellation new-value}))
    :empty-text "Add Appellation"
    :inline? true}])

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
  [editable-classification-field
   {:value (:vineyard wine)
    :field-type :vineyard
    :tooltip (:vineyard common/field-descriptions)
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:vineyard new-value}))
    :empty-text "Add vineyard"
    :compact? true
    :inline? true}])

(defn editable-classification
  [app-state wine]
  [editable-classification-field
   {:value (:classification wine)
    :field-type :classification
    :tooltip (:classification common/field-descriptions)
    :app-state app-state
    :wine wine
    :classifications (:classifications @app-state)
    :on-save
    (fn [new-value]
      (api/update-wine app-state (:id wine) {:classification new-value}))
    :empty-text "Add classification"
    :compact? true
    :inline? true}])

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
    :compact? true
    :inline? true}])

(defn editable-closure-type
  [app-state wine]
  [editable-autocomplete-field
   {:value (:closure_type wine)
    :options common/closure-type-options
    :free-solo false
    :on-save (fn [new-value]
               (api/update-wine app-state (:id wine) {:closure_type new-value}))
    :empty-text "Select closure type"
    :compact? true
    :inline? true}])

(defn editable-bottle-format
  [app-state wine]
  [editable-autocomplete-field
   {:value (:bottle_format wine)
    :options common/bottle-formats
    :free-solo false
    :on-save
    (fn [new-value]
      (api/update-wine app-state (:id wine) {:bottle_format new-value}))
    :empty-text "Select format"
    :compact? true
    :inline? true}])


(defn- dot-sep
  []
  [typography
   {:component "span" :sx {:color "text.secondary" :mx 0.75 :userSelect "none"}}
   "·"])

(defn- dot-separated-row
  [& children]
  [box {:sx {:display "flex" :flexWrap "wrap" :alignItems "baseline"}}
   (for [[i child] (map-indexed vector children)]
     ^{:key i} [:<> (when (pos? i) [dot-sep]) child])])

(defn wine-identity-section
  [app-state wine]
  [box {:sx {:mb 3 :pb 2 :borderBottom "1px solid rgba(0,0,0,0.08)"}}
   [dot-separated-row [editable-vintage app-state wine]
    [editable-producer app-state wine] [editable-name app-state wine]]])

(defn image-zoom-modal
  [app-state image-data image-title on-remove]
  [modal
   {:open (boolean (get @app-state :zoomed-image))
    :onClose #(.back js/history)
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
      [box {:sx {:display "flex" :alignItems "center" :gap 1}}
       (when on-remove
         [button
          {:size "small"
           :color "error"
           :variant "text"
           :onClick (fn [] (on-remove) (.back js/history))} "Remove"])
       [button {:onClick #(.back js/history) :sx {:minWidth "auto" :p 1}}
        [close]]]]
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
       :onClick
       #(do (.stopPropagation %)
            (.pushState js/history nil "" (.-pathname js/location))
            (swap! app-state assoc
              :zoomed-image
              {:data image-data :title title :on-remove on-image-remove}))}
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
     [image-zoom-modal app-state (:data zoomed) (:title zoomed)
      (:on-remove zoomed)])
   ;; Front Wine Label Image
   [grid {:item true :xs 6}
    [paper {:elevation 0 :sx {:p 2 :borderRadius 1}}
     [clickable-wine-image (:label_image wine) "front" "Front Wine Label"
      #(api/update-wine-image app-state (:id wine) %)
      #(api/update-wine-image
        app-state
        (:id wine)
        (assoc wine :label_image nil :label_thumbnail nil)) app-state]]]
   ;; Back Wine Label Image
   [grid {:item true :xs 6}
    [paper {:elevation 0 :sx {:p 2 :borderRadius 1}}
     [clickable-wine-image (:back_label_image wine) "back" "Back Wine Label"
      #(api/update-wine-image app-state (:id wine) %)
      #(api/update-wine-image app-state
                              (:id wine)
                              (assoc wine :back_label_image nil)) app-state]]]])

(defn- section-header
  [icon-component label border-color]
  [box
   {:sx {:display "flex"
         :alignItems "center"
         :mb 1.5
         :pb 1
         :borderBottom "1px solid rgba(255,255,255,0.06)"}}
   [box {:sx {:color border-color :display "flex" :mr 1 :opacity 0.85}}
    [icon-component {:fontSize "small"}]]
   [typography
    {:variant "overline"
     :sx {:fontWeight 700
          :letterSpacing "0.1em"
          :color "text.secondary"
          :lineHeight 1}} label]])

(defn wine-terroir-section
  [app-state wine]
  [box {:sx {:mt 3 :borderLeft "3px solid rgba(139,195,74,0.7)" :pl 1.5 :pb 2}}
   [section-header globe "Terroir" "rgba(139,195,74,0.7)"]
   [dot-separated-row [editable-country app-state wine]
    [editable-region app-state wine] [editable-appellation app-state wine]
    [editable-appellation-tier app-state wine]]
   [dot-separated-row [editable-classification app-state wine]
    [editable-designation app-state wine] [editable-vineyard app-state wine]]])

(def ^:private sparkling-styles #{"Sparkling" "Red Sparkling" "Rose Sparkling"})

(defn wine-composition-section
  [app-state wine]
  [box {:sx {:mt 3 :borderLeft "3px solid rgba(186,104,200,0.7)" :pl 1.5 :pb 2}}
   [section-header wine-bar "Composition" "rgba(186,104,200,0.7)"]
   [dot-separated-row [editable-styles app-state wine]
    [editable-alcohol-percentage app-state wine]
    [editable-bottle-format app-state wine]
    [editable-closure-type app-state wine]]
   (when (contains? sparkling-styles (:style wine))
     [box {:sx {:display "flex" :alignItems "baseline" :gap 0.5 :mt 1.5}}
      [dot-separated-row [editable-disgorgement-year app-state wine]
       [editable-dosage app-state wine]]
      [box {:component "span" :sx {:fontSize "1rem" :lineHeight 1}} "🫧"]])
   [divider
    {:sx {:my 1.5 :borderColor "rgba(186,104,200,0.7)" :borderTopWidth "3px"}}]
   [box {:sx {:mt 1}} [wine-varieties-list app-state (:id wine)]]])

(defn- cellar-summary
  [wine]
  (let [qty (:quantity wine)
        original (:original_quantity wine)
        location (when-not (str/blank? (:location wine)) (:location wine))]
    (cond (and original location) (str qty " of " original " · " location)
          original (str qty " of " original)
          location (str qty " bottles · " location)
          :else (str qty " bottles"))))

(defn- cellar-edit-modal
  [app-state wine open?]
  (r/with-let
   [laid-down-val (r/atom (when-let [q (:original_quantity wine)] (str q)))
    location-val (r/atom (or (:location wine) "")) error-msg (r/atom nil)]
   [dialog
    {:open true :onClose #(reset! open? false) :maxWidth "xs" :fullWidth true}
    [dialog-title "Cellar Stock"]
    [dialog-content
     [box {:sx {:pt 1 :display "flex" :flexDirection "column" :gap 2}}
      [quantity-control app-state (:id wine) (:quantity wine)
       (str (:quantity wine)) (:original_quantity wine)]
      [box {:sx {:borderTop "1px solid rgba(255,255,255,0.08)" :mt 0.5}}]
      [text-field
       {:value (or @laid-down-val "")
        :label "Laid Down"
        :type "number"
        :fullWidth true
        :size "small"
        :error (boolean @error-msg)
        :helperText (or @error-msg "Bottles originally purchased or laid down")
        :onChange (fn [e]
                    (reset! laid-down-val (.. e -target -value))
                    (reset! error-msg nil))}]
      [text-field
       {:value (or @location-val "")
        :label "Location"
        :fullWidth true
        :size "small"
        :placeholder "e.g. E2, Rack 2, Wine Fridge"
        :onChange (fn [e] (reset! location-val (.. e -target -value)))}]]]
    [dialog-actions [button {:onClick #(reset! open? false)} "Cancel"]
     [button
      {:variant "contained"
       :onClick
       (fn []
         (let [location (when-not (str/blank? @location-val) @location-val)
               laid-down (when-not (str/blank? @laid-down-val)
                           (js/parseInt @laid-down-val 10))
               current-qty (:quantity wine)]
           (if (and laid-down
                    (not (js/isNaN laid-down))
                    (< laid-down current-qty))
             (reset! error-msg (str "Can't set laid down to " laid-down
                                    " — current stock is " current-qty))
             (do (api/update-wine app-state
                                  (:id wine)
                                  {:location location
                                   :original_quantity laid-down})
                 (reset! open? false)))))} "Save"]]]))

(defn wine-cellar-section
  [app-state wine]
  (r/with-let
   [open? (r/atom false)]
   [box
    {:sx {:mt 3 :borderLeft "3px solid rgba(100,181,246,0.7)" :pl 1.5 :pb 2}}
    [section-header inventory "Cellar" "rgba(100,181,246,0.7)"]
    [box
     {:sx {:cursor "pointer"
           :borderRadius 1
           :px 0.5
           :mx -0.5
           "&:hover" {:bgcolor "action.hover"}}
      :onClick #(reset! open? true)}
     [typography {:variant "body1"} (cellar-summary wine)]]
    (when @open? [cellar-edit-modal app-state wine open?])]))

(defn- provenance-summary
  [wine]
  (let [price (:price wine)
        purveyor (when-not (str/blank? (:purveyor wine)) (:purveyor wine))
        date (format-date-iso (:purchase_date wine))
        price-str (when price (str "$" (gstring/format "%.2f" price)))]
    (if (and (nil? price-str) (nil? purveyor) (nil? date))
      "Add purchase details"
      (str/join
       " "
       (filter identity
               [(when price-str (str "Paid " price-str))
                (when purveyor
                  (if price-str (str "from " purveyor) (str "From " purveyor)))
                (when date (str "on " date))])))))

(defn- provenance-edit-modal
  [app-state wine open?]
  (r/with-let
   [price-val (r/atom (when-let [p (:price wine)] (gstring/format "%.2f" p)))
    purveyor-val (r/atom (or (:purveyor wine) "")) date-val
    (r/atom (format-date-iso (:purchase_date wine)))]
   (let [all-wines (:wines @app-state)
         existing-purveyors (->> all-wines
                                 (map :purveyor)
                                 (filter #(and % (not (str/blank? %))))
                                 (distinct)
                                 (sort)
                                 (vec))]
     [dialog
      {:open true :onClose #(reset! open? false) :maxWidth "xs" :fullWidth true}
      [dialog-title "Purchase Details"]
      [dialog-content
       [box {:sx {:pt 2 :display "flex" :flexDirection "column" :gap 2}}
        [text-field
         {:value (or @price-val "")
          :type "number"
          :label "Price"
          :fullWidth true
          :InputProps {:startAdornment "$"}
          :onChange (fn [e] (reset! price-val (.. e -target -value)))}]
        [autocomplete
         {:freeSolo true
          :options existing-purveyors
          :value @purveyor-val
          :onChange (fn [_ v] (when v (reset! purveyor-val v)))
          :onInputChange (fn [_ v _] (reset! purveyor-val v))
          :renderInput (fn [params]
                         (let [props (gobj/clone params)]
                           (gobj/set props "label" "Purchased From")
                           (gobj/set props "variant" "outlined")
                           (gobj/set props "fullWidth" true)
                           (r/create-element TextField props)))}]
        [text-field
         {:value (or @date-val "")
          :type "date"
          :label "Purchase Date"
          :fullWidth true
          :InputLabelProps {:shrink true}
          :onChange (fn [e] (reset! date-val (.. e -target -value)))}]]]
      [dialog-actions [button {:onClick #(reset! open? false)} "Cancel"]
       [button
        {:variant "contained"
         :onClick (fn []
                    (let [price (when-not (str/blank? @price-val)
                                  (js/parseFloat @price-val))
                          purveyor (when-not (str/blank? @purveyor-val)
                                     @purveyor-val)
                          date (when-not (str/blank? @date-val) @date-val)]
                      (api/update-wine
                       app-state
                       (:id wine)
                       {:price price :purveyor purveyor :purchase_date date})
                      (reset! open? false)))} "Save"]]])))

(defn wine-provenance-section
  [app-state wine]
  (r/with-let
   [open? (r/atom false)]
   [box {:sx {:mt 3 :borderLeft "3px solid rgba(255,213,79,0.7)" :pl 1.5 :pb 2}}
    [section-header receipt "Provenance" "rgba(255,213,79,0.7)"]
    [box
     {:sx {:cursor "pointer"
           :borderRadius 1
           :px 0.5
           :mx -0.5
           "&:hover" {:bgcolor "action.hover"}}
      :onClick #(reset! open? true)}
     [typography {:variant "body1"} (provenance-summary wine)]]
    (when @open? [provenance-edit-modal app-state wine open?])]))

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
  (let [suggesting? (:suggesting-drinking-window? @app-state)]
    [box {:sx {:mt 2}}
     [box {:sx {:display "flex" :alignItems "center" :flexWrap "wrap" :gap 1}}
      [button
       {:variant "outlined"
        :color "secondary"
        :size "small"
        :disabled suggesting?
        :startIcon (when-not suggesting? (r/as-element [auto-awesome]))
        :onClick (fn []
                   (-> (api/suggest-drinking-window app-state wine)
                       (.then (fn [{:keys [drink_from_year drink_until_year
                                           confidence reasoning]
                                    :as suggestion}]
                                (swap! app-state assoc
                                  :window-suggestion
                                  (assoc suggestion
                                         :message
                                         (str "Drinking window suggested: "
                                              drink_from_year
                                              " to " drink_until_year
                                              " (" confidence
                                              " confidence)\n\n" reasoning)))))
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
     [typography {:variant "body2" :sx {:mt 1}}
      (get-in @app-state [:window-suggestion :message])]
     [wine-tasting-window-suggestion-buttons app-state wine]]))

(defn- drinking-window-modal
  [app-state wine open?]
  (r/with-let
   [from-val (r/atom (when-let [y (:drink_from_year wine)] (str y))) until-val
    (r/atom (when-let [y (:drink_until_year wine)] (str y))) error-msg
    (r/atom nil)]
   [dialog
    {:open true :onClose #(reset! open? false) :maxWidth "xs" :fullWidth true}
    [dialog-title "Drinking Window"]
    [dialog-content
     [box {:sx {:pt 2 :display "flex" :flexDirection "column" :gap 2}}
      [text-field
       {:value (or @from-val "")
        :type "number"
        :label "Drink From Year"
        :fullWidth true
        :onChange (fn [e]
                    (reset! from-val (.. e -target -value))
                    (reset! error-msg nil))}]
      [text-field
       {:value (or @until-val "")
        :type "number"
        :label "Drink Until Year"
        :fullWidth true
        :error (boolean @error-msg)
        :helperText @error-msg
        :onChange (fn [e]
                    (reset! until-val (.. e -target -value))
                    (reset! error-msg nil))}]]]
    [dialog-actions [button {:onClick #(reset! open? false)} "Cancel"]
     [button
      {:variant "contained"
       :onClick (fn []
                  (let [from (when-not (str/blank? @from-val)
                               (js/parseInt @from-val 10))
                        until (when-not (str/blank? @until-val)
                                (js/parseInt @until-val 10))
                        err (vintage/valid-tasting-window? from until)]
                    (if err
                      (reset! error-msg err)
                      (do (api/update-wine app-state
                                           (:id wine)
                                           {:drink_from_year from
                                            :drink_until_year until})
                          (reset! open? false)))))} "Save"]]]))

(defn wine-tasting-window-section
  [app-state wine]
  (r/with-let
   [open? (r/atom false)]
   [box {:sx {:mt 3 :borderLeft "3px solid rgba(255,152,0,0.7)" :pl 1.5 :pb 2}}
    [section-header schedule "Drinking Window" "rgba(255,152,0,0.7)"]
    [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
     (let [status (vintage/tasting-window-status wine)
           window-text (vintage/format-tasting-window-text wine)]
       [typography
        {:variant "body2"
         :color (if (str/blank? window-text)
                  "text.secondary"
                  (vintage/tasting-window-color status))
         :sx {:fontStyle "italic"
              :cursor "pointer"
              :borderRadius 1
              :px 0.5
              :mx -0.5
              "&:hover" {:bgcolor "action.hover"}}
         :onClick #(reset! open? true)}
        (if (str/blank? window-text) "Set drinking window" window-text)])
     [box {:sx {:mt 1}} [editable-tasting-window-commentary app-state wine]]
     [wine-tasting-window-suggestion app-state wine]]
    (when @open? [drinking-window-modal app-state wine open?])]))

(defn wine-ai-summary-section
  [app-state wine]
  (let [generating? (:generating-ai-summary? @app-state)]
    [box
     {:sx {:mt 3 :borderLeft "3px solid rgba(232,195,200,0.7)" :pl 1.5 :pb 2}}
     [section-header auto-awesome "AI Summary" "rgba(232,195,200,0.7)"]
     [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
      [editable-ai-summary app-state wine]
      [box
       {:sx
        {:mt 1 :display "flex" :alignItems "center" :flexWrap "wrap" :gap 1}}
       [button
        {:variant "outlined"
         :color "secondary"
         :size "small"
         :disabled generating?
         :startIcon (when-not generating? (r/as-element [auto-awesome]))
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
        (if generating?
          [box {:sx {:display "flex" :alignItems "center"}}
           [circular-progress {:size 20 :sx {:mr 1}}] "Generating..."]
          "Generate AI Summary")]
       [provider-toggle-button app-state
        {:mobile-min-width "auto" :sx {:minWidth "auto" :px 1 :py 0.25}}]]]]))

(defn wine-technical-notes-section
  [app-state wine]
  [box {:sx {:mt 3 :borderLeft "3px solid rgba(128,203,196,0.7)" :pl 1.5 :pb 2}}
   [section-header science "Technical Notes" "rgba(128,203,196,0.7)"]
   [technical-data-editor
    {:metadata (or (:metadata wine) {})
     :on-change
     (fn [new-metadata]
       (api/update-wine app-state (:id wine) {:metadata new-metadata}))}]])

(defn history-date-cell
  [record]
  [table-cell {:sx {:whiteSpace "nowrap"}}
   (format-date-iso (:occurred_at record))])

(defn history-change-cell
  [record]
  [table-cell
   {:sx {:color (if (pos? (:change_amount record)) "success.main" "#ff5252")
         :fontWeight "bold"}}
   (if (pos? (:change_amount record))
     (str "+" (:change_amount record))
     (:change_amount record))])

(def history-reason-display->key
  (into {} (for [[k v] common/inventory-reasons] [v k])))

(defn history-reason-cell
  [record]
  (let [reason-key (:reason record)
        display-label (get common/inventory-reasons
                           (str/lower-case (or reason-key ""))
                           reason-key)]
    [table-cell display-label]))

(defn history-balance-cell
  [record]
  [table-cell {:sx {:whiteSpace "nowrap"}}
   (if-let [oq (:original_quantity record)]
     (if (= (str/lower-case (or (:reason record) "")) "restock")
       (let [prev-oq (- oq (:change_amount record))]
         (str (:previous_quantity record)
              " / " prev-oq
              " → " (:new_quantity record)
              " / " oq))
       (str (:previous_quantity record)
            " / " oq
            " → " (:new_quantity record)
            " / " oq))
     (str (:previous_quantity record) " → " (:new_quantity record)))])

(defn history-notes-cell [record] [table-cell (:notes record)])

(defn history-edit-dialog
  [app-state wine-id record open? on-close]
  (r/with-let
   [local-state (r/atom nil)]
   (when @open?
     (when (nil? @local-state)
       (let [reason-key (:reason record)
             display-label (get common/inventory-reasons
                                (str/lower-case (or reason-key ""))
                                reason-key)]
         (reset! local-state {:occurred_at (format-date-iso (:occurred_at
                                                             record))
                              :reason reason-key
                              :reason-display display-label
                              :notes (:notes record)})))
     [dialog {:open @open? :onClose on-close :maxWidth "sm" :fullWidth true}
      [dialog-title "Edit History Record"]
      [dialog-content
       [box {:sx {:pt 2 :display "flex" :flexDirection "column" :gap 2}}
        [text-field
         {:type "date"
          :label "Date"
          :value (:occurred_at @local-state)
          :onChange
          #(swap! local-state assoc :occurred_at (.. % -target -value))
          :fullWidth true}]
        [autocomplete
         {:freeSolo true
          :options (sort (vals common/inventory-reasons))
          :value (:reason-display @local-state)
          :onInputChange
          (fn [_ new-display _]
            (let [k (get history-reason-display->key new-display new-display)]
              (swap! local-state assoc :reason k :reason-display new-display)))
          :renderInput (fn [params]
                         (let [props (gobj/clone params)]
                           (gobj/set props "label" "Reason")
                           (gobj/set props "variant" "outlined")
                           (gobj/set props "fullWidth" true)
                           (r/create-element TextField props)))}]
        [text-field
         {:label "Notes"
          :value (:notes @local-state)
          :onChange #(swap! local-state assoc :notes (.. % -target -value))
          :multiline true
          :rows 4
          :fullWidth true
          :variant "outlined"}]]]
      [dialog-actions
       [button
        {:color "error"
         :sx {:mr "auto"}
         :onClick
         (fn []
           (when (js/confirm
                  "Delete this history record? Quantity will NOT change.")
             (api/delete-inventory-history app-state wine-id (:id record))
             (on-close)))} "Delete"] [button {:onClick on-close} "Cancel"]
       [button
        {:variant "contained"
         :onClick (fn []
                    (api/update-inventory-history app-state
                                                  wine-id
                                                  (:id record)
                                                  @local-state)
                    (on-close))} "Save Changes"]]])))


(defn inventory-history-row
  [app-state wine-id record]
  (r/with-let [edit-open? (r/atom false)]
              [:<>
               (when @edit-open?
                 [history-edit-dialog app-state wine-id record edit-open?
                  #(reset! edit-open? false)])
               [table-row
                {:onClick #(reset! edit-open? true)
                 :sx {:cursor "pointer" "&:hover" {:bgcolor "action.hover"}}}
                [history-date-cell record] [history-change-cell record]
                [history-reason-cell record] [history-balance-cell record]
                [history-notes-cell record]]]))

(defn inventory-history-section
  [app-state wine]
  (let [history (get-in @app-state [:inventory-history (:id wine)])]
    [box
     {:sx {:mt 3 :borderLeft "3px solid rgba(144,164,174,0.7)" :pl 1.5 :pb 2}}
     [section-header history-icon "Inventory History" "rgba(144,164,174,0.7)"]
     (if (empty? history)
       [typography
        {:variant "body2" :color "text.secondary" :fontStyle "italic"}
        "No inventory history recorded yet."]
       [:<>
        [divider
         {:sx
          {:my 1.5 :borderColor "rgba(144,164,174,0.7)" :borderTopWidth "3px"}}]
        [box {:sx {:overflow-x "auto"}}
         [table
          {:size "small" :sx {:width "100%" "& td" {:borderBottom "none"}}}
          [table-body
           (for [record history]
             ^{:key (:id record)}
             [inventory-history-row app-state (:id wine) record])]]]])]))

(defn wine-tasting-notes-section
  [app-state wine]
  (let [on-close #(.back js/history)]
    [box
     {:sx {:mt 3 :borderLeft "3px solid rgba(240,98,146,0.7)" :pl 1.5 :pb 2}}
     [section-header rate-review "Tasting Notes" "rgba(240,98,146,0.7)"]
     [tasting-notes-list app-state (:id wine)]
     [tooltip {:title "Add tasting note" :placement "right" :arrow true}
      [button
       {:size "small"
        :sx {:mt 1 :color "text.secondary" :minWidth 0 :p 0.5}
        :on-click #(do (.pushState js/history nil "" (.-pathname js/location))
                       (swap! app-state assoc :show-tasting-note-form? true))}
       [add {:fontSize "small"}]]]
     [dialog
      {:open (or (:show-tasting-note-form? @app-state)
                 (boolean (:editing-note-id @app-state)))
       :onClose on-close
       :maxWidth "md"
       :fullWidth true} [tasting-note-form app-state (:id wine) on-close]]]))

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
     :bgcolor "container.main"
     :backgroundImage
     "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))"}}
   [grid {:container true :spacing 2 :sx {:mb 1}}
    [wine-images-section app-state wine]] [wine-identity-section app-state wine]
   [wine-terroir-section app-state wine]
   [wine-composition-section app-state wine]
   [wine-cellar-section app-state wine] [wine-provenance-section app-state wine]
   [wine-tasting-window-section app-state wine]
   [wine-ai-summary-section app-state wine]
   [wine-technical-notes-section app-state wine]
   [inventory-history-section app-state wine]
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
      (go (<! (api/delete-wine app-state selected-wine-id))
          (nav/replace-wines!)))))

(defn wine-action-buttons
  "Render the back and delete buttons for wine details"
  [app-state selected-wine-id selected-wine]
  [box {:sx {:mt 2 :display "flex" :gap 2 :justifyContent "space-between"}}
   [button
    {:variant "contained"
     :color "primary"
     :start-icon (r/as-element [arrow-back])
     :onClick #(.back js/history)} "Back to List"]
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
