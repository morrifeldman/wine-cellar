(ns wine-cellar.views.tasting-notes.form
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.typography :refer [typography]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.formatting :refer [format-date-iso]]
            [wine-cellar.utils.vintage :as vintage]
            [wine-cellar.views.components.form :refer
             [checkbox-field date-field form-actions form-container form-divider
              form-row number-field select-field text-area-field text-field
              year-field]]))

;; TODO -- add type for external tasting notes
(defn tasting-note-form
  [app-state wine-id]
  (let [wine (first (filter #(= (:id %) wine-id) (:wines @app-state)))
        current-drink-from (:drink_from_year wine)
        current-drink-until (:drink_until_year wine)
        editing-note-id (:editing-note-id @app-state)
        editing? (boolean editing-note-id)
        editing-note (when editing?
                       (first (filter #(= (:id %) editing-note-id)
                                      (:tasting-notes @app-state))))
        new-note (:new-tasting-note @app-state)
        submitting? (:submitting-note? @app-state)]
    ;; Initialize form for editing if we have an editing-note-id
    (when (and editing? (not= (:note-id new-note) editing-note-id))
      (tap> ["editing note" editing-note])
      (swap! app-state assoc
        :new-tasting-note
        (-> editing-note
            (assoc :note-id editing-note-id)
            (assoc :wine-id wine-id)
            (assoc :drink_from_year current-drink-from)
            (assoc :drink_until_year current-drink-until)
            (assoc :last-known-drink-from current-drink-from)
            (assoc :last-known-drink-until current-drink-until)
            (assoc :form-edited false))))
    ;; Initialize or update the tasting window fields with current values
    ;; from the wine. We need to update in these cases:
    ;; 1. First time initialization (no wine-id set yet)
    ;; 2. Switching to a different wine
    ;; 3. Wine values have changed (only update to reflect external
    ;;    changes if the user hasn't edited the fields)
    (when (and (not editing?)
               (or
                ;; Case 1 & 2: First initialization or switching wines
                (not= (:wine-id new-note) wine-id)
                ;; Case 3: External changes to the wine's tasting window
                ;; Only update if the user hasn't edited the form
                (and (not (:form-edited new-note))
                     (or (not= (:last-known-drink-from new-note)
                               current-drink-from)
                         (not= (:last-known-drink-until new-note)
                               current-drink-until)))))
      (swap! app-state update
        :new-tasting-note
        #(-> %
             (assoc :drink_from_year current-drink-from)
             (assoc :drink_until_year current-drink-until)
             (assoc :wine-id wine-id)
             (assoc :last-known-drink-from current-drink-from)
             (assoc :last-known-drink-until current-drink-until)
             (assoc :form-edited false))))
    ;; Get the latest version of new-note after potential updates
    (let [{drink-from-year :drink_from_year
           drink-until-year :drink_until_year
           :as updated-note}
          (:new-tasting-note @app-state)
          is-external (boolean (:is_external updated-note))
          valid-tasting-window?
          (vintage/valid-tasting-window? drink-from-year drink-until-year)]
      [form-container
       {:title (if editing? "Edit Tasting Note" "Add Tasting Note")
        :on-submit
        #(do (swap! app-state assoc :submitting-note? true)
             ;; First update the tasting window if changed
             (when (or (not= drink-from-year current-drink-from)
                       (not= drink-until-year current-drink-until))
               (api/update-wine app-state
                                wine-id
                                (select-keys updated-note
                                             [:drink_from_year
                                              :drink_until_year])))
             ;; Then create or update the tasting note
             (let [note-data (-> updated-note
                                 (update :rating
                                         (fn [r]
                                           (if (string? r) (js/parseInt r) r)))
                                 (dissoc :drink_from_year
                                         :drink_until_year
                                         :wine-id
                                         :note-id
                                         :form-edited
                                         :last-known-drink-from
                                         :last-known-drink-until))]
               (if editing?
                 (api/update-tasting-note app-state
                                          wine-id
                                          editing-note-id
                                          note-data)
                 (api/create-tasting-note app-state wine-id note-data))))}
       ;; External note toggle
       [form-row
        [checkbox-field
         {:label "External Tasting Note"
          :checked is-external
          :on-change
          #(swap! app-state update-in [:new-tasting-note :is_external] not)}]]
       ;; Source field (only shown for external notes)
       (when is-external
         [form-row
          [select-field
           {:label "Source"
            :required true
            :value (:source updated-note)
            :options (or (:tasting-note-sources @app-state) [])
            :free-solo true
            :helper-text "Choose from existing sources or type a new one"
            :on-change
            #(swap! app-state assoc-in [:new-tasting-note :source] %)}]])
       ;; Date input (required only for personal notes)
       [form-row
        [date-field
         {:label "Tasting Date"
          :required (not is-external)
          :value (format-date-iso (:tasting_date updated-note))
          :helper-text (when is-external "Optional for external notes")
          :on-change
          #(swap! app-state assoc-in [:new-tasting-note :tasting_date] %)}]]
       ;; Tasting notes textarea
       [grid {:item true :xs 12}
        [text-area-field
         {:label "Notes"
          :required true
          :value (:notes updated-note)
          :on-change #(swap! app-state assoc-in [:new-tasting-note :notes] %)}]]
       ;; Rating input
       [form-row
        [number-field
         {:label "Rating (1-100)"
          :required true
          :min 1
          :max 100
          :value (:rating updated-note)
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
          :value drink-from-year
          :error (boolean valid-tasting-window?)
          :helper-text valid-tasting-window?
          :on-change #(swap! app-state update
                        :new-tasting-note
                        (fn [note]
                          (-> note
                              (assoc :drink_from_year
                                     (when-not (empty? %) (js/parseInt % 10)))
                              (assoc :form-edited true))))}]
        [year-field
         {:label "Drink Until Year"
          :free-solo true
          :options (vintage/default-drink-until-years)
          :value (:drink_until_year updated-note)
          :error (boolean valid-tasting-window?)
          :helper-text valid-tasting-window?
          :on-change #(swap! app-state update
                        :new-tasting-note
                        (fn [note]
                          (-> note
                              (assoc :drink_until_year
                                     (when-not (empty? %) (js/parseInt % 10)))
                              (assoc :form-edited true))))}]]
       ;; Submit and Cancel buttons
       [form-actions
        {:submit-text (if editing? "Update Note" "Add Note")
         :loading? submitting?
         :cancel-text (when editing? "Cancel")
         :on-cancel (when editing?
                      #(swap! app-state assoc
                         :editing-note-id nil
                         :new-tasting-note {}))}]])))

