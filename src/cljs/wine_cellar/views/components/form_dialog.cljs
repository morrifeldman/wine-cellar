(ns wine-cellar.views.components.form-dialog
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]))

(defn form-dialog
  "The edit dialog every small form shares: a title, the fields stacked with
  even spacing, then an optional Delete pushed left, Cancel and the save
  action. on-save decides for itself whether to close (it may refuse bad
  input)."
  [{:keys [open? title on-close on-save save-label save-disabled? on-delete
           delete-label max-width]
    :or {save-label "Save" delete-label "Delete" max-width "xs"}} & children]
  [dialog
   {:open (boolean open?) :onClose on-close :maxWidth max-width :fullWidth true}
   [dialog-title title]
   [dialog-content
    (into [box {:sx {:pt 1 :display "flex" :flexDirection "column" :gap 2}}]
          children)]
   [dialog-actions
    (when on-delete
      [button {:color "error" :sx {:mr "auto"} :onClick on-delete}
       delete-label]) [button {:onClick on-close} "Cancel"]
    [button
     {:variant "contained" :disabled (boolean save-disabled?) :onClick on-save}
     save-label]]])
