(ns wine-cellar.views.grape-varieties.list
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.card :refer [card]]
            [reagent-mui.material.card-content :refer [card-content]]
            [reagent-mui.material.card-header :refer [card-header]]
            [reagent-mui.material.container :refer [container]]
            [reagent-mui.material.divider :refer [divider]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.list :refer [list]]
            [reagent-mui.material.list-item :refer [list-item]]
            [reagent-mui.material.list-item-button :refer [list-item-button]]
            [reagent-mui.material.list-item-text :refer [list-item-text]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.edit :refer [edit]]
            [wine-cellar.api :as api]
            [wine-cellar.views.components.confirm :refer [confirm!]]
            [wine-cellar.views.components.form :refer
             [text-field form-container form-actions]]))

;; Remember call apis with :variety_name but data comes back just with :name
(defn variety-for-api
  [variety]
  (-> variety
      (dissoc :name)
      (assoc :variety_name (:name variety))))

(defn grape-variety-form
  [app-state]
  (let [editing-id (:editing-variety-id @app-state)
        variety (if editing-id
                  (:editing-variety @app-state)
                  (:new-grape-variety @app-state))
        submitting? (:submitting-variety? @app-state)
        submit-handler
        (fn []
          (if (empty? (:name variety))
            (swap! app-state assoc :error "Variety name is required")
            (do (swap! app-state assoc :submitting-variety? true)
                (if editing-id
                  (api/update-grape-variety app-state
                                            editing-id
                                            (variety-for-api variety))
                  (api/create-grape-variety app-state
                                            (variety-for-api variety))))))]
    [form-container
     {:title (if editing-id "Edit Grape Variety" "Add New Grape Variety")
      :on-submit submit-handler}
     [text-field
      {:label "Variety Name"
       :required true
       :value (:name variety)
       :on-change #(swap! app-state assoc-in
                     [(if editing-id :editing-variety :new-grape-variety) :name]
                     %)}]
     [form-actions
      {:submit-text (if editing-id "Update" "Add")
       :cancel-text "Cancel"
       :loading? submitting?
       :on-cancel #(swap! app-state assoc
                     :show-variety-form? false
                     :editing-variety-id nil
                     :new-grape-variety {})}]]))

(defn grape-varieties-list
  [app-state]
  (let [varieties (:grape-varieties @app-state)]
    [box
     [card
      [card-header
       {:title "Grape Varieties"
        :action (r/as-element [button
                               {:variant "contained"
                                :color "primary"
                                :start-icon (r/as-element [add])
                                :on-click #(swap! app-state assoc
                                             :show-variety-form?
                                             true)} "Add Variety"])}] [divider]
      [card-content
       (when (:show-variety-form? @app-state) [grape-variety-form app-state])
       (if (empty? varieties)
         [typography {:variant "body1" :sx {:p 2}}
          "No grape varieties found. Add some to get started."]
         [list
          (for [variety varieties]
            ^{:key (:id variety)}
            [list-item
             {:disablePadding true
              :secondaryAction
              (r/as-element
               [box
                [icon-button
                 {:edge "end"
                  :aria-label "edit"
                  :on-click #(swap! app-state assoc
                               :editing-variety-id (:id variety)
                               :editing-variety variety
                               :show-variety-form? true)} [edit]]
                [icon-button
                 {:edge "end"
                  :aria-label "delete"
                  :on-click
                  #(confirm!
                    app-state
                    {:title (str "Delete " (:name variety) "?")
                     :message "This grape variety will be gone for good."
                     :confirm-label "Delete"
                     :danger? true
                     :on-confirm (fn []
                                   (api/delete-grape-variety app-state
                                                             (:id variety)))})}
                 [delete]]])}
             [list-item-button
              [list-item-text {:primary (:name variety)}]]])])]]]))

(defn grape-varieties-page
  [app-state]
  [container {:maxWidth "md" :sx {:mt 4 :mb 4}}
   [typography {:variant "h4" :component "h1" :gutterBottom true}
    "Grape Varieties Management"] [grape-varieties-list app-state]])
