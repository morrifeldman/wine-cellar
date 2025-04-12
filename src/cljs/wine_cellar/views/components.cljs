(ns wine-cellar.views.components
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as r]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.table-cell :refer [table-cell]]
            [reagent-mui.material.table-sort-label :refer [table-sort-label]]
            [reagent-mui.material.autocomplete :refer [autocomplete]]
            [reagent-mui.util :refer [react-component]]
            [reagent-mui.icons.arrow-drop-up :refer [arrow-drop-up]]
            [reagent-mui.icons.arrow-drop-down :refer [arrow-drop-down]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.edit :refer [edit]]
            [reagent-mui.icons.save :refer [save]]
            [reagent-mui.icons.cancel :refer [cancel]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [wine-cellar.utils.formatting :as formatting]
            [wine-cellar.utils.vintage :refer
             [tasting-window-status tasting-window-color]]
            [wine-cellar.api :as api]))

;; Shared styles
(def form-field-style {:min-width "180px", :width "75%"})

;; Table components
(defn sortable-header
  [app-state label field]
  (let [sort-state (:sort @app-state)
        current-field (:field sort-state)
        current-direction (:direction sort-state)
        is-active (= field current-field)
        direction (if (= :asc current-direction) "asc" "desc")]
    [table-cell {:align "left", :sx {:font-weight "bold"}}
     [table-sort-label
      (cond-> {:active is-active,
               :onClick #(swap! app-state update
                           :sort
                           (fn [sort]
                             (if (= field (:field sort))
                               {:field field,
                                :direction
                                  (if (= :asc (:direction sort)) :desc :asc)}
                               {:field field, :direction :asc})))}
        is-active (assoc :direction direction)) label]]))

