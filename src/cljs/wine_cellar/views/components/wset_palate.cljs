(ns wine-cellar.views.components.wset-palate
  (:require
    [reagent.core :as r]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.radio-group :refer [radio-group]]
    [reagent-mui.material.form-control-label :refer [form-control-label]]
    [reagent-mui.material.radio :refer [radio]]
    [reagent-mui.material.collapse :refer [collapse]]
    [reagent-mui.material.icon-button :refer [icon-button]]
    [reagent-mui.icons.expand-more :refer [expand-more]]
    [wine-cellar.common :refer [wset-lexicon]]
    [wine-cellar.views.components.form :refer [text-field select-field]]))

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

(defn- flavor-characteristics-section
  "Categorized flavor characteristics selection with individual category dropdowns"
  [{:keys [value on-change]}]
  [grid {:item true :xs 12}
   [typography {:variant "subtitle2" :gutterBottom true}
    "Flavor Characteristics"]
   ;; Primary Flavors
   [grid {:container true :spacing 1 :sx {:mb 3}}
    [grid {:item true :xs 12}
     [typography {:variant "body2" :sx {:fontWeight "bold" :mb 1}} "Primary"]]
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
      :on-change #(on-change (assoc-in value [:primary :other] %))}]]
   ;; Secondary Flavors
   [grid {:container true :spacing 1 :sx {:mb 3}}
    [grid {:item true :xs 12}
     [typography {:variant "body2" :sx {:fontWeight "bold" :mb 1}} "Secondary"]]
    ;; Yeast
    [category-dropdown
     {:label "Yeast"
      :options (get-in wset-lexicon [:secondary :yeast])
      :all-values (get-in value [:secondary :yeast])
      :on-change #(on-change (assoc-in value [:secondary :yeast] %))}]
    ;; Malolactic Fermentation
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
      :on-change #(on-change (assoc-in value [:secondary :oak] %))}]]
   ;; Tertiary Flavors
   [grid {:container true :spacing 1 :sx {:mb 3}}
    [grid {:item true :xs 12}
     [typography {:variant "body2" :sx {:fontWeight "bold" :mb 1}} "Tertiary"]]
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
      :on-change #(on-change (assoc-in value [:tertiary :bottle-age] %))}]]])

(defn wset-palate-section
  "WSET Level 3 Palate Assessment Component"
  [{:keys [palate wine-style on-change]}]
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
         [flavor-characteristics-section
          {:value (:flavor-characteristics palate)
           :on-change #(update-field :flavor-characteristics %)}]
         ;; Other Observations
         [grid {:item true :xs 12}
          [text-field
           {:label "Other Observations"
            :value (:other-observations palate)
            :multiline true
            :rows 3
            :placeholder "Additional notes about the palate"
            :on-change #(update-field :other-observations %)}]]
         ;; Finish
         [grid {:item true :xs 12 :sm 6}
          [radio-group-field
           {:label "Finish"
            :value (:finish palate)
            :options (get-in wset-lexicon [:enums :finish])
            :on-change #(update-field :finish %)}]]]]]])))