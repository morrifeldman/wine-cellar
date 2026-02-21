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
  [app-state editing-note wine-id editing-note-id]
  (swap! app-state assoc
    :new-tasting-note
    (-> editing-note
        (assoc :note-id editing-note-id)
        (assoc :wine-id wine-id)
        (dissoc :notes))))

(defn- initialize-new-note!
  [app-state wine-id]
  (swap! app-state update
    :new-tasting-note
    #(-> %
         (assoc :wine-id wine-id)
         (dissoc :notes))))

(defn- get-form-state
  [app-state]
  (let [editing-note-id (:editing-note-id @app-state)
        editing? (boolean editing-note-id)
        editing-note (when editing?
                       (first (filter #(= (:id %) editing-note-id)
                                      (:tasting-notes @app-state))))]
    {:editing-note-id editing-note-id
     :editing? editing?
     :editing-note editing-note
     :submitting? (:submitting-note? @app-state)}))

(defn- handle-form-initialization!
  [app-state wine-id last-wine-id last-note-id
   {:keys [editing? editing-note editing-note-id]}]
  (when (and editing? (not= @last-note-id editing-note-id))
    (reset! last-note-id editing-note-id)
    (initialize-editing-note! app-state editing-note wine-id editing-note-id))
  (when (and (not editing?) (not= @last-wine-id wine-id))
    (reset! last-wine-id wine-id)
    (initialize-new-note! app-state wine-id)))

(defn- collect-note-data
  "Build note-data map from form state and DOM refs."
  [updated-note notes-ref other-obs-ref nose-obs-ref palate-obs-ref final-ref]
  (let [wset? (get-in updated-note [:wset_data :note_type])
        read-ref #(when (and wset? @%) (.-value @%))
        other-text (read-ref other-obs-ref)
        nose-text (read-ref nose-obs-ref)
        palate-text (read-ref palate-obs-ref)
        final-text (read-ref final-ref)
        updated
        (cond-> updated-note
          other-text (assoc-in [:wset_data :appearance :other_observations]
                      other-text)
          nose-text (assoc-in [:wset_data :nose :other_observations] nose-text)
          palate-text (assoc-in [:wset_data :palate :other_observations]
                       palate-text)
          final-text (assoc-in [:wset_data :conclusions :final_comments]
                      final-text))]
    (-> updated
        (assoc :notes (when @notes-ref (.-value @notes-ref)))
        (update :rating (fn [r] (if (string? r) (js/parseInt r) r)))
        (dissoc :wine-id :note-id))))

