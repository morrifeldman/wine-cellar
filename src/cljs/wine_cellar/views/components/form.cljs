(ns wine-cellar.views.components.form
  (:require
    [reagent.core :as r]
    [wine-cellar.views.components :refer [form-field-style format-label]]
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
    [reagent-mui.material.paper :refer [paper]]
    [reagent-mui.material.autocomplete :refer [autocomplete]]
    [reagent-mui.material.circular-progress :refer [circular-progress]]
    [reagent-mui.util :refer [react-component]]))

;; Form container components
(defn form-container
  "A container for forms with consistent styling and a title"
  [{:keys [title elevation on-submit]} & children]
  [paper
   {:elevation (or elevation 1)
    :sx {:p 2 ;; Reduced from p 4
         :borderRadius 2
         :mb 2 ;; Reduced from mb 4
         :position "relative"
         :overflow "hidden"
         :backgroundImage
         "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))"
         :borderLeft "4px solid rgba(114,47,55,0.5)"}}
   [:form {:on-submit (fn [e] (.preventDefault e) (when on-submit (on-submit)))}
    [grid {:container true :spacing 1} ;; Reduced from spacing 2
     [grid {:item true :xs 12}
      [typography
       {:variant "h5"
        :component "h2"
        :sx {:mb 1.5 ;; Reduced from mb 3
             :pb 0.5 ;; Reduced from pb 1
             :borderBottom "1px solid rgba(0,0,0,0.08)"
             :color "primary.main"
             :display "flex"
             :alignItems "center"}}
       [box
        {:component "span"
         :sx {:width "6px" ;; Reduced from 8px
              :height "6px" ;; Reduced from 8px
              :borderRadius "50%"
              :backgroundColor "primary.main"
              :display "inline-block"
              :mr 1}}] title]]
     (map-indexed (fn [idx child]
                    ^{:key (str "form-item-" idx)}
                    [grid {:item true :xs 12} child])
                  children)]]])

(defn form-actions
  "Standard form action buttons (submit, cancel) with consistent styling"
  [{:keys [on-submit on-cancel submit-text cancel-text disabled loading?]}]
  [grid
   {:item true
    :xs 12
    :sx {:display "flex"
         :justifyContent "flex-end"
         :mt 2 ;; Reduced from mt 4
         :pt 1 ;; Reduced from pt 2
         :borderTop "1px solid rgba(0,0,0,0.08)"}}
   (when on-cancel
     [button
      {:variant "outlined"
       :color "secondary"
       :onClick on-cancel
       :disabled loading?
       :size "small" ;; Added small size
       :sx {:mr 1.5 ;; Reduced from mr 2
            :px 2}} ;; Reduced from px 3
      (or cancel-text "Cancel")])
   [button
    {:type "submit"
     :variant "contained"
     :color "primary"
     :size "small" ;; Added small size
     :disabled (or disabled loading?)
     :sx {:px 2} ;; Reduced from px 3
     :startIcon (when loading?
                  (r/as-element [circular-progress
                                 {:size 16 :color "inherit"}]))
     :onClick (when on-submit on-submit)}
    (if loading? "Submitting..." (or submit-text "Submit"))]])

(defn form-row
  "A row in a form with consistent spacing"
  [& children]
  [grid
   {:container true
    :spacing 1.5 ;; Reduced from spacing 2
    :sx {:mb 1 ;; Reduced from mb 2
         :animation "fadeIn 0.3s ease-in-out"
         "@keyframes fadeIn" {:from {:opacity 0 :transform "translateY(5px)"} ;; Reduced
                                                                              ;; from
                                                                              ;; 10px
                              :to {:opacity 1 :transform "translateY(0)"}}}}
   (map-indexed (fn [idx child]
                  ^{:key (str "form-row-item-" idx)}
                  [grid {:item true :xs 12 :md (int (/ 12 (count children)))}
                   child])
                children)])

