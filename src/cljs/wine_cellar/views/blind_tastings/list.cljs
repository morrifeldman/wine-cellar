(ns wine-cellar.views.blind-tastings.list
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.material.divider :refer [divider]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.link :refer [link]]
            [reagent-mui.icons.check-circle :refer [check-circle]]
            [reagent-mui.icons.visibility :refer [visibility]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.formatting :refer [format-date]]))

(defn- wset-summary
  [wset-data]
  (let [appearance (get wset-data :appearance {})
        nose (get wset-data :nose {})
        conclusions (get wset-data :conclusions {})]
    [box {:sx {:display "flex" :gap 1 :flexWrap "wrap" :mt 1}}
     (when-let [colour (:colour appearance)]
       [chip {:label (name colour) :size "small" :variant "outlined"}])
     (when-let [intensity (:intensity appearance)]
       [chip
        {:label (str "Intensity: " (name intensity))
         :size "small"
         :variant "outlined"}])
     (when-let [condition (:condition nose)]
       [chip {:label (name condition) :size "small" :variant "outlined"}])
     (when-let [quality (:quality conclusions)]
       [chip
        {:label (str "Quality: " (name quality))
         :size "small"
         :color (case quality
                  :outstanding "success"
                  :very-good "success"
                  :good "primary"
                  :acceptable "warning"
                  :poor "error"
                  "default")
         :variant "outlined"}])]))

(defn- guess-comparison-row
  [label guessed actual match?]
  [box
   {:sx {:display "flex"
         :justifyContent "space-between"
         :alignItems "center"
         :py 0.5}}
   [typography {:variant "body2" :sx {:flex 1 :fontWeight 500}} label]
   [typography {:variant "body2" :sx {:flex 1 :color "text.secondary"}}
    (or guessed "(no guess)")]
   [box {:sx {:flex 0 :px 1}}
    (cond (nil? guessed) "—"
          match? [check-circle {:sx {:color "success.main" :fontSize 18}}]
          :else "✗")]
   [typography {:variant "body2" :sx {:flex 1}} (or actual "—")]])

