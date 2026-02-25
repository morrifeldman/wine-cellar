(ns wine-cellar.views.classifications.list
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.table :refer [table]]
            [reagent-mui.material.table-body :refer [table-body]]
            [reagent-mui.material.table-cell :refer [table-cell]]
            [reagent-mui.material.table-head :refer [table-head]]
            [reagent-mui.material.table-row :refer [table-row]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.icons.edit :refer [edit]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.arrow-back :refer [arrow-back]]
            [wine-cellar.api :as api]
            [wine-cellar.nav :as nav]
            [wine-cellar.views.components.classification-fields :refer
             [classification-fields]]
            [wine-cellar.views.classifications.form :refer
             [classification-form]]))

(defn edit-classification-form
  [app-state]
  (let [classification (:editing-classification @app-state)
        classifications (:classifications @app-state)]
    [box [dialog-title "Edit Classification"]
     [dialog-content
      [box {:sx {:mt 2}}
       [classification-fields app-state [:editing-classification]
        classifications]]]
     [dialog-actions
      [button {:on-click #(swap! app-state dissoc :editing-classification)}
       "Cancel"]
      [button
       {:on-click #(do (api/update-classification app-state
                                                  (:id classification)
                                                  (:editing-classification
                                                   @app-state))
                       (swap! app-state dissoc :editing-classification))
        :color "primary"} "Save"]]]))

(defn delete-confirmation-dialog
  [app-state]
  (let [classification (:deleting-classification @app-state)]
    [dialog
     {:open (boolean classification)
      :on-close #(swap! app-state dissoc :deleting-classification)}
     [dialog-title "Confirm Deletion"]
     [dialog-content
      [typography
       (str "Are you sure you want to delete the classification for "
            (:country classification)
            " - "
            (:region classification)
            (when-let [appellation (:appellation classification)]
              (str " - " appellation))
            "?")]]
     [dialog-actions
      [button {:on-click #(swap! app-state dissoc :deleting-classification)}
       "Cancel"]
      [button
       {:on-click #(api/delete-classification app-state
                                              (:id (:deleting-classification
                                                    @app-state)))
        :color "error"} "Delete"]]]))

(defn classification-actions
  [classification app-state]
  [box {:sx {:display "flex" :gap 1}}
   [button
    {:size "small"
     :start-icon (r/as-element [edit])
     :on-click #(swap! app-state assoc :editing-classification classification)}
    "Edit"]
   [button
    {:size "small"
     :color "error"
     :start-icon (r/as-element [delete])
     :on-click #(swap! app-state assoc :deleting-classification classification)}
    "Delete"]])

(defn classification-table-row
  [classification app-state]
  [table-row [table-cell (:country classification)]
   [table-cell (:region classification)]
   [table-cell (or (:appellation classification) "")]
   [table-cell (or (:appellation_tier classification) "")]
   [table-cell (or (:classification classification) "")]
   [table-cell [classification-actions classification app-state]]])

(defn classifications-table
  [classifications app-state]
  [paper
   [table
    [table-head
     [table-row [table-cell "Country"] [table-cell "Region"]
      [table-cell "Appellation"] [table-cell "Tier"]
      [table-cell "Classification"] [table-cell "Actions"]]]
    [table-body
     (if (empty? classifications)
       [table-row
        [table-cell {:col-span 6}
         [typography {:align "center" :sx {:py 3}}
          "No classifications found. Add one to get started."]]]
       (for [classification classifications]
         ^{:key (:id classification)}
         [classification-table-row classification app-state]))]]])

(defn classifications-page
  [app-state]
  (let [classifications (:classifications @app-state)]
    [box
     [typography {:variant "h4" :component "h1" :sx {:mb 2}}
      "Wine Classifications"]
     [box {:sx {:display "flex" :justifyContent "space-between" :mb 2}}
      [button
       {:variant "outlined"
        :color "primary"
        :start-icon (r/as-element [arrow-back])
        :on-click #(nav/go-wines!)} "Back to Wine List"]
      [button
       {:variant "contained"
        :color "primary"
        :start-icon (r/as-element [add])
        :on-click #(swap! app-state assoc :creating-classification? true)}
       "Add Classification"]]
     (when (:creating-classification? @app-state)
       [paper {:sx {:p 3 :mb 3}} [classification-form app-state]])
     (when (:editing-classification @app-state)
       [dialog
        {:open true
         :on-close #(swap! app-state dissoc :editing-classification)
         :max-width "md"
         :full-width true} [edit-classification-form app-state]])
     [delete-confirmation-dialog app-state]
     [classifications-table classifications app-state]]))
