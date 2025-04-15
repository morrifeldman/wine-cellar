(ns wine-cellar.views.tasting-notes.list
  (:require [wine-cellar.utils.formatting :refer [format-date]]
            [wine-cellar.api :as api]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.chip :refer [chip]]))

(defn get-rating-color
  [rating]
  (cond (>= rating 90) "rating.high"
        (>= rating 80) "rating.medium"
        :else "rating.low"))

(defn tasting-note-item
  [app-state wine-id note]
  (let [is-external (boolean (:is_external note))]
    [paper
     {:elevation 1
      :sx {:p 2
           :mb 2
           :borderLeft (when is-external "4px solid #9e9e9e")
           :bgcolor (when is-external "rgba(0, 0, 0, 0.02)")}}
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
        (:notes note)]]
      ;; Delete button
      [grid {:item true :xs 12 :sx {:display "flex" :justifyContent "flex-end"}}
       [button
        {:size "small"
         :color "error"
         :variant "outlined"
         :onClick #(api/delete-tasting-note app-state wine-id (:id note))}
        "Delete"]]]]))

(defn tasting-notes-list
  [app-state wine-id]
  (let [notes (:tasting-notes @app-state)
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
             ^{:key (:id note)} [tasting-note-item app-state wine-id note])])
        ;; External notes section
        (when (seq external-notes)
          [box
           [typography {:variant "subtitle1" :component "h4" :sx {:mb 1}}
            "External Reviews"]
           (for [note external-notes]
             ^{:key (:id note)}
             [tasting-note-item app-state wine-id note])])])]))
