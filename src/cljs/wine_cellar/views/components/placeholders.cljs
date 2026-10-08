(ns wine-cellar.views.components.placeholders
  "What a list shows while it loads, or when there is nothing in it."
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.typography :refer [typography]]))

(defn loading-block
  "A centred spinner standing in for content that is on its way."
  ([] (loading-block nil))
  ([label]
   [box
    {:sx {:display "flex" :justifyContent "center" :alignItems "center" :py 4}}
    [circular-progress {:size 32}]
    (when label [typography {:sx {:ml 2 :color "text.secondary"}} label])]))

(defn empty-state
  "The line a list shows when it has nothing in it. Page lists centre it;
  inline? keeps it small and flush left inside a section or sidebar."
  ([message] (empty-state {} message))
  ([{:keys [inline?]} message]
   [typography
    {:variant "body2"
     :sx (if inline?
           {:color "text.secondary" :fontStyle "italic" :py 1}
           {:color "text.secondary" :textAlign "center" :py 4})} message]))