;; Quantity control component
(defn quantity-control
  [app-state wine-id quantity]
  [box {:display "flex", :alignItems "center"}
   [box
    {:component "span",
     :sx {:fontSize "1rem", :mx 1, :minWidth "1.5rem", :textAlign "center"}}
    quantity]
   [box {:display "flex", :flexDirection "column", :ml 0.5}
    [button
     {:variant "text",
      :size "small",
      :sx {:minWidth 0, :p 0, :lineHeight 0.8},
      :onClick #(api/adjust-wine-quantity app-state wine-id 1)}
     [arrow-drop-up {:fontSize "small"}]]
    [button
     {:variant "text",
      :size "small",
      :sx {:minWidth 0, :p 0, :lineHeight 0.8},
      :disabled (= quantity 0),
      :onClick #(api/adjust-wine-quantity app-state wine-id -1)}
     [arrow-drop-down {:fontSize "small"}]]]])

(defn editable-field-wrapper
  "A generic wrapper for making any field editable.
   Options:
   - value: The current value of the field
   - on-save: Function to call when saving (receives new value)
   - validate-fn: Optional validation function that returns error message or nil
   - empty-text: Text to display when value is empty - defaults to 'Not specified'
   - render-input-fn: Function that renders the input component (receives value, on-change, and error)"
  [{:keys [value on-save validate-fn empty-text render-input-fn],
    :or {empty-text "Not specified"}}]
  (let [editing (r/atom false)
        field-value (r/atom value)
        field-error (r/atom nil)
        saving (r/atom false)]
    (fn [{:keys [value on-save validate-fn empty-text render-input-fn],
          :or {empty-text "Not specified"}}]
      (if @editing
        ;; Edit mode
        [box {:display "flex", :flexDirection "column", :width "100%"}
         [box {:display "flex", :alignItems "center"}
          [box {:sx {:flex 1, :mr 1}}
           (render-input-fn @field-value
                            (fn [new-value]
                              (reset! field-value new-value)
                              (reset! field-error nil))
                            @field-error)]
          [icon-button
           {:color "primary",
            :size "small",
            :disabled (or (boolean @field-error) @saving),
            :onClick
              (fn []
                (let [error (when validate-fn (validate-fn @field-value))]
                  (if error
                    (reset! field-error error)
                    (do 
                      (reset! saving true)
                      (-> (on-save @field-value)
                          (.then #(do
                                    (reset! saving false)
                                    (reset! editing false)))
                          (.catch #(do
                                     (reset! saving false)
                                     (reset! field-error "Save failed"))))))))}
           (if @saving
             [reagent-mui.material.circular-progress {:size 20}]
             [save])]
          [icon-button
           {:color "secondary",
            :size "small",
            :disabled @saving,
            :onClick (fn []
                       (reset! field-value value)
                       (reset! field-error nil)
                       (reset! editing false))} [cancel]]]]
        ;; View mode
        [box
         {:display "flex",
          :alignItems "center",
          :justifyContent "space-between",
          :width "100%"}
         [typography {:variant "body1"}
          (if (or (nil? value) (str/blank? value)) empty-text value)]
         [icon-button
          {:color "primary", :size "small", :onClick #(reset! editing true)}
          [edit]]])))

;; Specific field implementations
(defn editable-text-field
  "Standard text field implementation of editable-field"
  [{:keys [text-field-props], :as props}]
  [editable-field-wrapper
   (assoc props
     :render-input-fn (fn [value on-change error]
                        [text-field
                         (merge {:value value,
                                 :size "small",
                                 :fullWidth true,
                                 :autoFocus true,
                                 :error (boolean error),
                                 :helperText error,
                                 :onChange (fn [e]
                                             (on-change (.. e -target -value)))}
                                text-field-props)]))])

(defn editable-autocomplete-field
  "Autocomplete implementation of editable-field"
  [{:keys [options option-label free-solo multiple], :as props}]
  [editable-field-wrapper
   (assoc props
     :render-input-fn
       (fn [value on-change error]
         [autocomplete
          {:multiple (boolean multiple),
           :freeSolo (boolean free-solo),
           :options options,
           :size "small",
           :value (cond-> value multiple (or [])),
           :getOptionLabel (or option-label
                               (fn [option]
                                 (cond (nil? option) ""
                                       (string? option) option
                                       :else (str option)))),
           :renderInput (react-component [params]
                                         [text-field
                                          (merge params
                                                 {:variant "outlined",
                                                  :size "small",
                                                  :error (boolean error),
                                                  :helperText error,
                                                  :autoFocus true})]),
           :onChange (fn [_event new-value] (on-change new-value)),
           :clearOnBlur true,
           :autoHighlight true,
           :selectOnFocus true,
           :disableCloseOnSelect multiple,
           :openOnFocus true}]))])

(defn editable-classification-field
  "Classification field implementation of editable-field for country, region, AOC, and classification"
  [{:keys [field-type app-state wine classifications], :as props}]
  (let [country (:country wine)
        region (:region wine)
        aoc (:aoc wine)
        raw-options
          (case field-type
            :country (formatting/unique-countries classifications)
            :region (formatting/regions-for-country classifications country)
            :aoc (formatting/aocs-for-region classifications country region)
            :classification (formatting/classifications-for-aoc classifications
                                                                country
                                                                region
                                                                aoc)
            [])
        options (into [] (map str) (or raw-options []))]
    [editable-autocomplete-field
     (assoc props
       :options options
       :free-solo true
       :value (if (nil? (:value props)) "" (:value props)))]))

;; Utility functions
(defn format-label
  "Convert a keyword like :producer or :wine-type to a human-readable label"
  [k]
  (-> (name k)
      (str/replace #"-|_" " ")
      (str/capitalize)))

(defn toggle-button
  "A button that toggles a boolean value in app-state"
  [{:keys [app-state path show-text hide-text color variant on-click],
    :or {color "primary",
         variant "contained",
         show-text "Show",
         hide-text "Hide"}}]
  [box {:sx {:mb 2}}
   [button
    {:variant variant,
     :color color,
     :onClick (or on-click #(swap! app-state update-in path not))}
    (if (get-in @app-state path) hide-text show-text)]])

;; Wine card components
(defn get-rating-color
  [rating]
  (cond (>= rating 90) "rating.high"
        (>= rating 80) "rating.medium"
        :else "rating.low"))

(defn wine-thumbnail
  [wine]
  [box
   {:sx {:mr 2,
         :width 120,
         :height 120,
         :display "flex",
         :alignItems "center",
         :justifyContent "center",
         :borderRadius 1,
         :bgcolor "background.default"}}
   (if (:label_thumbnail wine)
     [box
      {:component "img",
       :src (:label_thumbnail wine),
       :sx {:width "100%",
            :height "100%",
            :objectFit "contain",
            :borderRadius 1}}]
     [typography
      {:variant "body2", :color "text.secondary", :sx {:textAlign "center"}}
      "No Image"])])

(defn wine-basic-info
  [wine]
  [box {:sx {:flex 1}}
   [typography
    {:variant "h6",
     :component "h3",
     :sx {:fontSize "1rem",
          :fontWeight "bold",
          :mb 0.3, ;; Reduced margin
          :lineHeight 1.1}} ;; Reduced line height
    (:producer wine)]
   [typography {:variant "body1", :sx {:mb 0.3}} ;; Reduced margin
    (:name wine)]
   [typography {:variant "body2", :color "text.secondary"}
    (if (:vintage wine) (str (:vintage wine)) "NV")]])

(defn wine-header
  [wine]
  [box {:sx {:display "flex", :mb 0}} ;; Removed margin completely
   [wine-thumbnail wine] [wine-basic-info wine]])

(defn wine-region-info
  [wine]
  [grid {:item true, :xs 12}
   [box {:sx {:display "flex", :alignItems "center", :mb 0.3}} ;; Reduced
                                                               ;; margin
    [typography
     {:variant "body2",
      :color "text.secondary",
      :sx {:mr 1, :minWidth "60px"}} "Region:"]
    [typography {:variant "body2"}
     (str (:region wine) (when (:aoc wine) (str " • " (:aoc wine))))]]])

(defn wine-style-info
  [wine]
  [grid {:item true, :xs 6}
   [box {:sx {:display "flex", :alignItems "center"}}
    [typography
     {:variant "body2",
      :color "text.secondary",
      :sx {:mr 1, :minWidth "60px"}} "Style:"]
    [typography {:variant "body2"} (or (:style wine) "-")]]])

(defn wine-classification-info
  [wine]
  [grid {:item true, :xs 6}
   [box {:sx {:display "flex", :alignItems "center"}}
    [typography {:variant "body2", :color "text.secondary", :sx {:mr 1}}
     "Class:"]
    [typography
     {:variant "body2",
      :sx {:whiteSpace "nowrap", :overflow "hidden", :textOverflow "ellipsis"}}
     (or (:classification wine) "-")]]])

(defn wine-location-info
  [wine]
  [grid {:item true, :xs 6}
   [box {:sx {:display "flex", :alignItems "center"}}
    [typography
     {:variant "body2",
      :color "text.secondary",
      :sx {:mr 1, :minWidth "60px"}} "Location:"]
    [typography {:variant "body2"} (or (:location wine) "-")]]])

(defn wine-price-info
  [wine]
  [grid {:item true, :xs 6}
   [box {:sx {:display "flex", :alignItems "center"}}
    [typography {:variant "body2", :color "text.secondary", :sx {:mr 1}}
     "Price:"]
    [typography {:variant "body2"}
     (gstring/format "$%.2f" (or (:price wine) 0))]]])

(defn wine-details-grid
  [wine]
  [grid {:container true, :spacing 0.5} ;; Reduced spacing
   [wine-region-info wine] [wine-style-info wine]
   [wine-classification-info wine] [wine-location-info wine]
   [wine-price-info wine]])

(defn wine-rating-display
  [wine]
  [box {:sx {:display "flex", :alignItems "center"}}
   (if-let [rating (:latest_rating wine)]
     [box {:sx {:display "flex", :alignItems "center"}}
      [typography {:variant "body2", :color "text.secondary", :sx {:mr 1}}
       "Rating:"]
      [typography {:sx {:color (get-rating-color rating), :fontWeight "bold"}}
       (str rating "/100")]]
     [typography {:variant "body2", :color "text.secondary"} "No Rating"])])

(defn wine-tasting-window
  [status drink-from-year drink-until-year]
  [box {:sx {:display "flex", :alignItems "center"}}
   [box
    {:sx {:color (tasting-window-color status),
          :fontWeight "medium",
          :display "flex",
          :alignItems "center"}}
    [typography {:variant "body2", :color "text.secondary", :sx {:mr 1}}
     "Drink:"]
    [box {:sx {:display "flex"}}
     (when drink-from-year
       [typography {:variant "body2", :sx {:lineHeight 1.2}}
        (str drink-from-year)])
     (when (and drink-from-year drink-until-year)
       [typography {:variant "body2", :sx {:lineHeight 1.2, :mx 0.5}} "—"])
     (when drink-until-year
       [typography {:variant "body2", :sx {:lineHeight 1.2}}
        (str drink-until-year)])]]])

(defn wine-bottom-info
  [wine status drink-from-year drink-until-year]
  [box
   {:sx {:mt "auto",
         :pt 0.5, ;; Reduced padding
         :borderTop "1px solid rgba(0,0,0,0.08)",
         :display "flex",
         :justifyContent "space-between",
         :alignItems "center"}} [wine-rating-display wine]
   [wine-tasting-window status drink-from-year drink-until-year]])

(defn wine-quantity-display
  [app-state wine]
  [box {:sx {:display "flex", :alignItems "center"}}
   [typography {:variant "body2", :color "text.secondary", :sx {:mr 1}}
    "Quantity:"] [quantity-control app-state (:id wine) (:quantity wine)]])

(defn wine-action-buttons
  [app-state wine]
  [box {:sx {:display "flex", :gap 1}}
   [button
    {:variant "outlined",
     :color "primary",
     :size "small",
     :onClick #(do (swap! app-state assoc :selected-wine-id (:id wine))
                   (swap! app-state assoc :new-tasting-note {})
                   (api/fetch-tasting-notes app-state (:id wine))
                   (api/fetch-wine-details app-state (:id wine)))} "View"]
   [button
    {:variant "outlined",
     :color "error",
     :size "small",
     :onClick #(api/delete-wine app-state (:id wine))} "Delete"]])

(defn wine-controls
  [app-state wine]
  [box
   {:sx {:display "flex",
         :justifyContent "space-between",
         :alignItems "center",
         :mt 1}} ;; Reduced margin
   [wine-quantity-display app-state wine] [wine-action-buttons app-state wine]])

(defn wine-card
  [app-state wine]
  (let [status (tasting-window-status wine)
        drink-from-year (or (:drink_from_year wine)
                            (when-let [date (:drink_from wine)]
                              (.getFullYear (js/Date. date))))
        drink-until-year (or (:drink_until_year wine)
                             (when-let [date (:drink_until wine)]
                               (.getFullYear (js/Date. date))))]
    [paper
     {:elevation 2,
      :sx
        {:p 1.5, ;; Reduced padding
         :mb 2,
         :borderRadius 2,
         :position "relative",
         :overflow "hidden",
         :transition "transform 0.2s, box-shadow 0.2s",
         :height "100%",
         :display "flex",
         :flexDirection "column",
         :justifyContent "space-between",
         :bgcolor "background.paper",
         :backgroundImage
           (when (= (:style wine) "Red")
             "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))"),
         ":hover" {:transform "translateY(-2px)", :boxShadow 4}}}
     ;; Wine header with thumbnail and basic info
     [wine-header wine]
     ;; Wine details
     [box {:sx {:mb 0}} ;; Removed margin completely
      [wine-details-grid wine]
      ;; Bottom section with rating, tasting window
      [wine-bottom-info wine status drink-from-year drink-until-year]
      ;; Quantity control and action buttons
      [wine-controls app-state wine]]])))