(defn form-divider
  "A visual divider between form sections"
  [title]
  [grid {:item true :xs 12 :sx {:mt 2 :mb 1}} ;; Reduced from mt 4 mb 2
   [box {:sx {:display "flex" :alignItems "center"}}
    [box
     {:sx {:flex "0 0 auto"
           :mr 1.5 ;; Reduced from mr 2
           :height "18px" ;; Reduced from 24px
           :width "3px" ;; Reduced from 4px
           :backgroundColor "secondary.main"
           :borderRadius "2px"}}]
    [typography
     {:variant "subtitle1"
      :sx {:fontWeight "bold" :color "text.primary" :fontSize "0.9rem"}} ;; Added
                                                                         ;; smaller
                                                                         ;; font
                                                                         ;; size
     title]
    [box
     {:sx {:flex "1 1 auto"
           :ml 1.5 ;; Reduced from ml 2
           :height "1px"
           :backgroundColor "divider"}}]]])

;; Input field components
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
    :size "small" ;; Added small size
    :margin "dense" ;; Added dense margin
    :sx (merge form-field-style
               {:transition "all 0.2s ease-in-out"
                ":hover" {:backgroundColor "rgba(0,0,0,0.01)"}})
    :variant "outlined"
    :onChange #(when on-change (on-change (.. % -target -value)))}])

