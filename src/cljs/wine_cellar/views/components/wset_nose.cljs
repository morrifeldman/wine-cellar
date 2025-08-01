(ns wine-cellar.views.components.wset-nose
  (:require [reagent.core :as r]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.radio-group :refer [radio-group]]
            [reagent-mui.material.form-control-label :refer
             [form-control-label]]
            [reagent-mui.material.radio :refer [radio]]
            [reagent-mui.material.collapse :refer [collapse]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.expand-more :refer [expand-more]]
            [wine-cellar.common :refer [wset-lexicon]]
            [wine-cellar.views.components.form :refer
             [select-field uncontrolled-text-area-field]]))

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

(defn- category-dropdown
  "Individual category dropdown with free-form input support"
  [{:keys [label options value all-values on-change]}]
  (r/with-let
   [dynamic-options (r/atom options)]
   [grid {:item true :xs 12 :sm 6}
    [select-field
     {:label label
      :value (filter #(some #{%} @dynamic-options) (or all-values []))
      :options @dynamic-options
      :multiple true
      :free-solo true
      :required false
      :on-change #(do
                    ;; Add any new custom values to the dynamic options
                    (doseq [item %]
                      (when (and item (not (some #{item} @dynamic-options)))
                        (swap! dynamic-options conj item)))
                    ;; Update the parent state
                    (on-change %))
      :sx {"& .MuiAutocomplete-endAdornment .MuiSvgIcon-root"
           {:color "text.primary"}}}]]))

(defn- aroma-characteristics-section
  "Categorized aroma characteristics selection with individual category dropdowns"
  [{:keys [value on-change]}]
  (r/with-let
   [primary-expanded? (r/atom false) secondary-expanded? (r/atom false)
    tertiary-expanded? (r/atom false)]
   [grid {:item true :xs 12}
    [typography {:variant "subtitle2" :gutterBottom true}
     "Aroma Characteristics"]
    ;; Primary Aromas
    [grid {:container true :spacing 1 :sx {:mb 3}}
     [grid {:item true :xs 12}
      [grid {:container true :alignItems "center" :spacing 1}
       [grid {:item true :xs true}
        [typography {:variant "body2" :sx {:fontWeight "bold" :mb 1}}
         "Primary"]]
       [grid {:item true :xs "auto"}
        [icon-button
         {:size "small"
          :onClick #(swap! primary-expanded? not)
          :sx {:transform
               (if @primary-expanded? "rotate(180deg)" "rotate(0deg)")
               :transition "transform 0.2s ease-in-out"
               :color "text.primary"}}
         [expand-more {:sx {:color "text.primary" :fontSize "1rem"}}]]]]]
     [collapse {:in @primary-expanded?}
      [grid {:container true :spacing 1 :sx {:pt 1}}
       ;; Floral
       [category-dropdown
        {:label "Floral"
         :options (get-in wset-lexicon [:primary :floral])
         :all-values (get-in value [:primary :floral])
         :on-change #(on-change (assoc-in value [:primary :floral] %))}]
       ;; Green Fruit
       [category-dropdown
        {:label "Green Fruit"
         :options (get-in wset-lexicon [:primary :green-fruit])
         :all-values (get-in value [:primary :green-fruit])
         :on-change #(on-change (assoc-in value [:primary :green-fruit] %))}]
       ;; Citrus Fruit
       [category-dropdown
        {:label "Citrus Fruit"
         :options (get-in wset-lexicon [:primary :citrus-fruit])
         :all-values (get-in value [:primary :citrus-fruit])
         :on-change #(on-change (assoc-in value [:primary :citrus-fruit] %))}]
       ;; Stone Fruit
       [category-dropdown
        {:label "Stone Fruit"
         :options (get-in wset-lexicon [:primary :stone-fruit])
         :all-values (get-in value [:primary :stone-fruit])
         :on-change #(on-change (assoc-in value [:primary :stone-fruit] %))}]
       ;; Tropical Fruit
       [category-dropdown
        {:label "Tropical Fruit"
         :options (get-in wset-lexicon [:primary :tropical-fruit])
         :all-values (get-in value [:primary :tropical-fruit])
         :on-change #(on-change (assoc-in value [:primary :tropical-fruit] %))}]
       ;; Red Fruit
       [category-dropdown
        {:label "Red Fruit"
         :options (get-in wset-lexicon [:primary :red-fruit])
         :all-values (get-in value [:primary :red-fruit])
         :on-change #(on-change (assoc-in value [:primary :red-fruit] %))}]
       ;; Black Fruit
       [category-dropdown
        {:label "Black Fruit"
         :options (get-in wset-lexicon [:primary :black-fruit])
         :all-values (get-in value [:primary :black-fruit])
         :on-change #(on-change (assoc-in value [:primary :black-fruit] %))}]
       ;; Dried/Cooked Fruit
       [category-dropdown
        {:label "Dried/Cooked Fruit"
         :options (get-in wset-lexicon [:primary :dried-cooked-fruit])
         :all-values (get-in value [:primary :dried-cooked-fruit])
         :on-change #(on-change
                      (assoc-in value [:primary :dried-cooked-fruit] %))}]
       ;; Herbaceous
       [category-dropdown
        {:label "Herbaceous"
         :options (get-in wset-lexicon [:primary :herbaceous])
         :all-values (get-in value [:primary :herbaceous])
         :on-change #(on-change (assoc-in value [:primary :herbaceous] %))}]
       ;; Herbal
       [category-dropdown
        {:label "Herbal"
         :options (get-in wset-lexicon [:primary :herbal])
         :all-values (get-in value [:primary :herbal])
         :on-change #(on-change (assoc-in value [:primary :herbal] %))}]
       ;; Pungent Spice
       [category-dropdown
        {:label "Pungent Spice"
         :options (get-in wset-lexicon [:primary :pungent-spice])
         :all-values (get-in value [:primary :pungent-spice])
         :on-change #(on-change (assoc-in value [:primary :pungent-spice] %))}]
       ;; Other Primary
       [category-dropdown
        {:label "Other Primary"
         :options (get-in wset-lexicon [:primary :other])
         :all-values (get-in value [:primary :other])
         :on-change #(on-change (assoc-in value [:primary :other] %))}]]]]
    ;; Secondary Aromas
    [grid {:container true :spacing 1 :sx {:mb 3}}
     [grid {:item true :xs 12}
      [grid {:container true :alignItems "center" :spacing 1}
       [grid {:item true :xs true}
        [typography {:variant "body2" :sx {:fontWeight "bold" :mb 1}}
         "Secondary"]]
       [grid {:item true :xs "auto"}
        [icon-button
         {:size "small"
          :onClick #(swap! secondary-expanded? not)
          :sx {:transform
               (if @secondary-expanded? "rotate(180deg)" "rotate(0deg)")
               :transition "transform 0.2s ease-in-out"
               :color "text.primary"}}
         [expand-more {:sx {:color "text.primary" :fontSize "1rem"}}]]]]]
     [collapse {:in @secondary-expanded?}
      [grid {:container true :spacing 1 :sx {:pt 1}}
       ;; Yeast
       [category-dropdown
        {:label "Yeast"
         :options (get-in wset-lexicon [:secondary :yeast])
         :all-values (get-in value [:secondary :yeast])
         :on-change #(on-change (assoc-in value [:secondary :yeast] %))}]
       ;; MLF
       [category-dropdown
        {:label "Malolactic Fermentation"
         :options (get-in wset-lexicon [:secondary :mlf])
         :all-values (get-in value [:secondary :mlf])
         :on-change #(on-change (assoc-in value [:secondary :mlf] %))}]
       ;; Oak
       [category-dropdown
        {:label "Oak"
         :options (get-in wset-lexicon [:secondary :oak])
         :all-values (get-in value [:secondary :oak])
         :on-change #(on-change (assoc-in value [:secondary :oak] %))}]]]]
    ;; Tertiary Aromas
    [grid {:container true :spacing 1 :sx {:mb 2}}
     [grid {:item true :xs 12}
      [grid {:container true :alignItems "center" :spacing 1}
       [grid {:item true :xs true}
        [typography {:variant "body2" :sx {:fontWeight "bold" :mb 1}}
         "Tertiary"]]
       [grid {:item true :xs "auto"}
        [icon-button
         {:size "small"
          :onClick #(swap! tertiary-expanded? not)
          :sx {:transform
               (if @tertiary-expanded? "rotate(180deg)" "rotate(0deg)")
               :transition "transform 0.2s ease-in-out"
               :color "text.primary"}}
         [expand-more {:sx {:color "text.primary" :fontSize "1rem"}}]]]]]
     [collapse {:in @tertiary-expanded?}
      [grid {:container true :spacing 1 :sx {:pt 1}}
       ;; Deliberate Oxidation
       [category-dropdown
        {:label "Deliberate Oxidation"
         :options (get-in wset-lexicon [:tertiary :deliberate-oxidation])
         :all-values (get-in value [:tertiary :deliberate-oxidation])
         :on-change #(on-change
                      (assoc-in value [:tertiary :deliberate-oxidation] %))}]
       ;; Fruit Development
       [category-dropdown
        {:label "Fruit Development"
         :options (get-in wset-lexicon [:tertiary :fruit-development])
         :all-values (get-in value [:tertiary :fruit-development])
         :on-change #(on-change
                      (assoc-in value [:tertiary :fruit-development] %))}]
       ;; Bottle Age
       [category-dropdown
        {:label "Bottle Age"
         :options (get-in wset-lexicon [:tertiary :bottle-age])
         :all-values (get-in value [:tertiary :bottle-age])
         :on-change #(on-change
                      (assoc-in value [:tertiary :bottle-age] %))}]]]]]))

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
         [aroma-characteristics-section
          {:value (:aroma-characteristics nose)
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
