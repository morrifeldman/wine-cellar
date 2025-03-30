(ns wine-cellar.views.tasting-notes.form
  (:require [wine-cellar.views.components.form :refer [form-container
                                                       form-actions
                                                       text-area-field
                                                       number-field
                                                       year-field
                                                       form-row
                                                       form-divider
                                                       date-field]]
            [wine-cellar.api :as api]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [wine-cellar.utils.vintage :as vintage]))

;; TODO Fix handling of tasting window in this form
;; Probably just delete the tasting window from here now that we have it above

;; TODO -- add type for external tasting notes
(defn tasting-note-form [app-state wine-id]
  (let [new-note (:new-tasting-note @app-state)
        wine (first (filter #(= (:id %) wine-id) (:wines @app-state)))
        current-drink-from (:drink_from_year wine)
        current-drink-until (:drink_until_year wine)]

    ;; Initialize the tasting window fields with current values
    (when (and (not (:drink_from_year new-note))
               (not (:drink_until_year new-note)))
      (swap! app-state update :new-tasting-note assoc
             :drink_from_year current-drink-from
             :drink_until_year current-drink-until))

    [form-container
     {:title "Add Tasting Note"
      :on-submit #(do
                    ;; First update the tasting window if changed
                    (when (or (not= (:drink_from_year new-note) current-drink-from)
                              (not= (:drink_until_year new-note) current-drink-until))
                      (api/update-wine
                       app-state
                       wine-id
                       (select-keys
                        new-note
                        [:drink_from_year :drink_until_year])))

                    ;; Then create the tasting note
                    (api/create-tasting-note
                     app-state
                     wine-id
                     (-> new-note
                         (update :rating (fn [r]
                                           (if (string? r)
                                             (js/parseInt r)
                                             r)))
                         (dissoc :drink_from_year :drink_until_year))))}

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

     ;; Tasting Window Section
     [form-divider "Update Tasting Window"]

     [box {:sx {:mb 2}}
      [typography {:variant "body2" :color "text.secondary"}
       "You can update the wine's tasting window based on this tasting"]]

     [form-row
      [year-field
       {:label "Drink From Year"
        :free-solo true
        :options (vintage/default-drink-from-years)
        :value (:drink_from_year new-note)
        :helper-text "Year when the wine will be ready to drink"
        :on-change #(swap! app-state assoc-in
                           [:new-tasting-note :drink_from_year]
                           (when-not (empty? %) (js/parseInt % 10)))}]

      [year-field
       {:label "Drink Until Year"
        :free-solo true
        :options (vintage/default-drink-until-years)
        :value (:drink_until_year new-note)
        :helper-text "Year when the wine should be consumed by"
        :on-change #(swap! app-state assoc-in
                           [:new-tasting-note :drink_until_year]
                           (when-not (empty? %) (js/parseInt % 10)))}]]

     ;; Submit button
     [form-actions
      {:submit-text "Add Note"}]]))
