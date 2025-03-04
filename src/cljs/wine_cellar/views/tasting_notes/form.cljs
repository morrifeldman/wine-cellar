(ns wine-cellar.views.tasting-notes.form
  (:require [reagent.core :as r]
            [wine-cellar.views.components :refer [smart-field date-field form-field-style]]
            [wine-cellar.api :as api]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]))

(defn tasting-note-form [app-state wine-id]
  (let [new-note (:new-tasting-note @app-state)]
    [paper {:elevation 1 :sx {:p 3 :mb 3}}
     [typography {:variant "h6" :sx {:mb 2}} "Add Tasting Note"]
     [:form {:on-submit (fn [e]
                     (.preventDefault e)
                     (api/create-tasting-note app-state wine-id 
                       (update new-note :rating #(if (string? %) (js/parseInt %) %))))}

      [grid {:container true :spacing 2}
       ;; Date input
       [grid {:item true :xs 12 :md 6}
        [date-field {:label "Tasting Date"
                     :required true
                     :value (:tasting_date new-note)
                     :on-change #(swap! app-state assoc-in
                                        [:new-tasting-note :tasting_date] %)}]]

       ;; Tasting notes textarea
       [grid {:item true :xs 12}
        [text-field
         {:label "Notes"
          :multiline true
          :rows 4
          :required true
          :fullWidth true
          :value (:notes new-note)
          :sx form-field-style
          :variant "outlined"
          :onChange #(swap! app-state assoc-in [:new-tasting-note :notes]
                            (.. % -target -value))}]]

       ;; Rating input
       [grid {:item true :xs 12 :md 6}
        [smart-field app-state [:new-tasting-note :rating]
         :label "Rating (1-100)"
         :type "number"
         :required true
         :min 1
         :max 100
         :on-change #(swap! app-state assoc-in [:new-tasting-note :rating]
                            (js/parseInt %))]]

       ;; Submit button
       [grid {:item true :xs 12 :sx {:textAlign "right" :mt 2}}
        [button
         {:type "submit"
          :variant "contained"
          :color "primary"}
         "Add Note"]]]]]))
