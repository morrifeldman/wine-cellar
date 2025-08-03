(ns wine-cellar.views.components.wset-nose
  (:require
    [reagent.core :as r]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.radio-group :refer [radio-group]]
    [reagent-mui.material.form-control-label :refer [form-control-label]]
    [reagent-mui.material.radio :refer [radio]]
    [reagent-mui.material.collapse :refer [collapse]]
    [reagent-mui.material.icon-button :refer [icon-button]]
    [reagent-mui.icons.expand-more :refer [expand-more]]
    [wine-cellar.common :refer [wset-lexicon]]
    [wine-cellar.views.components.form :refer [uncontrolled-text-area-field]]
    [wine-cellar.views.components.wset-shared :refer
     [characteristics-section]]))

(defn- radio-group-field
  "Simple radio group for WSET enum selections"
  [{:keys [label value options on-change]}]
  [grid {:item true :xs 12 :sm 6}
   [typography {:variant "subtitle2" :gutterBottom true} label]
   [radio-group
    {:value (or value "")
     :onChange #(on-change (-> %
                               .-target
                               .-value))}
    (for [option options]
      ^{:key option}
      [form-control-label
       {:value option
        :control (r/as-element [radio {:size "small"}])
        :label option}])]])

(defn wset-nose-section
  "WSET Level 3 Nose section component"
  [{:keys [nose on-change other-observations-ref]}]
  (r/with-let
   [expanded? (r/atom false)]
   (let [update-field (fn [field value] (on-change (assoc nose field value)))]
     [grid {:container true :spacing 2}
      ;; Section Header with collapse toggle
      [grid {:item true :xs 12}
       [grid {:container true :alignItems "center" :spacing 1}
        [grid {:item true :xs true}
         [typography {:variant "h6" :gutterBottom false} "NOSE"]]
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
         ;; Condition
         [radio-group-field
          {:label "Condition"
           :value (:condition nose)
           :options (get-in wset-lexicon [:enums :condition])
           :on-change #(update-field :condition %)}]
         ;; Intensity
         [radio-group-field
          {:label "Intensity"
           :value (:intensity nose)
           :options (get-in wset-lexicon [:enums :nose-intensity])
           :on-change #(update-field :intensity %)}]
         ;; Development
         [radio-group-field
          {:label "Development"
           :value (:development nose)
           :options (get-in wset-lexicon [:enums :development])
           :on-change #(update-field :development %)}]
         ;; Aroma Characteristics
         [characteristics-section
          {:value (:aroma-characteristics nose)
           :section-title "Aroma Characteristics"
           :on-change #(update-field :aroma-characteristics %)}]
         ;; Other Observations
         [grid {:item true :xs 12}
          [uncontrolled-text-area-field
           {:label "Other Observations"
            :initial-value (:other_observations nose)
            :rows 2
            :helper-text "Additional notes about the nose"
            :reset-key (str "nose-obs-" (hash nose))
            :input-ref other-observations-ref}]]]]]])))
