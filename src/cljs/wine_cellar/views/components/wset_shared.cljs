(ns wine-cellar.views.components.wset-shared
  (:require
    [reagent.core :as r]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.chip :refer [chip]]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.collapse :refer [collapse]]
    [reagent-mui.material.icon-button :refer [icon-button]]
    [reagent-mui.material.switch :refer [switch]]
    [reagent-mui.material.form-control-label :refer [form-control-label]]
    [reagent-mui.material.radio :refer [radio]]
    [reagent-mui.material.radio-group :refer [radio-group]]
    [reagent-mui.icons.expand-more :refer [expand-more]]
    [clojure.string :as str]
    [wine-cellar.common :refer [wset-lexicon]]
    [wine-cellar.views.components.form :refer [select-field]]
    [wine-cellar.views.components.wine-color :refer [wine-color-display]]))

(defn wset-label
  "The WSET vocabulary is stored in capitals, as the spec prints it; show it
   in sentence case like the rest of the app."
  [value]
  (if (and (string? value) (seq value))
    (let [lower (str/lower-case value)]
      (str (str/upper-case (subs lower 0 1)) (subs lower 1)))
    value))

(defn- filled?
  "Whether a WSET value holds anything to show. Saved notes keep every section
   with blank strings in it, which would otherwise count as content."
  [v]
  (cond (string? v) (not (str/blank? v))
        (map? v) (boolean (some filled? (vals v)))
        (coll? v) (boolean (some filled? v))
        :else (some? v)))

(defn- filled-section
  "The named section with its blank fields dropped, or nil if none are left."
  [wset-data k]
  (not-empty (into {} (filter (comp filled? val)) (get wset-data k))))

(defn radio-group-field
  "Radio group for one WSET enum."
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
        :label (wset-label option)}])]])

(defn normalize-characteristics
  "Flatten and clean a WSET characteristics section (primary/secondary/tertiary)."
  [section]
  (->> (or section {})
       vals
       flatten
       (map #(if (string? %)
               (let [trimmed (str/trim %)]
                 (when (not (str/blank? trimmed)) trimmed))
               %))
       (remove nil?)))

(defn- sensory-characteristics-group
  [items label]
  (when (seq items)
    [box {:sx {:mb 1}}
     [typography {:variant "body2" :sx {:fontWeight "bold" :mb 0.5}}
      (str label ":")]
     [grid {:container true :spacing 0.5}
      (for [value items]
        ^{:key (str label "-" value)}
        [grid {:item true}
         [chip
          {:label (wset-label value) :size "small" :variant "outlined"}]])]]))

(defn sensory-characteristics-display
  [{:keys [data primary-label secondary-label tertiary-label]}]
  (let [primary-items (normalize-characteristics (:primary data))
        secondary-items (normalize-characteristics (:secondary data))
        tertiary-items (normalize-characteristics (:tertiary data))]
    (when (some seq [primary-items secondary-items tertiary-items])
      [box {:sx {:mt 1}}
       [sensory-characteristics-group primary-items primary-label]
       [sensory-characteristics-group secondary-items secondary-label]
       [sensory-characteristics-group tertiary-items tertiary-label]])))