(defn text-area-field
  "A multi-line text field with consistent styling"
  [{:keys [label value on-change required rows helper-text error]}]
  [mui-text-field/text-field
   {:label label
    :multiline true
    :rows (or rows 3) ;; Reduced from 4
    :required required
    :fullWidth true
    :value value
    :error error
    :helperText helper-text
    :size "small" ;; Added small size
    :margin "dense" ;; Added dense margin
    :sx form-field-style
    :variant "outlined"
    :onChange #(on-change (.. % -target -value))}])

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
    :margin "dense" ;; Changed from normal to dense
    :size "small" ;; Added small size
    :fullWidth false
    :variant "outlined"
    :sx form-field-style
    :InputLabelProps {:shrink true}
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
    :margin "dense" ;; Changed from normal to dense
    :size "small" ;; Added small size
    :fullWidth false
    :variant "outlined"
    :sx form-field-style
    :InputProps {:startAdornment "$" :step "0.01" :min "0"}
    :on-change #(on-change (.. % -target -value))}])

(defn date-field
  "A date input field with consistent styling"
  [{:keys [label required value on-change]}]
  [mui-text-field/text-field
   {:label label
    :type "date"
    :required required
    :value value
    :margin "dense" ;; Changed from normal to dense
    :size "small" ;; Added small size
    :fullWidth false
    :variant "outlined"
    :InputLabelProps {:shrink true}
    :sx form-field-style
    :on-change #(on-change (.. % -target -value))}])

(defn select-field
  "A dropdown select field with autocomplete"
  [{:keys [label value options required on-change multiple disabled free-solo
           helper-text on-blur is-option-equal-to-value]
    :or {multiple false disabled false free-solo false
         is-option-equal-to-value #(= (js->clj %1) (js->clj %2))}}]
  [form-control
   {:variant "outlined" :margin "dense" :required required :sx form-field-style}
   [autocomplete
    {:multiple multiple
     :disabled disabled
     :options options
     :freeSolo free-solo
     :is-option-equal-to-value is-option-equal-to-value
     :size "small"
     :value (cond-> value multiple (or []))
     :get-option-label (fn [option]
                         (cond (nil? option) ""
                               (string? option) option
                               (object? option) (or (.-label option) "")
                               :else (str option)))
     :render-input (react-component [props]
                                    [mui-text-field/text-field
                                     (merge props
                                            {:label label
                                             :variant "outlined"
                                             :size "small"
                                             :required (if multiple
                                                         ;; workaround for
                                                         ;; bug
                                                         ;; https://github.com/mui/material-ui/issues/21663
                                                         (= (count value) 0)
                                                         required)
                                             :helperText helper-text})])
     :on-change (fn [_event new-value] (on-change new-value))
     :on-input-change (when free-solo
                        (fn [_event new-value reason]
                          (when (= reason "input") (on-change new-value))))
     :clear-on-blur true ;; Fix free solo mode holding onto values
     :auto-highlight true
     :auto-select false
     :select-on-focus true
     :disable-close-on-select multiple
     :open-on-focus true
     :blur-on-select "touch"
     :on-blur on-blur}]])

(defn year-field
  [{:keys [value] :as props}]
  (if value
    [number-field
     (select-keys props
                  [:label :value :on-change :required :min :max :step
                   :helper-text :error])]
    [select-field
     (select-keys props
                  [:label :value :on-change :required :options :multiple
                   :disabled :free-solo :helper-text :on-blur])]))

;; Smart field components
(defn smart-field
  "A versatile form field that derives its label from the last part of the path"
  [app-state path &
   {:keys [label type required min max step component]
    :or {type "text" component text-field}}]
  (let [derived-label (format-label (last path))
        field-label (or label derived-label)
        field-value (get-in @app-state path)
        props (cond-> {:label field-label
                       :value field-value
                       :on-change #(swap! app-state assoc-in path %)}
                type (assoc :type type)
                required (assoc :required required)
                min (assoc :min min)
                max (assoc :max max)
                step (assoc :step step))]
    [component props]))

(defn smart-select-field
  "A smart select field that updates app-state on change"
  [app-state path &
   {:keys [label options disabled on-change required free-solo helper-text
           on-blur]
    :or {required false disabled false free-solo false}}]
  (let [derived-label (format-label (last path))
        field-label (or label derived-label)
        field-value (get-in @app-state path)
        on-change-fn (or on-change #(swap! app-state assoc-in path %))]
    [select-field
     {:multiple false
      :label field-label
      :value field-value
      :required required
      :disabled disabled
      :options options
      :free-solo free-solo
      :helper-text helper-text
      :on-blur on-blur
      :on-change on-change-fn}]))

;; Other form components
(defn checkbox-field
  "A checkbox with label and optional helper text"
  [{:keys [label checked on-change helper-text]}]
  [form-control {:component "fieldset" :sx {:mt 1}} ;; Reduced from mt 2
   [form-control-label
    {:control (r/as-element [checkbox
                             {:checked (boolean checked)
                              :size "small" ;; Added small size
                              :onChange #(on-change (.. % -target -checked))}])
     :label label}] (when helper-text [form-helper-text helper-text])])

(defn radio-group-field
  "A group of radio buttons with a label"
  [{:keys [label value options on-change required row]}]
  [form-control {:required required :sx {:mt 1}} ;; Reduced from mt 2
   [input-label {:size "small"} label] ;; Added small size
   [radio-group
    {:value (or value "")
     :row (boolean row)
     :onChange #(on-change (.. % -target -value))}
    (for [[k v] options]
      ^{:key k}
      [form-control-label
       {:value k
        :control (r/as-element [radio {:size "small"}]) ;; Added small size
        :label v}])]])

(defn switch-field
  "A toggle switch with label"
  [{:keys [label checked on-change helper-text]}]
  [form-control {:component "fieldset" :sx {:mt 1}} ;; Reduced from mt 2
   [form-control-label
    {:control (r/as-element [switch
                             {:checked (boolean checked)
                              :size "small" ;; Added small size
                              :onChange #(on-change (.. % -target -checked))}])
     :label label}] (when helper-text [form-helper-text helper-text])])

(defn slider-field
  "A slider input with min/max values and optional step"
  [{:keys [label value min max step on-change marks disabled]}]
  [box {:sx {:width "100%" :mt 2 :mb 1}} ;; Reduced from mt 3 mb 2
   [typography {:gutterBottom true :variant "body2"} label] ;; Added body2
                                                            ;; for smaller
                                                            ;; text
   [slider
    {:value (or value min)
     :min min
     :max max
     :step (or step 1)
     :marks (boolean marks)
     :disabled disabled
     :size "small" ;; Added small size
     :valueLabelDisplay "auto"
     :onChange #(on-change (.. % -target -value))}]])

(defn validation-message
  "Display a validation error message"
  [message]
  (when message
    [typography {:variant "caption" :color "error" :sx {:mt 1}} message]))
