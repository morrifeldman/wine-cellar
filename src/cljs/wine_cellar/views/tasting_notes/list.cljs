(ns wine-cellar.views.tasting-notes.list
  (:require [clojure.string :as str]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.formatting :refer [format-date]]
            [wine-cellar.views.tasting-notes.form :refer [tasting-note-form]]
            [wine-cellar.views.components.wine-color :refer
             [wine-color-display]]))

(defn get-rating-color
  [rating]
  (cond (>= rating 90) "rating.high"
        (>= rating 80) "rating.medium"
        :else "rating.low"))

(defn- normalize-characteristics
  "Flatten and clean a WSET characteristics section (primary/secondary/tertiary)."
  [section]
  (->> (or section {})
       vals
       flatten
       (map #(if (string? %)
               (let [trimmed (str/trim %)]
                 (when (not (str/blank? trimmed)) trimmed))
               %))
       (remove nil?)))

(defn- sensory-characteristics-group
  [items label]
  (when (seq items)
    [box {:sx {:mb 1}}
     [typography {:variant "body2" :sx {:fontWeight "bold" :mb 0.5}}
      (str label ":")]
     [grid {:container true :spacing 0.5}
      (for [value items]
        ^{:key (str label "-" value)}
        [grid {:item true}
         [chip {:label value :size "small" :variant "outlined"}]])]]))

(defn- sensory-characteristics-display
  [{:keys [data primary-label secondary-label tertiary-label]}]
  (let [primary-items (normalize-characteristics (:primary data))
        secondary-items (normalize-characteristics (:secondary data))
        tertiary-items (normalize-characteristics (:tertiary data))]
    (when (some seq [primary-items secondary-items tertiary-items])
      [box {:sx {:mt 1}}
       [sensory-characteristics-group primary-items primary-label]
       [sensory-characteristics-group secondary-items secondary-label]
       [sensory-characteristics-group tertiary-items tertiary-label]])))

(defn- wset-display
  "Simple display of WSET structured data"
  [wset-data]
  (when wset-data
    [box {:sx {:mt 2 :p 2 :backgroundColor "background.paper" :borderRadius 1}}
     [typography {:variant "h6" :sx {:mb 1 :color "primary.main"}}
      "WSET Structured Tasting"]
     ;; Appearance section
     (when-let [appearance (:appearance wset-data)]
       [box {:sx {:mb 2}}
        [typography {:variant "subtitle2" :sx {:fontWeight "bold" :mb 1}}
         "Appearance"]
        [grid {:container true :spacing 1}
         (when (:clarity appearance)
           [grid {:item true}
            [chip {:label (:clarity appearance) :size "small"}]])
         (when (and (:colour appearance) (:intensity appearance))
           [grid {:item true}
            [wine-color-display
             {:selected-color (:colour appearance)
              :selected-intensity (:intensity appearance)
              :size :small}]])]
        (when (:other_observations appearance)
          [typography {:variant "body2" :sx {:mt 1 :fontStyle "italic"}}
           (:other_observations appearance)])])
     ;; Nose section
     (when-let [nose (:nose wset-data)]
       [box {:sx {:mb 2}}
        [typography {:variant "subtitle2" :sx {:fontWeight "bold" :mb 1}}
         "Nose"]
        [grid {:container true :spacing 1}
         (when (:condition nose)
           [grid {:item true} [chip {:label (:condition nose) :size "small"}]])
         (when (:intensity nose)
           [grid {:item true}
            [chip
             {:label (str "Intensity: " (:intensity nose)) :size "small"}]])
         (when (:development nose)
           [grid {:item true}
            [chip {:label (:development nose) :size "small"}]])]
        (when-let [aroma-data (:aroma-characteristics nose)]
          [sensory-characteristics-display
           {:data aroma-data
            :primary-label "Primary Aromas"
            :secondary-label "Secondary Aromas"
            :tertiary-label "Tertiary Aromas"}])
        (when (:other_observations nose)
          [typography {:variant "body2" :sx {:mt 1 :fontStyle "italic"}}
           (:other_observations nose)])])
     ;; Palate section
     (when-let [palate (:palate wset-data)]
       [box {:sx {:mb 2}}
        [typography {:variant "subtitle2" :sx {:fontWeight "bold" :mb 1}}
         "Palate"]
        [grid {:container true :spacing 1}
         (when (:sweetness palate)
           [grid {:item true}
            [chip
             {:label (str "Sweetness: " (:sweetness palate)) :size "small"}]])
         (when (:acidity palate)
           [grid {:item true}
            [chip {:label (str "Acidity: " (:acidity palate)) :size "small"}]])
         (when (:tannin palate)
           [grid {:item true}
            [chip {:label (str "Tannin: " (:tannin palate)) :size "small"}]])
         (when (:alcohol palate)
           [grid {:item true}
            [chip {:label (str "Alcohol: " (:alcohol palate)) :size "small"}]])
         (when (:body palate)
           [grid {:item true}
            [chip {:label (str "Body: " (:body palate)) :size "small"}]])
         (when (:flavor-intensity palate)
           [grid {:item true}
            [chip
             {:label (str "Flavor Intensity: " (:flavor-intensity palate))
              :size "small"}]])
         (when (:finish palate)
           [grid {:item true}
            [chip {:label (str "Finish: " (:finish palate)) :size "small"}]])]
        (when-let [flavor-data (:flavor-characteristics palate)]
          [sensory-characteristics-display
           {:data flavor-data
            :primary-label "Primary Flavors"
            :secondary-label "Secondary Flavors"
            :tertiary-label "Tertiary Flavors"}])
        (when (:other_observations palate)
          [typography {:variant "body2" :sx {:mt 1 :fontStyle "italic"}}
           (:other_observations palate)])])
     ;; Conclusions section
     (when-let [conclusions (:conclusions wset-data)]
       [box {:sx {:mb 2}}
        [typography {:variant "subtitle2" :sx {:fontWeight "bold" :mb 1}}
         "Conclusions"]
        [grid {:container true :spacing 1}
         (when (:quality-level conclusions)
           [grid {:item true}
            [chip
             {:label (str "Quality: " (:quality-level conclusions))
              :size "small"}]])
         (when (:readiness conclusions)
           [grid {:item true}
            [chip
             {:label (str "Readiness: " (:readiness conclusions))
              :size "small"}]])]
        (when (:final_comments conclusions)
          [typography {:variant "body2" :sx {:mt 1 :fontStyle "italic"}}
           (:final_comments conclusions)])])]))

