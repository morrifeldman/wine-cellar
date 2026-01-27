(ns wine-cellar.views.wines.varieties
  (:require [clojure.string :as str]
            [reagent.core :as r]
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

(defn get-variety-data
  [app-state]
  (let [editing-id (:editing-wine-variety-id @app-state)]
    {:editing-id editing-id
     :variety (if editing-id
                (:editing-wine-variety @app-state)
                (:new-wine-variety @app-state))
     :grape-varieties (:grape-varieties @app-state)
     :current-varieties (:wine-varieties @app-state)
     :submitting? (:submitting-wine-variety? @app-state)}))

(defn get-available-varieties
  [{:keys [editing-id grape-varieties current-varieties]}]
  (let [added-variety-ids (set (map :variety_id current-varieties))]
    (if editing-id
      grape-varieties
      (filter #(not (contains? added-variety-ids (:id %))) grape-varieties))))

(defn create-submit-handler
  [app-state wine-id
   {:keys [editing-id]
    {:keys [variety_id variety_name percentage] :as variety} :variety}]
  (fn []
    (cond
      ;; Case 1: Editing an existing variety (only percentage can be
      ;; changed)
      editing-id (do (swap! app-state assoc :submitting-wine-variety? true)
                     (api/update-wine-variety-percentage app-state
                                                         wine-id
                                                         editing-id
                                                         percentage))
      ;; Case 2: Selected an existing variety from dropdown
      variety_id (do (swap! app-state assoc :submitting-wine-variety? true)
                     (api/add-variety-to-wine app-state wine-id variety))
      ;; Case 3: Entered a new variety name (free-solo)
      variety_name (do (swap! app-state assoc :submitting-wine-variety? true)
                       (-> (api/create-grape-variety app-state variety)
                           (.then (fn [{:keys [id]}]
                                    (api/add-variety-to-wine
                                     app-state
                                     wine-id
                                     (assoc variety :variety_id id))))))
      ;; Case 4: No variety selected or entered
      :else (swap! app-state assoc :error "Grape variety is required"))))

(defn variety-selector
  [app-state variety-data]
  (let [{:keys [editing-id variety grape-varieties]} variety-data
        available-varieties (get-available-varieties variety-data)]
    (if editing-id
      [typography {:variant "h6"} (:variety_name variety)]
      (letfn [(update-new-variety! [f]
                (swap! app-state update :new-wine-variety (fnil f {})))
              (set-free-solo-name! [text]
                (update-new-variety!
                 (fn [variety]
                   (let [trimmed (str/trim (or text ""))]
                     (-> variety
                         (dissoc :variety_id)
                         ((if (str/blank? trimmed)
                            #(dissoc % :variety_name)
                            #(assoc % :variety_name trimmed))))))))]
        (let [selected-option
              (when-let [gv (first (filter #(= (:id %) (:variety_id variety))
                                           grape-varieties))]
                (clj->js (grape-variety->option gv)))]
          [select-field
           {:label "Grape Variety"
            :required true
            :free-solo true
            :value (or (get-in @app-state [:new-wine-variety :variety_name])
                       selected-option)
            :input-value (get-in @app-state [:new-wine-variety :variety_name])
            :options (clj->js (mapv grape-variety->option available-varieties))
            :on-input-change (fn [value reason]
                               (case reason
                                 "input" (set-free-solo-name! value)
                                 "clear" (update-new-variety!
                                          #(-> %
                                               (dissoc :variety_name)
                                               (dissoc :variety_id)))
                                 nil))
            :on-change (fn [value]
                         (if (string? value)
                           (set-free-solo-name! value)
                           (update-new-variety!
                            (fn [variety]
                              (-> variety
                                  (assoc :variety_id (when value (.-id value)))
                                  (dissoc :variety_name))))))
            :helper-text "Select an existing variety or type a new one"}])))))

(defn percentage-field
  [app-state {:keys [editing-id variety]}]
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
                  (when-not (empty? %) (js/parseInt % 10)))}])

(defn form-cancel-handler
  [app-state]
  #(swap! app-state assoc
     :show-wine-variety-form? false
     :editing-wine-variety-id nil
     :new-wine-variety {}))

(defn wine-variety-form
  [app-state wine-id]
  (let [variety-data (get-variety-data app-state)
        {:keys [editing-id submitting?]} variety-data
        submit-handler (create-submit-handler app-state wine-id variety-data)]
    [form-container
     {:title (if editing-id "Edit Grape Variety" "Add Grape Variety")
      :on-submit submit-handler}
     [form-row [variety-selector app-state variety-data]]
     [form-row [percentage-field app-state variety-data]]
     [form-actions
      {:submit-text (if editing-id "Update" "Add")
       :cancel-text "Cancel"
       :loading? submitting?
       :on-cancel (form-cancel-handler app-state)}]]))

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
        variety-total
        (when-let [percentages (seq (remove nil? (map :percentage varieties)))]
          (reduce + percentages))]
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
               :sx {:color "primary.main"}
               :on-click #(swap! app-state assoc
                            :editing-wine-variety-id (:variety_id variety)
                            :editing-wine-variety variety
                            :show-wine-variety-form? true)} [edit]]
             [icon-button
              {:edge "end"
               :aria-label "delete"
               :sx {:color "#ff8a80"}
               :on-click #(swap! app-state assoc
                            :deleting-wine-variety-id
                            (:variety_id variety))} [delete]]]]])])
     (when (and variety-total (not= variety-total 100))
       [typography {:variant "body1"} (str "Total: " variety-total "%")])
     (when-not (:show-wine-variety-form? @app-state)
       [button
        {:variant "contained"
         :color "primary"
         :size "small"
         :start-icon (r/as-element [add])
         :sx {:mt 2}
         :on-click #(swap! app-state assoc :show-wine-variety-form? true)}
        "Add Variety"])
     (when (:show-wine-variety-form? @app-state)
       [wine-variety-form app-state wine-id])
     [delete-variety-confirmation-dialog app-state wine-id]]))
