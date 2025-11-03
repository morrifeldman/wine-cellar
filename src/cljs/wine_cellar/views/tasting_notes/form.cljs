(ns wine-cellar.views.tasting-notes.form
  (:require [reagent.core :as r]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.text-field :as mui-text-field]
            [wine-cellar.api :as api]
            [wine-cellar.utils.formatting :refer [format-date-iso]]
            [wine-cellar.common :as common]
            [wine-cellar.views.components.form :refer
             [checkbox-field date-field form-actions form-container form-row
              number-field select-field]]
            [reagent-mui.material.divider :refer [divider]]
            [wine-cellar.views.components.wset-appearance :refer
             [wset-appearance-section]]
            [wine-cellar.views.components.wset-nose :refer [wset-nose-section]]
            [wine-cellar.views.components.wset-palate :refer
             [wset-palate-section]]
            [wine-cellar.views.components.wset-conclusions :refer
             [wset-conclusions-section]]))

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
    (r/atom nil) other-observations-ref (r/atom nil) nose-observations-ref
    (r/atom nil) palate-observations-ref (r/atom nil) final-comments-ref
    (r/atom nil) wset-expanded? (r/atom false)]
   (let [form-state (get-form-state app-state)]
     (handle-form-initialization! app-state
                                  wine-id
                                  last-wine-id
                                  last-editing-note-id
                                  form-state)
     ;; Get the latest version of new-note after potential updates
     (let [updated-note (:new-tasting-note @app-state)
           current-wine (first (filter #(= (:id %) wine-id)
                                       (:wines @app-state)))
           wine-style (get current-wine :style "Red")
           style-info (common/style->info wine-style)
           wset-style (:wset-style style-info)
           is-external (boolean (:is_external updated-note))
           {:keys [editing? editing-note-id editing-note submitting?]}
           form-state]
       [form-container
        {:title (if editing? "Edit Tasting Note" "Add Tasting Note")
         :on-submit
         #(do (swap! app-state assoc :submitting-note? true)
              ;; Get notes text from DOM
              (let [notes-text (when @notes-ref (.-value @notes-ref))
                    ;; Get WSET other observations from refs if WSET mode
                    ;; enabled
                    other-obs-text (when (and @other-observations-ref
                                              (get-in updated-note
                                                      [:wset_data :note_type]))
                                     (.-value @other-observations-ref))
                    nose-obs-text (when (and @nose-observations-ref
                                             (get-in updated-note
                                                     [:wset_data :note_type]))
                                    (.-value @nose-observations-ref))
                    palate-obs-text (when (and @palate-observations-ref
                                               (get-in updated-note
                                                       [:wset_data :note_type]))
                                      (.-value @palate-observations-ref))
                    final-comments-text
                    (when (and @final-comments-ref
                               (get-in updated-note [:wset_data :note_type]))
                      (.-value @final-comments-ref))
                    ;; Update WSET data with other observations from refs
                    updated-note-with-obs
                    (cond-> updated-note
                      other-obs-text (assoc-in [:wset_data :appearance
                                                :other_observations]
                                      other-obs-text)
                      nose-obs-text (assoc-in [:wset_data :nose
                                               :other_observations]
                                     nose-obs-text)
                      palate-obs-text (assoc-in [:wset_data :palate
                                                 :other_observations]
                                       palate-obs-text)
                      final-comments-text (assoc-in [:wset_data :conclusions
                                                     :final_comments]
                                           final-comments-text))
                    note-data (-> updated-note-with-obs
                                  (assoc :notes notes-text)
                                  (update :rating
                                          (fn [r]
                                            (if (string? r) (js/parseInt r) r)))
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
        ;; WSET Structured Notes checkbox
        [form-row
         [checkbox-field
          {:label "WSET Structured Tasting"
           :checked (boolean (get-in updated-note [:wset_data :note_type]))
           :on-change
           #(if (get-in updated-note [:wset_data :note_type])
              ;; Remove WSET data completely
              (do
                (swap! app-state assoc-in [:new-tasting-note :wset_data] nil)
                (reset! wset-expanded? false))
              ;; Initialize WSET data and expand
              (let [default-appearance {:colour (or (:default-color style-info) :garnet)
                                        :intensity (or (:default-intensity style-info) :medium)}]
                (swap! app-state assoc-in
                       [:new-tasting-note :wset_data]
                        {:note_type "wset_level_3"
                         :version "1.0"
                         :wset_wine_style wset-style
                         :appearance default-appearance
                         :nose {}
                         :palate {}
                         :conclusions {}})
                (reset! wset-expanded? true)))}]]
        ;; WSET Content (only shown when enabled)
        (when (get-in updated-note [:wset_data :note_type])
          [form-row
           [grid {:container true :spacing 2}
            ;; Appearance Section
            [grid {:item true :xs 12}
            [wset-appearance-section
             {:appearance (get-in updated-note [:wset_data :appearance] {})
              :style-info style-info
              :other-observations-ref other-observations-ref
              :on-change #(swap! app-state assoc-in
                            [:new-tasting-note :wset_data :appearance]
                            %)}]]
            ;; Nose Section
           [grid {:item true :xs 12}
            [wset-nose-section
             {:nose (get-in updated-note [:wset_data :nose] {})
               :other-observations-ref nose-observations-ref
               :on-change #(swap! app-state assoc-in
                             [:new-tasting-note :wset_data :nose]
                             %)}]]
            ;; Palate Section
            [grid {:item true :xs 12}
            [wset-palate-section
             {:palate (get-in updated-note [:wset_data :palate] {})
              :style-info style-info
              :other-observations-ref palate-observations-ref
              :nose (get-in updated-note [:wset_data :nose] {})
              :on-change #(swap! app-state assoc-in
                            [:new-tasting-note :wset_data :palate]
                            %)}]]
            ;; Conclusions Section
            [grid {:item true :xs 12}
             [wset-conclusions-section
              {:conclusions (get-in updated-note [:wset_data :conclusions] {})
               :final-comments-ref final-comments-ref
               :on-change #(swap! app-state assoc-in
                             [:new-tasting-note :wset_data :conclusions]
                             %)}]]]])
        ;; Divider between WSET and traditional notes (only when expanded)
        (when (and (get-in updated-note [:wset_data :note_type])
                   @wset-expanded?)
          [form-row [divider {:sx {:my 2 :borderColor "rating.medium"}}]])
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
         [mui-text-field/text-field
          {:multiline true
           :rows 12
           :fullWidth true
           :variant "outlined"
           :size "small"
           :margin "dense"
           :required true
           :defaultValue (if editing? (:notes editing-note) "")
           :key (str "notes-" (or editing-note-id "new"))
           :inputRef #(reset! notes-ref %)
           :sx {"& .MuiOutlinedInput-root" {:backgroundColor "container.main"
                                            :border "none"
                                            :borderRadius 2}}
           :placeholder "Enter your tasting notes here..."}]]
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