(defn tasting-note-item
  [app-state wine-id note]
  (let [is-external (boolean (:is_external note))]
    [paper
     {:elevation 1
      :sx {:p 2 :mb 2 :borderLeft (when is-external "4px solid #9e9e9e")}}
     [grid {:container true}
      ;; Header with date/source and rating
      [grid {:item true :xs 9}
       (if is-external
         ;; External note header
         [box
          {:sx {:display "flex" :alignItems "center" :flexWrap "wrap" :gap 1}}
          [chip
           {:label (:source note)
            :size "small"
            :color "default"
            :variant "outlined"
            :sx {:fontWeight "bold"}}]
          (when (:tasting_date note)
            [typography {:variant "body2" :color "text.secondary" :sx {:ml 1}}
             (format-date (:tasting_date note))])]
         ;; Personal note header
         [typography {:variant "subtitle1" :sx {:fontWeight "bold"}}
          (format-date (:tasting_date note))])]
      [grid {:item true :xs 3 :sx {:textAlign "right"}}
       [typography
        {:variant "subtitle1"
         :sx {:color (get-rating-color (:rating note)) :fontWeight "bold"}}
        (str (:rating note) "/100")]]
      ;; Note content
      [grid {:item true :xs 12 :sx {:mt 1 :mb 1}}
       [typography {:variant "body1" :sx {:whiteSpace "pre-wrap"}}
        (:notes note)]
       ;; WSET structured data display
       [wset-display (:wset_data note)]]
      ;; Action buttons
      [grid
       {:item true
        :xs 12
        :sx {:display "flex" :justifyContent "flex-end" :gap 1}}
       [button
        {:size "small"
         :color "primary"
         :variant "outlined"
         :onClick #(swap! app-state assoc :editing-note-id (:id note))} "Edit"]
       [button
        {:size "small"
         :color "error"
         :variant "outlined"
         :onClick #(api/delete-tasting-note app-state wine-id (:id note))}
        "Delete"]]]]))

(defn tasting-notes-list
  [app-state wine-id]
  (let [notes (:tasting-notes @app-state)
        editing-note-id (:editing-note-id @app-state)
        personal-notes (filter #(not (:is_external %)) notes)
        external-notes (filter :is_external notes)]
    [box {:sx {:mb 3}}
     [typography {:variant "h5" :component "h3" :sx {:mb 2}} "Tasting Notes"]
     (if (empty? notes)
       [typography {:variant "body1" :sx {:fontStyle "italic"}}
        "No tasting notes yet."]
       [box {:sx {:mt 2}}
        ;; Personal notes section
        (when (seq personal-notes)
          [box {:sx {:mb 3}}
           [typography {:variant "subtitle1" :component "h4" :sx {:mb 1}}
            "Your Notes"]
           (for [note personal-notes]
             (if (= (:id note) editing-note-id)
               ;; Show the edit form for this note
               ^{:key (str "edit-form-" (:id note))}
               [tasting-note-form app-state wine-id]
               ;; Show the note item
               ^{:key (str "personal-note-" (:id note))}
               [tasting-note-item app-state wine-id note]))])
        ;; External notes section
        (when (seq external-notes)
          [box
           [typography {:variant "subtitle1" :component "h4" :sx {:mb 1}}
            "External Reviews"]
           (for [note external-notes]
             (if (= (:id note) editing-note-id)
               ;; Show the edit form for this note
               ^{:key (str "edit-form-" (:id note))}
               [tasting-note-form app-state wine-id]
               ;; Show the note item
               ^{:key (str "external-note-" (:id note))}
               [tasting-note-item app-state wine-id note]))])])]))
