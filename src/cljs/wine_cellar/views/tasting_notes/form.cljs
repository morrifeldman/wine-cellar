(ns wine-cellar.views.tasting-notes.form
  (:require [wine-cellar.views.components.form :refer [form-container
                                                       form-actions
                                                       text-area-field
                                                       number-field
                                                       form-row
                                                       date-field]]
            [wine-cellar.api :as api]
            [reagent-mui.material.grid :refer [grid]]))

(defn tasting-note-form [app-state wine-id]
  (let [new-note (:new-tasting-note @app-state)]
    [form-container
     {:title "Add Tasting Note"
      :on-submit #(api/create-tasting-note
                   app-state
                   wine-id
                   (update new-note :rating (fn [r]
                                              (if (string? r)
                                                (js/parseInt r)
                                                r))))}

     ;; Date input
     [form-row
      [date-field {:label "Tasting Date"
                   :required true
                   :value (:tasting_date new-note)
                   :on-change #(swap! app-state assoc-in
                                      [:new-tasting-note :tasting_date] %)}]]

     ;; Tasting notes textarea
     [grid {:item true :xs 12}
      [text-area-field
       {:label "Notes"
        :required true
        :value (:notes new-note)
        :on-change #(swap! app-state assoc-in [:new-tasting-note :notes] %)}]]

     ;; Rating input
     [form-row
      [number-field
       {:label "Rating (1-100)"
        :required true
        :min 1
        :max 100
        :value (:rating new-note)
        :on-change #(swap! app-state assoc-in
                           [:new-tasting-note :rating]
                           (js/parseInt %))}]]

     ;; Submit button
     [form-actions
      {:on-submit #(api/create-tasting-note
                    app-state
                    wine-id
                    (update new-note :rating (fn [r] (if (string? r) (js/parseInt r) r))))
       :submit-text "Add Note"}]]))
