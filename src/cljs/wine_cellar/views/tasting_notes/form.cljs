(ns wine-cellar.views.tasting-notes.form
  (:require [wine-cellar.views.components.form :refer
             [form-container form-actions text-area-field number-field
              year-field form-row form-divider date-field text-field
              checkbox-field]]
            [wine-cellar.api :as api]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [wine-cellar.utils.vintage :as vintage]))

;; TODO -- add type for external tasting notes
(defn tasting-note-form
  [app-state wine-id]
  (let [wine (first (filter #(= (:id %) wine-id) (:wines @app-state)))
        current-drink-from (:drink_from_year wine)
        current-drink-until (:drink_until_year wine)
        new-note (:new-tasting-note @app-state)]
    ;; Initialize or update the tasting window fields with current values
    ;; from the wine. We need to update in these cases:
    ;; 1. First time initialization (fields are nil)
    ;; 2. Switching to a different wine
    ;; 3. Wine values have changed (always update to reflect external
    ;; changes)
    (when (or (nil? (:drink_from_year new-note))
              (nil? (:drink_until_year new-note))
              (not= (:wine-id new-note) wine-id)
              (not= (:last-known-drink-from new-note) current-drink-from)
              (not= (:last-known-drink-until new-note) current-drink-until))
      (swap! app-state update
        :new-tasting-note
        #(-> %
             (assoc :drink_from_year current-drink-from)
             (assoc :drink_until_year current-drink-until)
             (assoc :wine-id wine-id)
             (assoc :last-known-drink-from current-drink-from)
             (assoc :last-known-drink-until current-drink-until))))
    ;; Get the latest version of new-note after potential updates
    (let [updated-note (:new-tasting-note @app-state)
          is-external (boolean (:is_external updated-note))]
      [form-container
       {:title "Add Tasting Note",
        :on-submit
          #(do
             ;; First update the tasting window if changed
             (when (or (not= (:drink_from_year updated-note) current-drink-from)
                       (not= (:drink_until_year updated-note)
                             current-drink-until))
               (api/update-wine app-state
                                wine-id
                                (select-keys updated-note
                                             [:drink_from_year
                                              :drink_until_year])))
             ;; Then create the tasting note
             (api/create-tasting-note
               app-state
               wine-id
               (-> updated-note
                   (update :rating (fn [r] (if (string? r) (js/parseInt r) r)))
                   (dissoc :drink_from_year
                           :drink_until_year
                           :wine-id
                           :form-edited
                           :last-known-drink-from
                           :last-known-drink-until))))}
       ;; External note toggle
       [form-row
        [checkbox-field
         {:label "External Tasting Note",
          :checked is-external,
          :on-change #(swap! app-state update-in
                        [:new-tasting-note]
                        (fn [note]
                          (let [new-is-external (not is-external)]
                            (-> note
                                (assoc :is_external new-is-external)
                                ;; Clear tasting date if switching to
                                ;; external and no date set
                                (cond-> (and new-is-external
                                             (nil? (:tasting_date note)))
                                          (assoc :tasting_date nil))))))}]]
       ;; Source field (only shown for external notes)
       (when is-external
         [form-row
          [text-field
           {:label "Source",
            :required true,
            :value (:source updated-note),
            :helper-text "e.g., Decanter, Wine Spectator, Vivino",
            :on-change
              #(swap! app-state assoc-in [:new-tasting-note :source] %)}]])
       ;; Date input (required only for personal notes)
       [form-row
        [date-field
         {:label "Tasting Date",
          :required (not is-external),
          :value (:tasting_date updated-note),
          :helper-text (when is-external "Optional for external notes"),
          :on-change
            #(swap! app-state assoc-in [:new-tasting-note :tasting_date] %)}]]
       ;; Tasting notes textarea
       [grid {:item true, :xs 12}
        [text-area-field
         {:label "Notes",
          :required true,
          :value (:notes updated-note),
          :on-change #(swap! app-state assoc-in [:new-tasting-note :notes] %)}]]
       ;; Rating input
       [form-row
        [number-field
         {:label "Rating (1-100)",
          :required true,
          :min 1,
          :max 100,
          :value (:rating updated-note),
          :on-change #(swap! app-state assoc-in
                        [:new-tasting-note :rating]
                        (js/parseInt %))}]]
       ;; Tasting Window Section
       [form-divider "Update Tasting Window"]
       [box {:sx {:mb 2}}
        [typography {:variant "body2", :color "text.secondary"}
         "You can update the wine's tasting window based on this tasting"]]
       [form-row
        [year-field
         {:label "Drink From Year",
          :free-solo true,
          :options (vintage/default-drink-from-years),
          :value (:drink_from_year updated-note),
          :error (boolean (or (vintage/valid-tasting-year? (:drink_from_year
                                                             updated-note))
                              (and (:drink_from_year updated-note)
                                   (:drink_until_year updated-note)
                                   (> (:drink_from_year updated-note)
                                      (:drink_until_year updated-note))))),
          :helper-text
            (or
              (vintage/valid-tasting-year? (:drink_from_year updated-note))
              (when (and (:drink_from_year updated-note)
                         (:drink_until_year updated-note)
                         (> (:drink_from_year updated-note)
                            (:drink_until_year updated-note)))
                "Drink from year must be less than or equal to drink until year")
              "Year when the wine will be ready to drink"),
          :on-change #(swap! app-state update
                        :new-tasting-note
                        (fn [note]
                          (-> note
                              (assoc :drink_from_year (when-not (empty? %)
                                                        (js/parseInt % 10)))
                              (assoc :form-edited true))))}]
        [year-field
         {:label "Drink Until Year",
          :free-solo true,
          :options (vintage/default-drink-until-years),
          :value (:drink_until_year updated-note),
          :error (boolean (or (vintage/valid-tasting-year? (:drink_until_year
                                                             updated-note))
                              (and (:drink_from_year updated-note)
                                   (:drink_until_year updated-note)
                                   (< (:drink_until_year updated-note)
                                      (:drink_from_year updated-note))))),
          :helper-text
            (or
              (vintage/valid-tasting-year? (:drink_until_year updated-note))
              (when (and (:drink_from_year updated-note)
                         (:drink_until_year updated-note)
                         (< (:drink_until_year updated-note)
                            (:drink_from_year updated-note)))
                "Drink until year must be greater than or equal to drink from year")
              "Year when the wine should be consumed by"),
          :on-change #(swap! app-state update
                        :new-tasting-note
                        (fn [note]
                          (-> note
                              (assoc :drink_until_year (when-not (empty? %)
                                                         (js/parseInt % 10)))
                              (assoc :form-edited true))))}]]
       ;; Submit button
       [form-actions {:submit-text "Add Note"}]])))

