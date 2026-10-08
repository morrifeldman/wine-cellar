(ns wine-cellar.views.components.confirm
  "One confirmation dialog for the whole app, in place of js/confirm: it looks
   like the rest of the app and works inside the installed PWA."
  (:require [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.typography :refer [typography]]))

(defn confirm!
  "Asks the user, and runs `on-confirm` if they agree. `danger?` styles the
   confirm button for destructive actions."
  [app-state {:keys [title message confirm-label danger? on-confirm]}]
  (swap! app-state assoc
    :confirm
    {:title title
     :message message
     :confirm-label confirm-label
     :danger? danger?
     :on-confirm on-confirm}))

(defn confirm-dialog
  "Render once, near the root, so any view can call confirm!."
  [app-state]
  (let [{:keys [title message confirm-label danger? on-confirm] :as pending}
        (:confirm @app-state)
        close! #(swap! app-state dissoc :confirm)]
    [dialog
     {:open (boolean pending) :on-close close! :max-width "xs" :full-width true}
     (when title [dialog-title title])
     [dialog-content [typography {:sx {:whiteSpace "pre-line"}} message]]
     [dialog-actions [button {:on-click close!} "Cancel"]
      [button
       {:variant "contained"
        :color (if danger? "error" "primary")
        :on-click (fn [] (close!) (on-confirm))} (or confirm-label "OK")]]]))