(defn wset-display
  "Simple display of WSET structured data"
  [wset-data]
  (when (filled? (select-keys wset-data
                              [:appearance :nose :palate :conclusions]))
    [box
     ;; Appearance section
     (when-let [appearance (filled-section wset-data :appearance)]
       [box {:sx {:mt 1.5}}
        [typography {:variant "subtitle2" :sx {:fontWeight "bold" :mb 1}}
         "Appearance"]
        [grid {:container true :spacing 1}
         (when (:clarity appearance)
           [grid {:item true}
            [chip {:label (wset-label (:clarity appearance)) :size "small"}]])
         (when (and (:colour appearance) (:intensity appearance))
           [grid {:item true}
            [wine-color-display
             {:selected-color (:colour appearance)
              :selected-intensity (:intensity appearance)
              :size :small}]])]
        (when (:other_observations appearance)
          [typography {:variant "body2" :sx {:mt 1 :fontStyle "italic"}}
           (:other_observations appearance)])])
     ;; Nose section
     (when-let [nose (filled-section wset-data :nose)]
       [box {:sx {:mt 1.5}}
        [typography {:variant "subtitle2" :sx {:fontWeight "bold" :mb 1}}
         "Nose"]
        [grid {:container true :spacing 1}
         (when (:condition nose)
           [grid {:item true}
            [chip {:label (wset-label (:condition nose)) :size "small"}]])
         (when (:intensity nose)
           [grid {:item true}
            [chip
             {:label (str "Intensity: " (wset-label (:intensity nose)))
              :size "small"}]])
         (when (:development nose)
           [grid {:item true}
            [chip {:label (wset-label (:development nose)) :size "small"}]])]
        (when-let [aroma-data (:aroma-characteristics nose)]
          [sensory-characteristics-display
           {:data aroma-data
            :primary-label "Primary Aromas"
            :secondary-label "Secondary Aromas"
            :tertiary-label "Tertiary Aromas"}])
        (when (:other_observations nose)
          [typography {:variant "body2" :sx {:mt 1 :fontStyle "italic"}}
           (:other_observations nose)])])
     ;; Palate section
     (when-let [palate (filled-section wset-data :palate)]
       [box {:sx {:mt 1.5}}
        [typography {:variant "subtitle2" :sx {:fontWeight "bold" :mb 1}}
         "Palate"]
        [grid {:container true :spacing 1}
         (when (:sweetness palate)
           [grid {:item true}
            [chip
             {:label (str "Sweetness: " (wset-label (:sweetness palate)))
              :size "small"}]])
         (when (:acidity palate)
           [grid {:item true}
            [chip
             {:label (str "Acidity: " (wset-label (:acidity palate)))
              :size "small"}]])
         (when (:tannin palate)
           [grid {:item true}
            [chip
             {:label (str "Tannin: " (wset-label (:tannin palate)))
              :size "small"}]])
         (when (:alcohol palate)
           [grid {:item true}
            [chip
             {:label (str "Alcohol: " (wset-label (:alcohol palate)))
              :size "small"}]])
         (when (:body palate)
           [grid {:item true}
            [chip
             {:label (str "Body: " (wset-label (:body palate)))
              :size "small"}]])
         (when (:flavor-intensity palate)
           [grid {:item true}
            [chip
             {:label (str "Flavor intensity: "
                          (wset-label (:flavor-intensity palate)))
              :size "small"}]])
         (when (:finish palate)
           [grid {:item true}
            [chip
             {:label (str "Finish: " (wset-label (:finish palate)))
              :size "small"}]])]
        (when-let [flavor-data (:flavor-characteristics palate)]
          [sensory-characteristics-display
           {:data flavor-data
            :primary-label "Primary Flavors"
            :secondary-label "Secondary Flavors"
            :tertiary-label "Tertiary Flavors"}])
        (when (:other_observations palate)
          [typography {:variant "body2" :sx {:mt 1 :fontStyle "italic"}}
           (:other_observations palate)])])
     ;; Conclusions section
     (when-let [conclusions (filled-section wset-data :conclusions)]
       [box {:sx {:mt 1.5}}
        [typography {:variant "subtitle2" :sx {:fontWeight "bold" :mb 1}}
         "Conclusions"]
        [grid {:container true :spacing 1}
         (when (:quality-level conclusions)
           [grid {:item true}
            [chip
             {:label (str "Quality: " (wset-label (:quality-level conclusions)))
              :size "small"}]])
         (when (:readiness conclusions)
           [grid {:item true}
            [chip
             {:label (str "Readiness: " (wset-label (:readiness conclusions)))
              :size "small"}]])]
        (when (:final_comments conclusions)
          [typography {:variant "body2" :sx {:mt 1 :fontStyle "italic"}}
           (:final_comments conclusions)])])]))

(defn- category-dropdown
  "Individual category dropdown with free-form input support"
  [{:keys [label options all-values on-change suppress-keyboard?]}]
  (r/with-let [dynamic-options (r/atom (set options))]
              (let [register-new-options (fn [values]
                                           ;; Add any new custom values to
                                           ;; the dynamic options set
                                           (doseq [item values]
                                             (when (not (str/blank? item))
                                               (swap! dynamic-options conj
                                                 item))))]
                (register-new-options all-values)
                [grid {:item true :xs 12 :sm 6}
                 [select-field
                  {:label label
                   :value (filter #(@dynamic-options %) (or all-values []))
                   :options (vec @dynamic-options)
                   :get-option-label wset-label
                   :multiple true
                   :free-solo true
                   :required false
                   :suppress-keyboard? suppress-keyboard?
                   :on-change #(do (register-new-options %) (on-change %))
                   :on-blur #(let [input-value (-> %
                                                   .-target
                                                   .-value)]
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
    tertiary-expanded? (r/atom false) keyboard? (r/atom false)]
   (let [expanded-atoms {"Primary" primary-expanded?
                         "Secondary" secondary-expanded?
                         "Tertiary" tertiary-expanded?}]
     [grid {:item true :xs 12}
      [box
       {:sx
        {:display "flex" :alignItems "center" :justifyContent "space-between"}}
       [typography {:variant "subtitle2" :gutterBottom true} section-title]
       [form-control-label
        {:sx {:m 0}
         :labelPlacement "start"
         :control (r/as-element [switch
                                 {:size "small"
                                  :checked @keyboard?
                                  :onChange #(swap! keyboard? not)}])
         :label (r/as-element
                 [typography {:variant "caption" :sx {:color "text.secondary"}}
                  "Type custom"])}]]
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
                  :suppress-keyboard? (not @keyboard?)
                  :on-change #(on-change (assoc-in value path %))}])]]])))])))
