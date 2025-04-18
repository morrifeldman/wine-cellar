(ns wine-cellar.views.admin.schema
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-content-text :refer
             [dialog-content-text]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.alert :refer [alert]]
            [reagent-mui.material.alert-title :refer [alert-title]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [wine-cellar.api :as api]))

(defn schema-admin-page
  []
  (let [state (r/atom {:confirm-open false :loading false :result nil})]
    (fn []
      [box {:sx {:mt 4}}
       [paper {:sx {:p 3}}
        [typography {:variant "h4" :component "h2" :gutterBottom true}
         "Database Schema Administration"]
        [typography {:variant "body1" :paragraph true}
         "This page allows you to reset the database schema to the latest version while preserving all data."]
        [typography {:variant "body1" :paragraph true}
         "The schema reset process:"]
        [:ol
         [:li
          [typography {:variant "body1"}
           "Exports all data from the current database"]]
         [:li [typography {:variant "body1"} "Drops all existing tables"]]
         [:li
          [typography {:variant "body1"}
           "Creates tables with the latest schema definition"]]
         [:li
          [typography {:variant "body1"}
           "Imports the data back into the new schema"]]]
        [box {:sx {:mt 3 :mb 3}}
         [alert {:severity "warning"} [alert-title "Warning"]
          "This operation may cause temporary downtime for the application. "
          "It should only be performed during maintenance windows or when user impact is minimal."]]
        [box {:sx {:mt 3 :display "flex" :justifyContent "center"}}
         [button
          {:variant "contained"
           :color "primary"
           :disabled (:loading @state)
           :on-click #(swap! state assoc :confirm-open true)}
          (if (:loading @state)
            [box {:sx {:display "flex" :alignItems "center"}}
             [circular-progress {:size 24 :sx {:mr 1}}] "Processing..."]
            "Reset Database Schema")]]
        (when (:result @state)
          [box {:sx {:mt 3}}
           [alert {:severity (if (:success (:result @state)) "success" "error")}
            (if (:success (:result @state))
              "Schema reset completed successfully!"
              (str "Error: " (:error (:result @state))))]])]
       ;; Confirmation Dialog
       [dialog
        {:open (:confirm-open @state)
         :on-close #(swap! state assoc :confirm-open false)}
        [dialog-title "Confirm Schema Reset"]
        [dialog-content
         [dialog-content-text
          "Are you sure you want to reset the database schema? "
          "This operation will temporarily make the application unavailable "
          "while the schema is being updated."]]
        [dialog-actions
         [button
          {:on-click #(swap! state assoc :confirm-open false) :color "primary"}
          "Cancel"]
         [button
          {:on-click
           (fn []
             (swap! state assoc :confirm-open false :loading true :result nil)
             (-> (api/reset-schema)
                 (.then
                  (fn [result]
                    (swap! state assoc :loading false :result {:success true})))
                 (.catch (fn [error]
                           (swap! state assoc
                             :loading false
                             :result {:success false
                                      :error (.-message error)})))))
           :color "secondary"
           :variant "contained"
           :auto-focus true} "Reset Schema"]]]])))
