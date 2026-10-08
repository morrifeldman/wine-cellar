(ns wine-cellar.views.components.banner
  (:require [reagent-mui.icons.close :refer [close]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]))

(defn dismissable-banner
  "A message the user closes; severity is \"error\", \"success\" etc."
  [{:keys [text severity on-dismiss aria-label sx]}]
  (let [bg (str severity ".light")
        fg (str severity ".dark")]
    [paper
     {:elevation 3
      :sx (merge {:p 2 :mb 3 :bgcolor bg :color fg :position "relative"} sx)}
     [box {:sx {:display "flex" :alignItems "flex-start"}}
      [typography {:variant "body1" :sx {:flex 1 :pr 2}} text]
      [icon-button
       {:aria-label aria-label
        :size "small"
        :onClick on-dismiss
        :sx {:color fg}} [close {:fontSize "small"}]]]]))
