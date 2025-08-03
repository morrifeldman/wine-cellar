(ns wine-cellar.views.components.wset-palate
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

(defn wset-palate-section
  "WSET Level 3 Palate Assessment Component"
  [{:keys [palate wine-style on-change other-observations-ref]}]
  (r/with-let
   [expanded? (r/atom false)]
   (let [update-field (fn [field value] (on-change (assoc palate field value)))]
     [grid {:container true :spacing 2}
      ;; Section Header with collapse toggle
      [grid {:item true :xs 12}
       [grid {:container true :alignItems "center" :spacing 1}
        [grid {:item true :xs true}
         [typography {:variant "h6" :gutterBottom false} "PALATE"]]
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
         ;; Sweetness
         [grid {:item true :xs 12 :sm 6}
          [radio-group-field
           {:label "Sweetness"
            :value (:sweetness palate)
            :options (get-in wset-lexicon [:enums :sweetness])
            :on-change #(update-field :sweetness %)}]]
         ;; Acidity
         [grid {:item true :xs 12 :sm 6}
          [radio-group-field
           {:label "Acidity"
            :value (:acidity palate)
            :options (get-in wset-lexicon [:enums :acidity])
            :on-change #(update-field :acidity %)}]]
         ;; Tannin (only for reds)
         (when (#{"RED"} wine-style)
           [grid {:item true :xs 12 :sm 6}
            [radio-group-field
             {:label "Tannin"
              :value (:tannin palate)
              :options (get-in wset-lexicon [:enums :tannin])
              :on-change #(update-field :tannin %)}]])
         ;; Alcohol
         [grid {:item true :xs 12 :sm 6}
          [radio-group-field
           {:label "Alcohol"
            :value (:alcohol palate)
            :options (get-in wset-lexicon [:enums :alcohol])
            :on-change #(update-field :alcohol %)}]]
         ;; Body
         [grid {:item true :xs 12 :sm 6}
          [radio-group-field
           {:label "Body"
            :value (:body palate)
            :options (get-in wset-lexicon [:enums :body])
            :on-change #(update-field :body %)}]]
         ;; Flavor Intensity
         [grid {:item true :xs 12 :sm 6}
          [radio-group-field
           {:label "Flavor Intensity"
            :value (:flavor-intensity palate)
            :options (get-in wset-lexicon [:enums :flavour-intensity])
            :on-change #(update-field :flavor-intensity %)}]]
         ;; Flavor Characteristics
         [characteristics-section
          {:value (:flavor-characteristics palate)
           :section-title "Flavor Characteristics"
           :on-change #(update-field :flavor-characteristics %)}]
         ;; Other Observations
         [grid {:item true :xs 12}
          [uncontrolled-text-area-field
           {:label "Other Observations"
            :initial-value (:other_observations palate)
            :rows 3
            :helper-text "Additional notes about the palate"
            :reset-key (str "palate-obs-" (hash palate))
            :input-ref other-observations-ref}]]
         ;; Finish
         [grid {:item true :xs 12 :sm 6}
          [radio-group-field
           {:label "Finish"
            :value (:finish palate)
            :options (get-in wset-lexicon [:enums :finish])
            :on-change #(update-field :finish %)}]]]]]])))