(defn- guess-comparison
  [wset-data wine]
  (let [guessed-varieties (get wset-data :guessed_varieties [])
        guessed-country (get wset-data :guessed_country)
        guessed-region (get wset-data :guessed_region)
        guessed-vintage (get wset-data :guessed_vintage_range)
        guessed-producer (get wset-data :guessed_producer)
        actual-varieties (mapv :name (:varieties wine))
        actual-country (:wine_country wine)
        actual-region (:wine_region wine)
        actual-vintage (:wine_vintage wine)
        actual-producer (:wine_producer wine)
        variety-match? (some (set guessed-varieties) actual-varieties)
        country-match? (and guessed-country
                            (= (str/lower-case guessed-country)
                               (str/lower-case (or actual-country ""))))
        region-match? (and guessed-region
                           (str/includes? (str/lower-case (or actual-region ""))
                                          (str/lower-case guessed-region)))
        vintage-in-range? (when (and guessed-vintage actual-vintage)
                            (let [parts (str/split guessed-vintage #"-")
                                  start (js/parseInt (first parts))
                                  end (js/parseInt (second parts))]
                              (and (>= actual-vintage start)
                                   (<= actual-vintage end))))]
    [paper {:variant "outlined" :sx {:p 2 :mt 2 :bgcolor "background.paper"}}
     [typography {:variant "subtitle2" :sx {:mb 1}} "Your Guesses vs Actual"]
     [box
      {:sx {:display "flex"
            :justifyContent "space-between"
            :borderBottom "1px solid"
            :borderColor "divider"
            :pb 0.5
            :mb 1}}
      [typography {:variant "caption" :sx {:flex 1 :fontWeight 600}} ""]
      [typography {:variant "caption" :sx {:flex 1 :fontWeight 600}} "Guessed"]
      [typography {:variant "caption" :sx {:flex 0 :px 1}} ""]
      [typography {:variant "caption" :sx {:flex 1 :fontWeight 600}} "Actual"]]
     [guess-comparison-row "Variety"
      (when (seq guessed-varieties) (str/join ", " guessed-varieties))
      (when (seq actual-varieties) (str/join ", " actual-varieties))
      variety-match?]
     [guess-comparison-row "Country" guessed-country actual-country
      country-match?]
     [guess-comparison-row "Region" guessed-region actual-region region-match?]
     [guess-comparison-row "Vintage" guessed-vintage
      (when actual-vintage (str actual-vintage)) vintage-in-range?]
     [guess-comparison-row "Producer" guessed-producer actual-producer
      (and guessed-producer
           actual-producer
           (str/includes? (str/lower-case actual-producer)
                          (str/lower-case guessed-producer)))]]))

(defn- blind-tasting-card
  [app-state note]
  (let [linked? (some? (:wine_id note))
        wset-data (:wset_data note)]
    [paper {:elevation 2 :sx {:p 2 :mb 2}}
     [box
      {:sx {:display "flex"
            :justifyContent "space-between"
            :alignItems "flex-start"}}
      [box {:sx {:flex 1}}
       [box {:sx {:display "flex" :alignItems "center" :gap 1 :mb 1}}
        [typography {:variant "subtitle1" :sx {:fontWeight 600}}
         (format-date (:tasting_date note))]
        (if linked?
          [chip
           {:label "Linked"
            :size "small"
            :color "success"
            :icon (r/as-element [check-circle])}]
          [chip {:label "Pending" :size "small" :color "warning"}])]
       (when linked?
         [typography {:variant "body2" :color "text.secondary"}
          (str (:wine_producer note)
               (when (:wine_name note) (str " " (:wine_name note)))
               (when (:wine_vintage note) (str " " (:wine_vintage note))))])
       (when wset-data [wset-summary wset-data])
       (when (:rating note)
         [typography {:variant "body2" :sx {:mt 1}}
          (str "Rating: " (:rating note) "/100")])]
      [box {:sx {:display "flex" :gap 1}}
       (when-not linked?
         [button
          {:variant "outlined"
           :size "small"
           :startIcon (r/as-element [link])
           :onClick #(do (swap! app-state assoc-in
                           [:blind-tastings :linking-note-id]
                           (:id note))
                         (swap! app-state assoc-in
                           [:blind-tastings :show-link-dialog?]
                           true))} "Link"])
       [button
        {:variant "text"
         :size "small"
         :startIcon (r/as-element [visibility])
         :onClick #(swap! app-state assoc :viewing-blind-note note)} "View"]]]
     (when (and linked? wset-data) [guess-comparison wset-data note])]))

(defn blind-tastings-page
  [app-state]
  (r/create-class
   {:component-did-mount (fn [_] (api/fetch-blind-tastings app-state))
    :reagent-render
    (fn [app-state]
      (let [blind-state (:blind-tastings @app-state)
            loading? (:loading? blind-state)
            notes (:list blind-state)
            pending (filter #(nil? (:wine_id %)) notes)
            completed (filter #(some? (:wine_id %)) notes)]
        [box {:sx {:p 2}}
         [box
          {:sx {:display "flex"
                :justifyContent "space-between"
                :alignItems "center"
                :mb 3}} [typography {:variant "h5"} "Blind Tastings"]
          [button
           {:variant "contained"
            :startIcon (r/as-element [add])
            :onClick
            #(swap! app-state assoc-in [:blind-tastings :show-form?] true)}
           "New Blind Tasting"]]
         (if loading?
           [box {:sx {:display "flex" :justifyContent "center" :py 4}}
            [circular-progress]]
           [box
            (when (seq pending)
              [box {:sx {:mb 4}}
               [typography {:variant "h6" :sx {:mb 2 :color "warning.main"}}
                (str "Pending (" (count pending) ")")]
               (for [note pending]
                 ^{:key (:id note)} [blind-tasting-card app-state note])])
            (when (and (seq pending) (seq completed)) [divider {:sx {:my 3}}])
            (when (seq completed)
              [box
               [typography {:variant "h6" :sx {:mb 2 :color "success.main"}}
                (str "Completed (" (count completed) ")")]
               (for [note completed]
                 ^{:key (:id note)} [blind-tasting-card app-state note])])
            (when (and (empty? pending) (empty? completed))
              [paper {:sx {:p 4 :textAlign "center"}}
               [typography {:variant "body1" :color "text.secondary"}
                "No blind tastings yet. Start one to practice your blind tasting skills!"]])])]))}))
