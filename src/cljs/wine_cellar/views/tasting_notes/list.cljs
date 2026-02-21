(ns wine-cellar.views.tasting-notes.list
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.divider :refer [divider]]
            [reagent-mui.material.typography :refer [typography]]
            [wine-cellar.utils.formatting :refer [format-date]]
            [wine-cellar.views.components.wset-shared :refer [wset-display]]))

(defn get-rating-color
  [rating]
  (cond (>= rating 90) "rating.high"
        (>= rating 80) "rating.medium"
        :else "rating.low"))

(defn tasting-note-item
  [app-state _wine-id note]
  (let [is-external (boolean (:is_external note))]
    [paper
     {:elevation 1
      :sx {:p 2
           :mb 2
           :borderLeft
           (if is-external "4px solid #9e9e9e" "4px solid rgba(240,98,146,0.7)")
           :cursor "pointer"
           "&:hover" {:boxShadow 3}}
      :onClick #(swap! app-state assoc :editing-note-id (:id note))}
     [grid {:container true}
      ;; Header with date/source and rating
      [grid {:item true :xs 9}
       (if is-external
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
         [typography {:variant "subtitle1" :sx {:fontWeight "bold"}}
          (format-date (:tasting_date note))])]
      [grid {:item true :xs 3 :sx {:textAlign "right"}}
       [typography
        {:variant "subtitle1"
         :sx {:color (get-rating-color (:rating note)) :fontWeight "bold"}}
        (str (:rating note) "/100")]]
      [grid {:item true :xs 12 :sx {:mt 1}}
       [typography {:variant "body1" :sx {:whiteSpace "pre-wrap"}}
        (:notes note)] [wset-display (:wset_data note)]]]]))

(defn tasting-notes-list
  [app-state wine-id]
  (let [notes (:tasting-notes @app-state)
        personal-notes (filter #(not (:is_external %)) notes)
        external-notes (filter :is_external notes)]
    [box {:sx {:mb 3}}
     (if (empty? notes)
       [typography {:variant "body1" :sx {:fontStyle "italic"}}
        "No tasting notes yet."]
       [box {:sx {:mt 2}}
        ;; Personal notes section
        (when (seq personal-notes)
          [box {:sx {:mb 2}}
           (for [note personal-notes]
             ^{:key (str "personal-note-" (:id note))}
             [tasting-note-item app-state wine-id note])])
        ;; Divider between personal and external sections
        (when (and (seq personal-notes) (seq external-notes))
          [divider
           {:sx
            {:my 2 :borderColor "rgba(240,98,146,0.7)" :borderTopWidth "3px"}}])
        ;; External notes section
        (when (seq external-notes)
          [box
           (for [note external-notes]
             ^{:key (str "external-note-" (:id note))}
             [tasting-note-item app-state wine-id note])])])]))
