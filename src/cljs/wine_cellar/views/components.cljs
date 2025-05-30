(ns wine-cellar.views.components
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
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
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [wine-cellar.utils.formatting :as formatting]
            [wine-cellar.api :as api]))

;; Shared styles
(def form-field-style {:min-width "180px" :width "75%"})

;; Quantity control component
(defn quantity-control
  [app-state wine-id quantity]
  [box {:display "flex" :alignItems "center"}
   [box
    {:component "span"
     :sx {:fontSize "1rem" :mx 1 :minWidth "1.5rem" :textAlign "center"}}
    quantity]
   [box {:display "flex" :flexDirection "column" :ml 0.5}
    [button
     {:variant "text"
      :size "small"
      :sx {:minWidth 0 :p 0 :lineHeight 0.8}
      :onClick #(api/adjust-wine-quantity app-state wine-id 1)}
     [arrow-drop-up {:fontSize "small"}]]
    [button
     {:variant "text"
      :size "small"
      :sx {:minWidth 0 :p 0 :lineHeight 0.8}
      :disabled (= quantity 0)
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
  [{:keys [value on-save validate-fn empty-text render-input-fn]
    :or {empty-text "Not specified"}}]
  (let [editing (r/atom false)
        field-value (r/atom value)
        field-error (r/atom nil)
        saving (r/atom false)]
    (fn [{:keys [value on-save validate-fn empty-text render-input-fn]
          :or {empty-text "Not specified"}}]
      (tap> ["field-value" @field-value])
      (if @editing
        ;; Edit mode
        [box {:display "flex" :flexDirection "column" :width "100%"}
         [box {:display "flex" :alignItems "center"}
          [box {:sx {:flex 1 :mr 1}}
           (render-input-fn @field-value
                            (fn [new-value]
                              (reset! field-value new-value)
                              (reset! field-error nil))
                            @field-error)]
          [icon-button
           {:color "primary"
            :size "small"
            :disabled (or (boolean @field-error) @saving)
            :onClick (fn []
                       (let [error (when validate-fn
                                     (validate-fn @field-value))]
                         (if error
                           (reset! field-error error)
                           (do (reset! saving true)
                               (-> (on-save @field-value)
                                   (.then #(do (reset! saving false)
                                               (reset! editing false)))
                                   (.catch #(do (reset! saving false)
                                                (reset! field-error
                                                  "Save failed"))))))))}
           (if @saving [circular-progress {:size 20}] [save])]
          [icon-button
           {:color "secondary"
            :size "small"
            :disabled @saving
            :onClick (fn []
                       (reset! field-value value)
                       (reset! field-error nil)
                       (reset! editing false))} [cancel]]]]
        ;; View mode
        [box
         {:display "flex"
          :alignItems "center"
          :justifyContent "space-between"
          :width "100%"}
         [typography {:variant "body1"}
          (if (or (nil? value) (str/blank? value)) empty-text value)]
         [icon-button
          {:color "primary" :size "small" :onClick #(reset! editing true)}
          [edit]]]))))

  ;; Specific field implementations
(defn editable-text-field
  "Standard text field implementation of editable-field"
  [{:keys [text-field-props] :as props}]
  [editable-field-wrapper
   (assoc props
          :render-input-fn
          (fn [value on-change error]
            [text-field
             (merge {:value value
                     :size "small"
                     :fullWidth true
                     :autoFocus true
                     :error (boolean error)
                     :helperText error
                     :onChange (fn [e] (on-change (.. e -target -value)))}
                    text-field-props)]))])

(defn editable-autocomplete-field
  "Autocomplete implementation of editable-field"
  [{:keys [options option-label free-solo multiple] :as props}]
  [editable-field-wrapper
   (assoc props
          :render-input-fn
          (fn [value on-change error]
            [autocomplete
             {:multiple (boolean multiple)
              :freeSolo (boolean free-solo)
              :options options
              :size "small"
              :value (cond-> value multiple (or []))
              :getOptionLabel (or option-label
                                  (fn [option]
                                    (cond (nil? option) ""
                                          (string? option) option
                                          :else (str option))))
              :renderInput (react-component [params]
                                            [text-field
                                             (merge params
                                                    {:variant "outlined"
                                                     :size "small"
                                                     :error (boolean error)
                                                     :helperText error
                                                     :autoFocus true})])
              :onChange (fn [_event new-value] (on-change new-value))
              :clearOnBlur false
              :autoHighlight true
              :selectOnFocus true
              :disableCloseOnSelect multiple
              :openOnFocus true}]))])

(defn editable-classification-field
  "Classification field implementation of editable-field for country, region, AOC, and classification"
  [{:keys [field-type app-state wine classifications] :as props}]
  (let [country (:country wine)
        region (:region wine)
        aoc (:aoc wine)
        classification (:classification wine)
        raw-options
        (case field-type
          :country (formatting/unique-countries classifications)
          :region (formatting/regions-for-country classifications country)
          :aoc (formatting/aocs-for-region classifications country region)
          :classification (formatting/classifications-for-aoc classifications
                                                              country
                                                              region
                                                              aoc)
          :level (formatting/levels-for-classification classifications
                                                       country
                                                       region
                                                       aoc
                                                       classification)
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
  [{:keys [app-state path show-text hide-text color variant on-click]
    :or
    {color "primary" variant "contained" show-text "Show" hide-text "Hide"}}]
  [box {:sx {:mb 2}}
   [button
    {:variant variant
     :color color
     :onClick (or on-click #(swap! app-state update-in path not))}
    (if (get-in @app-state path) hide-text show-text)]])
