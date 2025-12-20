(ns wine-cellar.views.components.technical-data
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [cljs.core.async :refer [go <!]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.edit :refer [edit]]
            [reagent-mui.icons.save :refer [save]]
            [reagent-mui.icons.cancel :refer [cancel]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.autocomplete :refer [autocomplete]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.grid :refer [grid]]
            [wine-cellar.common :as common]
            [wine-cellar.api :as api]
            [wine-cellar.utils.mui :refer [safe-js-props]]))

(defn- normalize-input-key
  [input]
  (-> input
      str/trim
      str/lower-case
      (str/replace #"\s+" "-")
      keyword))

(defn inline-editor
  [{:keys [value on-save on-delete]}]
  (r/with-let
   [editing? (r/atom false) temp-value (r/atom value)]
   (if @editing?
     ;; Edit Mode
     [box {:display "flex" :alignItems "flex-start" :width "100%"}
      [text-field
       {:full-width true
        :variant "outlined"
        :size "small"
        :multiline true
        :value @temp-value
        :auto-focus true
        :on-change #(reset! temp-value (.. % -target -value))}]
      [box {:display "flex" :flexDirection "column" :ml 0.5}
       [icon-button
        {:color "primary"
         :size "small"
         :on-click #(do (on-save @temp-value) (reset! editing? false))} [save]]
       [icon-button
        {:color "secondary"
         :size "small"
         :on-click #(do (reset! temp-value value) (reset! editing? false))}
        [cancel]]]]
     ;; View Mode
     [box
      {:display "flex"
       :alignItems "center"
       :justifyContent "space-between"
       :width "100%"}
      [typography {:variant "body2" :sx {:flex 1 :mr 1}} (str value)]
      [box {:display "flex" :gap 0.5}
       [icon-button
        {:color "primary" :size "small" :on-click #(reset! editing? true)}
        [edit]]
       [icon-button {:color "error" :size "small" :on-click on-delete}
        [delete]]]])))

(defn technical-data-editor
  "Component to edit arbitrary metadata fields.
               Props:
                 :metadata - The current metadata map (or nil)
                 :on-change - Function called with updated metadata map"
  []
  (let [new-key-input (r/atom "")
        new-value (r/atom "")
        base-keys (mapv common/humanize-key common/technical-data-keys)
        suggested-options (r/atom base-keys)]
    (go (let [result (<! (api/GET "/api/wines/technical-data-keys"
                                  "Failed to fetch technical data keys"))]
          (when (:success result)
            (let [dynamic-keys (:data result)
                  combined (distinct (concat base-keys
                                             (map common/humanize-key
                                                  dynamic-keys)))]
              (reset! suggested-options (sort combined))))))
    (fn [{:keys [metadata on-change]}]
      (let [metadata (if (and metadata (not (map? metadata)))
                       (js->clj metadata :keywordize-keys true)
                       metadata)
            handle-delete (fn [k] (on-change (dissoc metadata k)))
            handle-save (fn [k v] (on-change (assoc metadata k v)))
            handle-add (fn []
                         (let [k (normalize-input-key @new-key-input)
                               v @new-value]
                           (when (and (not (str/blank? (name k)))
                                      (not (str/blank? v)))
                             (on-change (assoc metadata k v))
                             (reset! new-key-input "")
                             (reset! new-value ""))))
            ;; Sort metadata by key for consistent display
            sorted-entries (sort-by (comp str key) metadata)]
        [box {:sx {:mt 2}}
         [typography {:variant "subtitle2" :sx {:mb 1}}
          "Technical Data & Notes"]
         ;; Existing Entries
         (when (seq sorted-entries)
           [box {:sx {:mb 2}}
            (for [[k v] sorted-entries]
              ^{:key (str k)}
              [grid
               {:container true :spacing 2 :sx {:mb 1 :alignItems "center"}}
               [grid {:item true :xs 4}
                [typography
                 {:variant "body2"
                  :sx {:fontWeight "bold" :color "text.secondary"}}
                 (common/humanize-key k)]]
               [grid {:item true :xs 8}
                [inline-editor
                 {:value v
                  :on-save #(handle-save k %)
                  :on-delete #(handle-delete k)}]]])])
         ;; Add New Entry
         [grid {:container true :spacing 2 :sx {:alignItems "flex-start"}}
          [grid {:item true :xs 4}
           [autocomplete
            {:free-solo true
             :options @suggested-options
             :value @new-key-input
             :on-input-change (fn [_ v] (reset! new-key-input v))
             :render-input (fn [params]
                             (r/as-element [text-field
                                            (merge (safe-js-props params)
                                                   {:label "Field"
                                                    :variant "outlined"
                                                    :size "small"})]))}]]
          [grid {:item true :xs 6}
           [text-field
            {:full-width true
             :label "Value"
             :variant "outlined"
             :size "small"
             :multiline true
             :maxRows 4
             :value @new-value
             :on-change #(reset! new-value (.. % -target -value))
             :on-key-down (fn [e]
                            (when (and (= (.-key e) "Enter")
                                       (not (.-shiftKey e)))
                              (.preventDefault e)
                              (handle-add)))}]]
          [grid {:item true :xs 2}
           [button
            {:variant "contained"
             :color "primary"
             :size "medium"
             :disabled (or (str/blank? @new-key-input) (str/blank? @new-value))
             :on-click handle-add} "Add"]]]]))))
