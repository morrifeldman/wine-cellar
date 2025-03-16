(ns wine-cellar.views.components
  (:require [clojure.string :as str]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.table-cell :refer [table-cell]]
            [reagent-mui.material.table-sort-label :refer [table-sort-label]]
            [reagent-mui.icons.arrow-drop-up :refer [arrow-drop-up]]
            [reagent-mui.icons.arrow-drop-down :refer [arrow-drop-down]]
            [reagent-mui.material.autocomplete :refer [autocomplete]]
            [reagent-mui.util :refer [react-component]]
            [wine-cellar.api :as api]))

;; Shared styles
(def form-field-style {:min-width "200px" :width "75%"})

(defn input-field [{:keys [label type required value on-change min step]}]
  [text-field
   {:label label
    :type type
    :required required
    :value value
    :margin "normal"
    :fullWidth false
    :variant "outlined"
    :sx form-field-style
    :InputProps (cond-> {}
                  min (assoc :min min)
                  step (assoc :step step))
    :on-change #(on-change (.. % -target -value))}])

(defn select-field [{:keys [label value options required on-change multiple disabled]
                           :or {multiple false disabled false} :as input}]
  [form-control {:variant "outlined"
                 :margin "normal"
                 :required required
                 :sx form-field-style}
   [autocomplete
    {:multiple multiple
     :disabled disabled
     :options options
     :value (cond-> value
              multiple (or []))
     :getOptionLabel (fn [option]
                       (cond
                         (nil? option) ""
                         (string? option) option
                         :else (str option)))
     :renderInput (react-component
                    [props]
                    [text-field (merge props
                                       {:label label
                                        :variant "outlined"
                                        :required required})])

     :onChange (fn [_event new-value] (on-change new-value))
     :autoHighlight true
     :autoSelect false
     :selectOnFocus true
     :blurOnSelect "touch"
     :disableCloseOnSelect multiple}]])

(defn quantity-control [app-state wine-id quantity]
  [box {:display "flex" :alignItems "center"}
   [box {:component "span"
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

(defn format-label
  "Convert a keyword like :producer or :wine-type to a human-readable label"
  [k]
  (-> (name k)
      (str/replace #"-|_" " ")
      (str/capitalize)))

(defn smart-field
  "A versatile form field that derives its label from the last part of the path"
  [app-state path & {:keys [label type required min max step component]
                     :or {type "text"
                          component input-field}}]
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

(defn date-field [{:keys [label required value on-change]}]
  [text-field
   {:label label
    :type "date"
    :required required
    :value value
    :margin "normal"
    :fullWidth false
    :variant "outlined"
    :InputLabelProps {:shrink true} ;; This is the key fix
    :sx form-field-style
    :on-change #(on-change (.. % -target -value))}])

(defn form-section [title]
  [grid {:item true :xs 12}
   [typography {:variant "subtitle1" :sx {:fontWeight "bold" :mt 2}} title]])

(defn smart-select-field
  [app-state path & {:keys [label options disabled on-change required]
                     :or {required false
                          disabled false}}]
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
      :on-change on-change-fn}]))

;; Table components
(defn sortable-header [app-state label field]
  (let [sort-state (:sort @app-state)
        current-field (:field sort-state)
        current-direction (:direction sort-state)
        is-active (= field current-field)
        direction (if (= :asc current-direction) "asc" "desc")]
    [table-cell
     {:align "left"
      :sx {:font-weight "bold"}}
     [table-sort-label
      (cond-> {:active is-active
               :onClick #(swap! app-state update :sort
                                (fn [sort]
                                  (if (= field (:field sort))
                                    {:field field
                                     :direction (if (= :asc (:direction sort)) :desc :asc)}
                                    {:field field :direction :asc})))}
        is-active (assoc :direction direction))
      label]]))
