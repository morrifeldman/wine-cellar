(ns wine-cellar.views.components.wset-conclusions
  (:require
    [reagent.core :as r]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.collapse :refer [collapse]]
    [reagent-mui.material.icon-button :refer [icon-button]]
    [reagent-mui.icons.expand-more :refer [expand-more]]
    [wine-cellar.common :refer [wset-lexicon]]
    [wine-cellar.views.components.wset-shared :refer [radio-group-field]]
    [wine-cellar.views.components.form :refer [uncontrolled-text-area-field]]))

(defn wset-conclusions-section
  "WSET Level 3 Conclusions Assessment Component"
  [{:keys [conclusions on-change final-comments-ref]}]
  (r/with-let
   [expanded? (r/atom false)]
   (let [update-field (fn [field value]
                        (on-change (assoc conclusions field value)))]
     [grid {:container true :spacing 2}
      ;; Section Header with collapse toggle
      [grid {:item true :xs 12}
       [grid {:container true :alignItems "center" :spacing 1}
        [grid {:item true :xs true}
         [typography {:variant "h6" :gutterBottom false} "CONCLUSIONS"]]
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
         ;; Quality Level
         [radio-group-field
          {:label "Quality Level"
           :value (:quality-level conclusions)
           :options (get-in wset-lexicon [:enums :quality-level])
           :on-change #(update-field :quality-level %)}]
         ;; Readiness for Drinking
         [radio-group-field
          {:label "Readiness for Drinking"
           :value (:readiness conclusions)
           :options (get-in wset-lexicon [:enums :readiness])
           :on-change #(update-field :readiness %)}]
         ;; Final Comments
         [grid {:item true :xs 12}
          [uncontrolled-text-area-field
           {:label "Final Comments"
            :initial-value (:final_comments conclusions)
            :rows 4
            :helper-text
            "Overall assessment, drinking window, food pairing suggestions, etc."
            :reset-key (str "conclusions-comments-"
                            (hash (:final_comments conclusions)))
            :input-ref final-comments-ref}]]]]]])))