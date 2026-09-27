(ns wine-cellar.views.components.wset-appearance
  (:require
    [reagent.core :as r]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.collapse :refer [collapse]]
    [reagent-mui.material.icon-button :refer [icon-button]]
    [reagent-mui.icons.expand-more :refer [expand-more]]
    [wine-cellar.common :refer [wset-lexicon]]
    [wine-cellar.views.components.wset-shared :refer [radio-group-field]]
    [wine-cellar.views.components.form :refer [uncontrolled-text-area-field]]
    [wine-cellar.views.components.wine-color :refer [wine-color-selector]]))


(defn wset-appearance-section
  "WSET Level 3 Appearance section component"
  [{:keys [appearance style-info on-change other-observations-ref]}]
  (r/with-let
   [expanded? (r/atom true)]
   (let [update-field (fn [field value]
                        (on-change (assoc appearance field value)))]
     [grid {:container true :spacing 2}
      ;; Section Header with collapse toggle
      [grid {:item true :xs 12}
       [grid {:container true :alignItems "center" :spacing 1}
        [grid {:item true :xs true}
         [typography {:variant "h6" :gutterBottom false} "APPEARANCE"]]
        [grid {:item true :xs "auto"}
         [icon-button
          {:size "small"
           :onClick #(swap! expanded? not)
           :sx {:transform (if @expanded? "rotate(180deg)" "rotate(0deg)")
                :transition "transform 0.2s ease-in-out"
                :color "text.primary"}}
          [expand-more {:sx {:color "text.primary"}}]]]]]
      ;; Collapsible content
      [grid {:item true :xs 12}
       [collapse {:in @expanded?}
        [grid {:container true :spacing 2 :sx {:pt 1}}
         ;; Clarity
         [radio-group-field
          {:label "Clarity"
           :value (:clarity appearance)
           :options (get-in wset-lexicon [:enums :clarity])
           :on-change #(update-field :clarity %)}]
         ;; Wine Color (Wine Folly colors with separate intensity)
         [grid {:item true :xs 12}
          [wine-color-selector
           {:style-info style-info
            :selected-color (:colour appearance)
            :selected-intensity (:intensity appearance)
            :on-change (fn [{:keys [color intensity]}]
                         (on-change (-> appearance
                                        (assoc :colour color)
                                        (assoc :intensity intensity))))}]]
         ;; Other Observations
         [grid {:item true :xs 12}
          [uncontrolled-text-area-field
           {:label "Other Observations"
            :initial-value (:other_observations appearance)
            :rows 2
            :helper-text "e.g. legs/tears, deposit, pétillance, bubbles"
            :reset-key (str "other-obs-"
                            (hash (:other_observations appearance)))
            :input-ref other-observations-ref}]]]]]])))
