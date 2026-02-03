(ns wine-cellar.views.blind-tastings.form
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.divider :refer [divider]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.autocomplete :refer [autocomplete]]
            [reagent-mui.material.chip :refer [chip]]
            [wine-cellar.api :as api]
            [wine-cellar.common :refer [wine-styles style->info]]
            [wine-cellar.utils.mui :refer [safe-js-props]]
            [wine-cellar.views.components.form :refer
             [date-field number-field select-field]]
            [wine-cellar.views.components.wset-appearance :refer
             [wset-appearance-section]]
            [wine-cellar.views.components.wset-nose :refer [wset-nose-section]]
            [wine-cellar.views.components.wset-palate :refer
             [wset-palate-section]]
            [wine-cellar.views.components.wset-conclusions :refer
             [wset-conclusions-section]]))

(defn- guesses-section
  [app-state]
  (let [classifications (r/cursor app-state [:classifications])
        varieties (r/cursor app-state [:grape-varieties])
        country-options (r/reaction (->> @classifications
                                         (map :country)
                                         (remove nil?)
                                         distinct
                                         sort
                                         clj->js))
        variety-options (r/reaction (->> @varieties
                                         (map :name)
                                         sort
                                         clj->js))]
    (fn [app-state]
      (let [form-data (get-in @app-state [:blind-tastings :form :wset_data] {})
            selected-country (:guessed_country form-data)
            region-options (->> @classifications
                                (filter #(or (empty? selected-country)
                                             (= (:country %) selected-country)))
                                (map :region)
                                (remove nil?)
                                distinct
                                sort
                                clj->js)]
        [box {:sx {:mt 3}}
         [typography {:variant "h6" :sx {:mb 2}} "Your Guesses"]
         [typography {:variant "body2" :color "text.secondary" :sx {:mb 2}}
          "Record your guesses before revealing the wine. These will be compared when you link this tasting to a wine."]
         [grid {:container true :spacing 2}
          [grid {:item true :xs 12}
           [autocomplete
            {:multiple true
             :freeSolo true
             :options @variety-options
             :value (clj->js (or (:guessed_varieties form-data) []))
             :onChange (fn [_ v]
                         (swap! app-state assoc-in
                           [:blind-tastings :form :wset_data :guessed_varieties]
                           (js->clj v)))
             :renderTags
             (fn [value getTagProps]
               (r/as-element
                [:<>
                 (map-indexed
                  (fn [idx v]
                    (let [props (js->clj (getTagProps #js {:index idx})
                                         :keywordize-keys
                                         true)]
                      [chip (merge {:label v :size "small"} props)]))
                  (js->clj value))]))
             :renderInput (fn [params]
                            (r/as-element
                             [mui-text-field/text-field
                              (merge (safe-js-props params)
                                     {:label "Guessed Varieties"
                                      :variant "outlined"
                                      :size "small"
                                      :fullWidth true
                                      :helperText
                                      "Type variety names, press Enter"})]))}]]
          [grid {:item true :xs 12 :sm 6}
           [autocomplete
            {:freeSolo true
             :options @country-options
             :value (or (:guessed_country form-data) "")
             :onInputChange (fn [_ new-value _]
                              (swap! app-state assoc-in
                                [:blind-tastings :form :wset_data
                                 :guessed_country]
                                new-value))
             :renderInput (fn [params]
                            (r/as-element [mui-text-field/text-field
                                           (merge (safe-js-props params)
                                                  {:label "Guessed Country"
                                                   :variant "outlined"
                                                   :size "small"
                                                   :fullWidth true})]))}]]
          [grid {:item true :xs 12 :sm 6}
           [autocomplete
            {:freeSolo true
             :options region-options
             :value (or (:guessed_region form-data) "")
             :onInputChange (fn [_ new-value _]
                              (swap! app-state assoc-in
                                [:blind-tastings :form :wset_data
                                 :guessed_region]
                                new-value))
             :renderInput (fn [params]
                            (r/as-element [mui-text-field/text-field
                                           (merge (safe-js-props params)
                                                  {:label "Guessed Region"
                                                   :variant "outlined"
                                                   :size "small"
                                                   :fullWidth true})]))}]]
          [grid {:item true :xs 12 :sm 6}
           [number-field
            {:label "Guessed Vintage"
             :fullWidth true
             :placeholder "YYYY"
             :min 1900
             :max 2100
             :value (or (:guessed_vintage form-data) "")
             :on-change (fn [val]
                          (let [parsed (js/parseInt val)]
                            (swap! app-state assoc-in
                              [:blind-tastings :form :wset_data
                               :guessed_vintage]
                              (if (js/isNaN parsed) nil parsed))))}]]
          [grid {:item true :xs 12 :sm 6}
           [mui-text-field/text-field
            {:label "Guessed Producer (optional)"
             :fullWidth true
             :size "small"
             :value (or (:guessed_producer form-data) "")
             :onChange #(swap! app-state assoc-in
                          [:blind-tastings :form :wset_data :guessed_producer]
                          (.. % -target -value))}]]]]))))