(defn- wset-sections
  [app-state updated-note style-info other-obs-ref nose-obs-ref palate-obs-ref
   final-ref]
  [form-row
   [grid {:container true :spacing 2}
    [grid {:item true :xs 12}
     [wset-appearance-section
      {:appearance (get-in updated-note [:wset_data :appearance] {})
       :style-info style-info
       :other-observations-ref other-obs-ref
       :on-change #(swap! app-state assoc-in
                     [:new-tasting-note :wset_data :appearance]
                     %)}]]
    [grid {:item true :xs 12}
     [wset-nose-section
      {:nose (get-in updated-note [:wset_data :nose] {})
       :other-observations-ref nose-obs-ref
       :on-change
       #(swap! app-state assoc-in [:new-tasting-note :wset_data :nose] %)}]]
    [grid {:item true :xs 12}
     [wset-palate-section
      {:palate (get-in updated-note [:wset_data :palate] {})
       :style-info style-info
       :other-observations-ref palate-obs-ref
       :nose (get-in updated-note [:wset_data :nose] {})
       :on-change
       #(swap! app-state assoc-in [:new-tasting-note :wset_data :palate] %)}]]
    [grid {:item true :xs 12}
     [wset-conclusions-section
      {:conclusions (get-in updated-note [:wset_data :conclusions] {})
       :final-comments-ref final-ref
       :on-change #(swap! app-state assoc-in
                     [:new-tasting-note :wset_data :conclusions]
                     %)}]]]])

;; TODO -- add type for external tasting notes
(defn tasting-note-form
  [app-state wine-id & [on-close]]
  (r/with-let
   [last-wine-id (r/atom nil) last-note-id (r/atom nil) notes-ref (r/atom nil)
    other-obs-ref (r/atom nil) nose-obs-ref (r/atom nil) palate-obs-ref
    (r/atom nil) final-ref (r/atom nil) wset-expanded? (r/atom false)]
   (let [form-state (get-form-state app-state)]
     (handle-form-initialization! app-state
                                  wine-id
                                  last-wine-id
                                  last-note-id
                                  form-state)
     (let [{:keys [editing? editing-note-id editing-note submitting?]}
           form-state
           updated-note (:new-tasting-note @app-state)
           current-wine (first (filter #(= (:id %) wine-id)
                                       (:wines @app-state)))
           style-info (common/style->info (get current-wine :style "Red"))
           wset-style (:wset-style style-info)
           is-external (boolean (:is_external updated-note))
           wset-mode? (boolean (get-in updated-note [:wset_data :note_type]))
           show-notes? (or (not wset-mode?) (seq (:notes editing-note)))]
       [form-container
        {:title (if editing? "Edit Tasting Note" "Add Tasting Note")
         :on-submit
         #(let [note-data (collect-note-data updated-note
                                             notes-ref
                                             other-obs-ref
                                             nose-obs-ref
                                             palate-obs-ref
                                             final-ref)]
            (swap! app-state assoc :submitting-note? true)
            (if editing?
              (do (api/update-tasting-note app-state
                                           wine-id
                                           editing-note-id
                                           note-data)
                  (when on-close (on-close)))
              (do
                (api/create-tasting-note app-state wine-id note-data notes-ref)
                (when on-close (on-close)))))}
        [form-row
         [checkbox-field
          {:label "External Tasting Note"
           :checked is-external
           :on-change
           #(swap! app-state update-in [:new-tasting-note :is_external] not)}]]
        (when is-external
          [form-row
           [select-field
            {:label "Source"
             :required true
             :value (:source updated-note)
             :options (or (:tasting-note-sources @app-state) [])
             :free-solo true
             :helper-text "Choose from existing sources or type a new one"
             :on-input-change
             (fn [val _]
               (swap! app-state assoc-in [:new-tasting-note :source] val))
             :on-change
             #(swap! app-state assoc-in [:new-tasting-note :source] %)}]])
        [form-row
         [checkbox-field
          {:label "WSET Structured Tasting"
           :checked wset-mode?
           :on-change
           #(if wset-mode?
              (do (swap! app-state assoc-in [:new-tasting-note :wset_data] nil)
                  (reset! wset-expanded? false))
              (do (swap! app-state assoc-in
                    [:new-tasting-note :wset_data]
                    {:note_type "wset_level_3"
                     :version "1.0"
                     :wset_wine_style wset-style
                     :appearance
                     {:colour (or (:default-color style-info) :garnet)
                      :intensity (or (:default-intensity style-info) :medium)}
                     :nose {}
                     :palate {}
                     :conclusions {}})
                  (reset! wset-expanded? true)))}]]
        (when wset-mode?
          [wset-sections app-state updated-note style-info other-obs-ref
           nose-obs-ref palate-obs-ref final-ref])
        (when (and wset-mode? @wset-expanded?)
          [form-row [divider {:sx {:my 2 :borderColor "rating.medium"}}]])
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
        (when show-notes?
          [grid {:item true :xs 12}
           [mui-text-field/text-field
            {:multiline true
             :rows 12
             :fullWidth true
             :variant "outlined"
             :size "small"
             :margin "dense"
             :required (not wset-mode?)
             :defaultValue (if editing? (:notes editing-note) "")
             :key (str "notes-" (or editing-note-id "new"))
             :inputRef #(reset! notes-ref %)
             :sx {"& .MuiOutlinedInput-root" {:backgroundColor "container.main"
                                              :border "none"
                                              :borderRadius 2}}
             :placeholder (if wset-mode?
                            "Enter your tasting notes here... (Optional)"
                            "Enter your tasting notes here...")}]])
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
        [form-actions
         {:submit-text (if editing? "Update Note" "Add Note")
          :loading? submitting?
          :cancel-text "Cancel"
          :on-delete
          (when editing?
            (fn []
              (api/delete-tasting-note app-state wine-id editing-note-id)
              (swap! app-state assoc :editing-note-id nil :new-tasting-note {})
              (when on-close (on-close))))
          :on-cancel
          #(do (swap! app-state assoc :editing-note-id nil :new-tasting-note {})
               (when on-close (on-close)))}]]))))
