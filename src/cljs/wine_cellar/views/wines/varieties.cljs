(ns wine-cellar.views.wines.varieties
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.list :refer [list]]
            [reagent-mui.material.list-item :refer [list-item]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.edit :refer [edit]]
            [wine-cellar.api :as api]
            [wine-cellar.views.components.form :refer
             [form-container form-actions form-row select-field number-field]]))

(defn grape-variety->option [gv] {"id" (:id gv) "label" (:name gv)})

(defn wine-variety-form
  [app-state wine-id]
  (let [editing-id (:editing-wine-variety-id @app-state)
        variety (if editing-id
                  (:editing-wine-variety @app-state)
                  (:new-wine-variety @app-state))
        ;; Get all grape varieties
        grape-varieties (:grape-varieties @app-state)
        ;; Get currently added varieties
        current-varieties (:wine-varieties @app-state)
        ;; Get IDs of varieties already added to this wine
        added-variety-ids (set (map :variety_id current-varieties))
        ;; Filter out varieties that are already added (unless we're
        ;; editing)
        available-varieties
        (if editing-id
          grape-varieties
          (filter #(not (contains? added-variety-ids (:id %))) grape-varieties))
        submitting? (:submitting-wine-variety? @app-state)
        submit-handler
        (fn []
          (if (nil? (:variety_id variety))
            (swap! app-state assoc :error "Grape variety is required")
            (do (swap! app-state assoc :submitting-wine-variety? true)
                (if editing-id
                  (api/update-wine-variety-percentage app-state
                                                      wine-id
                                                      editing-id
                                                      (:percentage variety))
                  (api/add-variety-to-wine app-state wine-id variety)))))]
    [form-container
     {:title (if editing-id "Edit Grape Variety" "Add Grape Variety")
      :on-submit submit-handler}
     [form-row
      (if editing-id
        [typography {:variant "h6"} (:variety_name variety)]
        [select-field
         {:label "Grape Variety"
          :required true
          :value (when-let [gv (first (filter #(= (:id %) (:variety_id variety))
                                              grape-varieties))]
                   (clj->js (grape-variety->option gv)))
          :options (clj->js (mapv grape-variety->option available-varieties))
          :on-change
          #(swap! app-state assoc-in [:new-wine-variety :variety_id] (.-id %))
          :helper-text
          (when (empty? available-varieties)
            "All available grape varieties have already been added to this wine")}])]
     [form-row
      [number-field
       {:label "Percentage"
        :required false
        :min 0
        :max 100
        :value (:percentage variety)
        :helper-text "Percentage of this grape in the wine blend (0-100)"
        :on-change #(swap! app-state assoc-in
                      [(if editing-id :editing-wine-variety :new-wine-variety)
                       :percentage]
                      (when-not (empty? %) (js/parseInt % 10)))}]]
     [form-actions
      {:submit-text (if editing-id "Update" "Add")
       :cancel-text "Cancel"
       :loading? submitting?
       :on-cancel #(swap! app-state assoc
                     :show-wine-variety-form? false
                     :editing-wine-variety-id nil
                     :new-wine-variety {})}]]))

(defn delete-variety-confirmation-dialog
  [app-state wine-id]
  (let [variety-id (:deleting-wine-variety-id @app-state)
        grape-variety (when variety-id
                        (first (filter #(= (:id %) variety-id)
                                       (:grape-varieties @app-state))))]
    [dialog
     {:open (boolean variety-id)
      :on-close #(swap! app-state assoc :deleting-wine-variety-id nil)}
     [dialog-title "Confirm Removal"]
     [dialog-content
      [typography
       (str "Are you sure you want to remove the grape variety '"
            (:variety_name grape-variety)
            "' from this wine?")]]
     [dialog-actions
      [button {:on-click #(swap! app-state assoc :deleting-wine-variety-id nil)}
       "Cancel"]
      [button
       {:color "error"
        :variant "contained"
        :on-click (fn []
                    (api/remove-variety-from-wine app-state wine-id variety-id)
                    (swap! app-state assoc :deleting-wine-variety-id nil))}
       "Remove"]]]))

(defn wine-varieties-list
  [app-state wine-id]
  (let [varieties (:wine-varieties @app-state)
        grape-varieties (:grape-varieties @app-state)
        ;; Get IDs of varieties already added to this wine
        added-variety-ids (set (map :variety_id varieties))
        ;; Check if there are any varieties available to add
        available-varieties? (some #(not (contains? added-variety-ids (:id %)))
                                   grape-varieties)]
    [box {:sx {:width "100%"}}
     ;; Remove the card wrapper and just keep the content
     (if (empty? varieties)
       [typography {:variant "body1"}
        "No grape varieties associated with this wine."]
       [list
        (for [variety varieties]
          ^{:key (:variety_id variety)}
          [list-item
           [grid {:container true :spacing 2 :alignItems "center"}
            [grid {:item true :xs 6}
             [typography {:variant "body1"} (:variety_name variety)]]
            [grid {:item true :xs 4}
             (if (:percentage variety)
               [typography {:variant "body2"} (str (:percentage variety) "%")]
               [typography {:variant "body2" :color "text.secondary"}
                "No percentage specified"])]
            [grid {:item true :xs 2 :textAlign "right"}
             [icon-button
              {:edge "end"
               :aria-label "edit"
               :on-click #(swap! app-state assoc
                            :editing-wine-variety-id (:variety_id variety)
                            :editing-wine-variety variety
                            :show-wine-variety-form? true)} [edit]]
             [icon-button
              {:edge "end"
               :aria-label "delete"
               :on-click #(swap! app-state assoc
                            :deleting-wine-variety-id
                            (:variety_id variety))} [delete]]]]])])
     ;; Keep the "Add Variety" button
     (when-not (:show-wine-variety-form? @app-state)
       [button
        {:variant "contained"
         :color "primary"
         :size "small"
         :start-icon (r/as-element [add])
         :sx {:mt 2}
         :disabled (or (empty? grape-varieties) (not available-varieties?))
         :on-click #(swap! app-state assoc :show-wine-variety-form? true)}
        "Add Variety"])
     (when (:show-wine-variety-form? @app-state)
       [wine-variety-form app-state wine-id])
     [delete-variety-confirmation-dialog app-state wine-id]]))

(defn wine-varieties-component
  [app-state wine-id]
  (r/create-class
   {:component-did-mount (fn [_]
                           (api/fetch-grape-varieties app-state)
                           (api/fetch-wine-varieties app-state wine-id))
    :reagent-render (fn [app-state wine-id] [wine-varieties-list app-state
                                             wine-id])}))
