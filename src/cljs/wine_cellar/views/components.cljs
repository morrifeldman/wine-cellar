(ns wine-cellar.views.components
  (:require [clojure.string :as str]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.table-cell :refer [table-cell]]
            [reagent-mui.material.table-sort-label :refer [table-sort-label]]
            [reagent-mui.icons.arrow-drop-up :refer [arrow-drop-up]]
            [reagent-mui.icons.arrow-drop-down :refer [arrow-drop-down]]
            [wine-cellar.api :as api]))

;; Shared styles
(def form-field-style {:min-width "180px" :width "75%"})

;; Table components
(defn sortable-header [app-state label field]
  (let [sort-state (:sort @app-state)
        current-field (:field sort-state)
        current-direction (:direction sort-state)
        is-active (= field current-field)
        direction (if (= :asc current-direction) "asc" "desc")]
    [table-cell
     {:align "left"
      :sx {:font-weight "bold"}}
     [table-sort-label
      (cond-> {:active is-active
               :onClick #(swap! app-state update :sort
                                (fn [sort]
                                  (if (= field (:field sort))
                                    {:field field
                                     :direction (if (= :asc (:direction sort)) :desc :asc)}
                                    {:field field :direction :asc})))}
        is-active (assoc :direction direction))
      label]]))

;; Quantity control component
(defn quantity-control [app-state wine-id quantity]
  [box {:display "flex" :alignItems "center"}
   [box {:component "span"
         :sx {:fontSize "1rem" :mx 1 :minWidth "1.5rem" :textAlign "center"}}
    quantity]
   [box {:display "flex" :flexDirection "column" :ml 0.5}
    [button
     {:variant "text"
      :size "small"
      :sx {:minWidth 0 :p 0 :lineHeight 0.8}
      :onClick #(api/adjust-wine-quantity app-state wine-id 1)}
     [arrow-drop-up {:fontSize "small"}]]
    [button
     {:variant "text"
      :size "small"
      :sx {:minWidth 0 :p 0 :lineHeight 0.8}
      :disabled (= quantity 0)
      :onClick #(api/adjust-wine-quantity app-state wine-id -1)}
     [arrow-drop-down {:fontSize "small"}]]]])

;; Utility functions
(defn format-label
  "Convert a keyword like :producer or :wine-type to a human-readable label"
  [k]
  (-> (name k)
      (str/replace #"-|_" " ")
      (str/capitalize)))

(defn toggle-button
  "A button that toggles a boolean value in app-state"
  [{:keys [app-state path show-text hide-text color variant on-click]
    :or {color "primary"
         variant "contained"
         show-text "Show"
         hide-text "Hide"}}]
  [box {:sx {:mb 2}}
   [button
    {:variant variant
     :color color
     :onClick (or on-click #(swap! app-state update-in path not))}
    (if (get-in @app-state path)
      hide-text
      show-text)]])

