(ns wine-cellar.views.components.summary
  "The pieces the bar lists and detail pages share: a clickable summary card
  and the Delete … Done action row."
  (:require [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [wine-cellar.theme :as theme]))

(defn summary-card
  "Title, a dot-separated meta line, then an optional muted blurb clipped to
  blurb-lines. dim? fades the card for things used up."
  [{:keys [title meta blurb blurb-lines italic-blurb? dim? on-click]
    :or {blurb-lines 2}}]
  [paper
   {:elevation (if dim? 0 1)
    :sx {:p 1.5
         :mb 1
         :cursor "pointer"
         :opacity (if dim? 0.45 1)
         "&:hover" {:bgcolor "action.hover"}}
    :on-click on-click} [typography {:sx theme/card-title} title]
   (when (seq meta)
     [typography {:variant "body2" :sx {:color "text.secondary" :mt 0.25}}
      meta])
   (when (seq blurb)
     [typography
      {:variant "body2"
       :sx {:color "text.secondary"
            :mt 0.5
            :fontStyle (when italic-blurb? "italic")
            :display "-webkit-box"
            :WebkitLineClamp blurb-lines
            :WebkitBoxOrient "vertical"
            :overflow "hidden"}} blurb])])

(defn detail-actions
  "Delete on the left, Done on the right, anything else just before Done."
  [{:keys [on-delete on-done]} & extras]
  (into [box {:sx {:display "flex" :gap 1 :alignItems "center" :mt 2}}
         [button {:variant "outlined" :color "error" :on-click on-delete}
          "Delete"] [box {:sx {:flex 1}}]]
        (concat extras
                [[button {:variant "contained" :on-click on-done} "Done"]])))
