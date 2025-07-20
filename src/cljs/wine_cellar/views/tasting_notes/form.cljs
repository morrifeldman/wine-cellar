(ns wine-cellar.views.tasting-notes.form
  (:require [reagent.core :as r]
            [reagent-mui.material.grid :refer [grid]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.formatting :refer [format-date-iso]]
            [wine-cellar.views.components.form :refer
             [checkbox-field date-field form-actions form-container form-row
              number-field select-field uncontrolled-text-area-field]]))

(defn- initialize-editing-note!
  "Initialize form state when starting to edit an existing note"
  [app-state editing-note wine-id editing-note-id]
  (swap! app-state assoc
    :new-tasting-note
    (-> editing-note
        (assoc :note-id editing-note-id)
        (assoc :wine-id wine-id)
        (dissoc :notes))))

(defn- initialize-new-note!
  "Initialize form state when switching to a new wine"
  [app-state wine-id]
  (swap! app-state update
    :new-tasting-note
    #(-> %
         (assoc :wine-id wine-id)
         (dissoc :notes))))

(defn- get-form-state
  "Extract form state from app-state for the given wine"
  [app-state]
  (let [editing-note-id (:editing-note-id @app-state)
        editing? (boolean editing-note-id)
        editing-note (when editing?
                       (first (filter #(= (:id %) editing-note-id)
                                      (:tasting-notes @app-state))))]
    {:editing-note-id editing-note-id
     :editing? editing?
     :editing-note editing-note
     :new-note (:new-tasting-note @app-state)
     :submitting? (:submitting-note? @app-state)}))

(defn- handle-form-initialization!
  "Handle form initialization logic with state tracking"
  [app-state wine-id last-wine-id last-editing-note-id
   {:keys [editing? editing-note editing-note-id]}]
  ;; Initialize form for editing if we have an editing-note-id (only once
  ;; per edit session)
  (when (and editing? (not= @last-editing-note-id editing-note-id))
    (reset! last-editing-note-id editing-note-id)
    (initialize-editing-note! app-state editing-note wine-id editing-note-id))
  ;; Initialize form when switching wines (only once per wine change)
  (when (and (not editing?) (not= @last-wine-id wine-id))
    (reset! last-wine-id wine-id)
    (initialize-new-note! app-state wine-id)))

;; TODO -- add type for external tasting notes
(defn tasting-note-form
  [app-state wine-id]
  (r/with-let
   [last-wine-id (r/atom nil) last-editing-note-id (r/atom nil) notes-ref
    (r/atom nil)]
   (let [form-state (get-form-state app-state)]
     (handle-form-initialization! app-state
                                  wine-id
                                  last-wine-id
                                  last-editing-note-id
                                  form-state)
     ;; Get the latest version of new-note after potential updates
     (let [updated-note (:new-tasting-note @app-state)
           is-external (boolean (:is_external updated-note))
           {:keys [editing? editing-note-id editing-note submitting?]}
           form-state]
       [form-container
        {:title (if editing? "Edit Tasting Note" "Add Tasting Note")
         :on-submit #(do (swap! app-state assoc :submitting-note? true)
                         ;; Get notes text from DOM
                         (let [notes-text (when @notes-ref (.-value @notes-ref))
                               note-data
                               (-> updated-note
                                   (assoc :notes notes-text)
                                   (update
                                    :rating
                                    (fn [r] (if (string? r) (js/parseInt r) r)))
                                   (dissoc :wine-id :note-id))]
                           (if editing?
                             (api/update-tasting-note app-state
                                                      wine-id
                                                      editing-note-id
                                                      note-data)
                             (api/create-tasting-note app-state
                                                      wine-id
                                                      note-data
                                                      notes-ref))))}
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
        ;; Date input (required only for personal notes, unless editing
        ;; existing dateless note)
        [form-row
         [date-field
          {:label "Tasting Date"
           :required (and (not is-external)
                          (not (and editing?
                                    (nil? (:tasting_date editing-note)))))
           :value (format-date-iso (:tasting_date updated-note))
           :helper-text (cond is-external "Optional for external notes"
                              (and editing? (nil? (:tasting_date editing-note)))
                              "Optional when editing existing dateless notes"
                              :else nil)
           :on-change
           #(swap! app-state assoc-in [:new-tasting-note :tasting_date] %)}]]
        ;; Tasting notes textarea (uncontrolled for performance)
        [grid {:item true :xs 12}
         [uncontrolled-text-area-field
          {:label "Notes"
           :required true
           :initial-value (if editing? (:notes editing-note) "")
           :reset-key (str "notes-" (or editing-note-id "new"))
           :input-ref notes-ref
           :rows 12}]]
        ;; Rating input
        [form-row
         [number-field
          {:label "Rating (1-100)"
           :required false
           :min 1
           :max 100
           :value (:rating updated-note)
           :on-change #(swap! app-state assoc-in
                         [:new-tasting-note :rating]
                         (js/parseInt %))}]]
        ;; Submit and Cancel buttons
        [form-actions
         {:submit-text (if editing? "Update Note" "Add Note")
          :loading? submitting?
          :cancel-text (when editing? "Cancel")
          :on-cancel (when editing?
                       #(swap! app-state assoc
                          :editing-note-id nil
                          :new-tasting-note {}))}]]))))

