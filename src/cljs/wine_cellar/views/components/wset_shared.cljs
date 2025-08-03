(ns wine-cellar.views.components.wset-shared
  (:require [reagent.core :as r]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.collapse :refer [collapse]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.expand-more :refer [expand-more]]
            [clojure.string :as str]
            [wine-cellar.common :refer [wset-lexicon]]
            [wine-cellar.views.components.form :refer [select-field]]))

(defn- category-dropdown
  "Individual category dropdown with free-form input support"
  [{:keys [label options all-values on-change]}]
  (r/with-let [dynamic-options (r/atom (set options))]
              (let [register-new-options (fn [values]
                                           ;; Add any new custom values to
                                           ;; the dynamic options set
                                           (doseq [item values]
                                             (when (not (str/blank? item))
                                               (swap! dynamic-options conj
                                                 item))))]
                [grid {:item true :xs 12 :sm 6}
                 [select-field
                  {:label label
                   :value (filter #(@dynamic-options %) (or all-values []))
                   :options (vec @dynamic-options)
                   :multiple true
                   :free-solo true
                   :required false
                   :on-change #(do (register-new-options %)
                                   ;; Update the parent state
                                   (on-change %))
                   :on-blur #(let [input-value (-> %
                                                   .-target
                                                   .-value)]
                               (println input-value)
                               ;; Register any new option that was typed in
                               ;; the input field
                               (register-new-options [input-value]))
                   :sx {"& .MuiAutocomplete-endAdornment .MuiSvgIcon-root"
                        {:color "text.primary"}}}]])))

(def ^:private category-definitions
  [{:section "Primary"
    :categories [{:label "Floral" :path [:primary :floral]}
                 {:label "Green Fruit" :path [:primary :green-fruit]}
                 {:label "Citrus Fruit" :path [:primary :citrus-fruit]}
                 {:label "Stone Fruit" :path [:primary :stone-fruit]}
                 {:label "Tropical Fruit" :path [:primary :tropical-fruit]}
                 {:label "Red Fruit" :path [:primary :red-fruit]}
                 {:label "Black Fruit" :path [:primary :black-fruit]}
                 {:label "Dried/Cooked Fruit"
                  :path [:primary :dried-cooked-fruit]}
                 {:label "Herbaceous" :path [:primary :herbaceous]}
                 {:label "Herbal" :path [:primary :herbal]}
                 {:label "Pungent Spice" :path [:primary :pungent-spice]}
                 {:label "Other Primary" :path [:primary :other]}]}
   {:section "Secondary"
    :categories [{:label "Yeast" :path [:secondary :yeast]}
                 {:label "Malolactic Fermentation" :path [:secondary :mlf]}
                 {:label "Oak" :path [:secondary :oak]}]}
   {:section "Tertiary"
    :categories
    [{:label "Deliberate Oxidation" :path [:tertiary :deliberate-oxidation]}
     {:label "Fruit Development" :path [:tertiary :fruit-development]}
     {:label "Bottle Age" :path [:tertiary :bottle-age]}]}])

(defn characteristics-section
  "Shared aroma/flavor characteristics selection with individual category dropdowns"
  [{:keys [value on-change section-title]}]
  (r/with-let
   [primary-expanded? (r/atom false) secondary-expanded? (r/atom false)
    tertiary-expanded? (r/atom false)]
   (let [expanded-atoms {"Primary" primary-expanded?
                         "Secondary" secondary-expanded?
                         "Tertiary" tertiary-expanded?}]
     [grid {:item true :xs 12}
      [typography {:variant "subtitle2" :gutterBottom true} section-title]
      (doall
       (for [{:keys [section categories]} category-definitions]
         (let [expanded? (get expanded-atoms section)]
           ^{:key section}
           [grid {:container true :spacing 1 :sx {:mb 3}}
            [grid {:item true :xs 12}
             [grid {:container true :alignItems "center" :spacing 1}
              [grid {:item true :xs true}
               [typography {:variant "body2" :sx {:fontWeight "bold" :mb 1}}
                section]]
              [grid {:item true :xs "auto"}
               [icon-button
                {:size "small"
                 :onClick #(swap! expanded? not)
                 :sx {:transform (if @expanded? "rotate(180deg)" "rotate(0deg)")
                      :transition "transform 0.2s ease-in-out"
                      :color "text.primary"}}
                [expand-more {:sx {:color "text.primary" :fontSize "1rem"}}]]]]]
            [collapse {:in @expanded?}
             [grid {:container true :spacing 1 :sx {:pt 1}}
              (for [{:keys [label path]} categories]
                ^{:key label}
                [category-dropdown
                 {:label label
                  :options (get-in wset-lexicon path)
                  :all-values (get-in value path)
                  :on-change #(on-change (assoc-in value path %))}])]]])))])))
