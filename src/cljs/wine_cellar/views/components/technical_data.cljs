(ns wine-cellar.views.components.technical-data
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [cljs.core.async :refer [go <!]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.autocomplete :refer [autocomplete]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.tooltip :refer [tooltip]]
            [reagent-mui.icons.add :refer [add]]
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

(defn- field-edit-modal
  [entry open-entry metadata on-change]
  (r/with-let
   [val-atom (r/atom (:value entry))]
   (let [close #(reset! open-entry nil)]
     [dialog {:open true :onClose close :maxWidth "sm" :fullWidth true}
      [dialog-title (common/humanize-key (:key entry))]
      [dialog-content
       [box {:sx {:pt 1}}
        [text-field
         {:value @val-atom
          :multiline true
          :rows 4
          :fullWidth true
          :size "small"
          :autoFocus true
          :onChange #(reset! val-atom (.. % -target -value))}]]]
      [dialog-actions
       [button
        {:color "error"
         :sx {:mr "auto"}
         :onClick #(do (on-change (dissoc metadata (:key entry))) (close))}
        "Delete"] [button {:onClick close} "Cancel"]
       [button
        {:variant "contained"
         :onClick #(do (on-change (assoc metadata (:key entry) @val-atom))
                       (close))} "Save"]]])))
(defn technical-data-editor
  "Component to edit arbitrary metadata fields.
               Props:
                 :metadata - The current metadata map (or nil)
                 :on-change - Function called with updated metadata map"
  []
  (let [new-key-input (r/atom "")
        new-value (r/atom "")
        open-entry (r/atom nil)
        add-open? (r/atom false)
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
         (when @open-entry
           [field-edit-modal @open-entry open-entry metadata on-change])
         ;; Existing Entries
         (when (seq sorted-entries)
           [box {:sx {:mb 3}}
            (for [[k v] sorted-entries]
              ^{:key (str k)}
              [box
               {:sx {:mb 2
                     :pb 1
                     :borderBottom "1px solid rgba(255,255,255,0.05)"
                     :cursor "pointer"
                     :px 0.5
                     :mx -0.5
                     :borderRadius 1
                     "&:hover" {:bgcolor "action.hover"}}
                :onClick #(reset! open-entry {:key k :value v})}
               [typography
                {:variant "caption"
                 :sx {:fontWeight "bold"
                      :color "text.secondary"
                      :textTransform "uppercase"
                      :display "block"
                      :mb 0.5
                      :letterSpacing "0.05em"}} (common/humanize-key k)]
               [typography {:variant "body2" :sx {:whiteSpace "pre-wrap"}}
                (str v)]])])
         ;; Add button
         [tooltip {:title "Add technical data" :placement "right" :arrow true}
          [button
           {:size "small"
            :sx {:mt 1 :color "text.secondary" :minWidth 0 :p 0.5}
            :on-click #(reset! add-open? true)} [add {:fontSize "small"}]]]
         ;; Add New Entry modal
         [dialog
          {:open @add-open?
           :onClose #(reset! add-open? false)
           :maxWidth "sm"
           :fullWidth true} [dialog-title "Add Technical Data"]
          [dialog-content
           [box {:sx {:pt 1 :display "flex" :flexDirection "column" :gap 2}}
            [autocomplete
             {:free-solo true
              :options @suggested-options
              :value @new-key-input
              :on-input-change (fn [_ v] (reset! new-key-input v))
              :render-input (fn [params]
                              (r/as-element [text-field
                                             (merge (safe-js-props params)
                                                    {:label "Field Name"
                                                     :variant "outlined"
                                                     :placeholder
                                                     "e.g. Soil Type"
                                                     :size "small"
                                                     :autoFocus true})]))}]
            [text-field
             {:full-width true
              :label "Value"
              :variant "outlined"
              :size "small"
              :multiline true
              :maxRows 8
              :value @new-value
              :on-change #(reset! new-value (.. % -target -value))}]]]
          [dialog-actions
           [button {:on-click #(reset! add-open? false)} "Cancel"]
           [button
            {:variant "contained"
             :disabled (or (str/blank? @new-key-input) (str/blank? @new-value))
             :on-click (fn [] (handle-add) (reset! add-open? false))}
            "Add"]]]]))))