(defn blind-tasting-form-dialog
  [app-state]
  (r/with-let
   [other-observations-ref (r/atom nil) nose-observations-ref (r/atom nil)
    palate-observations-ref (r/atom nil) final-comments-ref (r/atom nil)]
   (let [blind-state (:blind-tastings @app-state)
         open? (:show-form? blind-state)
         submitting? (:submitting? blind-state)
         form-data (or (:form blind-state) {})
         wset-data (or (:wset_data form-data) {})
         selected-style (get wset-data :wset_wine_style "Red")
         style-info (style->info selected-style)
         close! #(swap! app-state update
                   :blind-tastings
                   (fn [s]
                     (-> s
                         (assoc :show-form? false)
                         (assoc :form {}))))
         handle-submit
         (fn []
           (let [other-obs (when @other-observations-ref
                             (.-value @other-observations-ref))
                 nose-obs (when @nose-observations-ref
                            (.-value @nose-observations-ref))
                 palate-obs (when @palate-observations-ref
                              (.-value @palate-observations-ref))
                 final-comments (when @final-comments-ref
                                  (.-value @final-comments-ref))
                 updated-wset
                 (cond-> wset-data
                   other-obs (assoc-in [:appearance :other_observations]
                              other-obs)
                   nose-obs (assoc-in [:nose :other_observations] nose-obs)
                   palate-obs (assoc-in [:palate :other_observations]
                               palate-obs)
                   final-comments (assoc-in [:conclusions :final_comments]
                                   final-comments)
                   true (assoc :note_type "wset_level_3" :version "1.0"))
                 note-data {:tasting_date (:tasting_date form-data)
                            :rating (when-let [r (:rating form-data)]
                                      (if (string? r) (js/parseInt r) r))
                            :wset_data updated-wset}]
             (api/create-blind-tasting app-state note-data)))]
     [dialog {:open open? :onClose close! :maxWidth "md" :fullWidth true}
      [dialog-title "New Blind Tasting"]
      [dialog-content {:sx {:pt 2}}
       [typography {:variant "body2" :color "text.secondary" :sx {:mb 3}}
        "Record your tasting notes without knowing the wine. You can link this to the actual wine later to see how well you guessed."]
       [grid {:container true :spacing 2}
        [grid {:item true :xs 12 :sm 6}
         [date-field
          {:label "Tasting Date"
           :required true
           :value (:tasting_date form-data)
           :on-change #(swap! app-state assoc-in
                         [:blind-tastings :form :tasting_date]
                         %)}]]
        [grid {:item true :xs 12 :sm 6}
         [number-field
          {:label "Rating (1-100)"
           :min 1
           :max 100
           :value (:rating form-data)
           :on-change #(swap! app-state assoc-in
                         [:blind-tastings :form :rating]
                         (js/parseInt %))}]]] [divider {:sx {:my 3}}]
       [typography {:variant "h6" :sx {:mb 2}} "WSET Tasting Notes"]
       [grid {:container true :spacing 2}
        [grid {:item true :xs 12}
         [select-field
          {:label "Wine Style (for Palate/Color)"
           :value selected-style
           :options (sort wine-styles)
           :required true
           :on-change #(swap! app-state assoc-in
                         [:blind-tastings :form :wset_data :wset_wine_style]
                         %)}]]
        [grid {:item true :xs 12}
         [wset-appearance-section
          {:appearance (get wset-data :appearance {})
           :style-info style-info
           :other-observations-ref other-observations-ref
           :on-change #(swap! app-state assoc-in
                         [:blind-tastings :form :wset_data :appearance]
                         %)}]]
        [grid {:item true :xs 12}
         [wset-nose-section
          {:nose (get wset-data :nose {})
           :other-observations-ref nose-observations-ref
           :on-change #(swap! app-state assoc-in
                         [:blind-tastings :form :wset_data :nose]
                         %)}]]
        [grid {:item true :xs 12}
         [wset-palate-section
          {:palate (get wset-data :palate {})
           :style-info style-info
           :other-observations-ref palate-observations-ref
           :nose (get wset-data :nose {})
           :on-change #(swap! app-state assoc-in
                         [:blind-tastings :form :wset_data :palate]
                         %)}]]
        [grid {:item true :xs 12}
         [wset-conclusions-section
          {:conclusions (get wset-data :conclusions {})
           :final-comments-ref final-comments-ref
           :on-change #(swap! app-state assoc-in
                         [:blind-tastings :form :wset_data :conclusions]
                         %)}]]] [guesses-section app-state]]
      [dialog-actions [button {:onClick close!} "Cancel"]
       [button
        {:variant "contained" :disabled submitting? :onClick handle-submit}
        (if submitting? "Saving..." "Save Blind Tasting")]]])))
