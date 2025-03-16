(ns wine-cellar.views.components.form
  (:require [reagent.core :as r]
            [wine-cellar.views.components :refer [form-field-style]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.checkbox :refer [checkbox]]
            [reagent-mui.material.form-control-label :refer [form-control-label]]
            [reagent-mui.material.form-helper-text :refer [form-helper-text]]
            [reagent-mui.material.radio :refer [radio]]
            [reagent-mui.material.radio-group :refer [radio-group]]
            [reagent-mui.material.switch :refer [switch]]
            [reagent-mui.material.slider :refer [slider]]
            [reagent-mui.material.paper :refer [paper]]))

;; Enhanced form components

(defn form-container
  "A container for forms with consistent styling and a title"
  [{:keys [title elevation on-submit]} & children]
  [paper {:elevation (or elevation 1)
          :sx {:p 4
               :borderRadius 2
               :mb 4
               :position "relative"
               :overflow "hidden"
               :backgroundImage "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))"}}
   [:form {:on-submit (fn [e]
                        (.preventDefault e)
                        (when on-submit (on-submit)))}
    [grid {:container true :spacing 2}
     [grid {:item true :xs 12}
      [typography {:variant "h5"
                   :component "h2"
                   :sx {:mb 3
                        :pb 1
                        :borderBottom "1px solid rgba(0,0,0,0.08)"
                        :color "primary.main"}}
       title]]
     (map-indexed
      (fn [idx child]
        ^{:key (str "form-item-" idx)}
        [grid {:item true :xs 12}
         child])
      children)]]])

(defn form-actions
  "Standard form action buttons (submit, cancel) with consistent styling"
  [{:keys [on-submit on-cancel submit-text cancel-text disabled]}]
  [grid {:item true :xs 12 :sx {:display "flex" :justifyContent "flex-end" :mt 3}}
   (when on-cancel
     [button
      {:variant "outlined"
       :color "secondary"
       :onClick on-cancel
       :sx {:mr 2}}
      (or cancel-text "Cancel")])
   [button
    {:type "submit"
     :variant "contained"
     :color "primary"
     :disabled disabled
     :onClick (when on-submit on-submit)}
    (or submit-text "Submit")]])

(defn text-area-field
  "A multi-line text field with consistent styling"
  [{:keys [label value on-change required rows helper-text error]}]
  [mui-text-field/text-field
   {:label label
    :multiline true
    :rows (or rows 4)
    :required required
    :fullWidth true
    :value value
    :error error
    :helperText helper-text
    :sx form-field-style
    :variant "outlined"
    :onChange #(on-change (.. % -target -value))}])

(defn text-field
  "A single-line text field with consistent styling"
  [{:keys [label value on-change required helper-text error]}]
  [mui-text-field/text-field
   {:label label
    :required required
    :fullWidth true
    :value (or value "")
    :error error
    :helperText helper-text
    :sx form-field-style
    :variant "outlined"
    :onChange #(when on-change (on-change (.. % -target -value)))}])

(defn checkbox-field
  "A checkbox with label and optional helper text"
  [{:keys [label checked on-change helper-text]}]
  [form-control {:component "fieldset" :sx {:mt 2}}
   [form-control-label
    {:control (r/as-element
               [checkbox
                {:checked (boolean checked)
                 :onChange #(on-change (.. % -target -checked))}])
     :label label}]
   (when helper-text
     [form-helper-text helper-text])])

(defn radio-group-field
  "A group of radio buttons with a label"
  [{:keys [label value options on-change required row]}]
  [form-control {:required required :sx {:mt 2}}
   [input-label label]
   [radio-group
    {:value (or value "")
     :row (boolean row)
     :onChange #(on-change (.. % -target -value))}
    (for [[k v] options]
      ^{:key k}
      [form-control-label
       {:value k
        :control (r/as-element [radio])
        :label v}])]])

(defn switch-field
  "A toggle switch with label"
  [{:keys [label checked on-change helper-text]}]
  [form-control {:component "fieldset" :sx {:mt 2}}
   [form-control-label
    {:control (r/as-element
               [switch
                {:checked (boolean checked)
                 :onChange #(on-change (.. % -target -checked))}])
     :label label}]
   (when helper-text
     [form-helper-text helper-text])])

(defn slider-field
  "A slider input with min/max values and optional step"
  [{:keys [label value min max step on-change marks disabled]}]
  [box {:sx {:width "100%" :mt 3 :mb 2}}
   [typography {:gutterBottom true} label]
   [slider
    {:value (or value min)
     :min min
     :max max
     :step (or step 1)
     :marks (boolean marks)
     :disabled disabled
     :valueLabelDisplay "auto"
     :onChange #(on-change (.. % -target -value))}]])

(defn form-row
  "A row in a form with consistent spacing"
  [& children]
  [grid {:container true :spacing 2 :sx {:mb 2}}
   (map-indexed
    (fn [idx child]
      ^{:key (str "form-row-item-" idx)}
      [grid {:item true :xs 12 :md (int (/ 12 (count children)))}
       child])
    children)])

(defn form-divider
  "A visual divider between form sections"
  [title]
  [grid {:item true :xs 12 :sx {:mt 3 :mb 1}}
   [typography {:variant "subtitle1" :sx {:fontWeight "bold"}} title]])

(defn validation-message
  "Display a validation error message"
  [message]
  (when message
    [typography {:variant "caption" :color "error" :sx {:mt 1}}
     message]))

(defn number-field
  "A specialized input for numeric values with min/max/step"
  [{:keys [label value on-change required min max step helper-text error]}]
  [mui-text-field/text-field
   {:label label
    :type "number"
    :required required
    :value value
    :error error
    :helperText helper-text
    :margin "normal"
    :fullWidth false
    :variant "outlined"
    :sx form-field-style
    :InputProps (cond-> {}
                  min (assoc :min min)
                  max (assoc :max max)
                  step (assoc :step step))
    :on-change #(on-change (.. % -target -value))}])

(defn currency-field
  "A specialized input for currency values"
  [{:keys [label value on-change required helper-text error]}]
  [mui-text-field/text-field
   {:label label
    :type "number"
    :required required
    :value value
    :error error
    :helperText helper-text
    :margin "normal"
    :fullWidth false
    :variant "outlined"
    :sx form-field-style
    :InputProps {:startAdornment "$"
                 :step "0.01"
                 :min "0"}
    :on-change #(on-change (.. % -target -value))}])
