(ns wine-cellar.views.bar.inventory
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.checkbox :refer [checkbox]]
            [reagent-mui.material.form-control-label :refer
             [form-control-label]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.icons.add :refer [add]]
            [wine-cellar.api :as api]))

(def category-labels
  {"juice" "Juices"
   "soda" "Sodas"
   "syrup" "Syrups"
   "bitters" "Bitters"
   "garnish" "Garnishes"
   "other" "Other"})

(def category-order ["juice" "soda" "syrup" "bitters" "garnish" "other"])

(defn- add-item-form
  [app-state show-form? form-data]
  (let [name-val (:name @form-data "")
        cat-val (:category @form-data "other")]
    [box
     {:sx
      {:mt 1.5 :display "flex" :gap 1 :alignItems "flex-end" :flexWrap "wrap"}}
     [mui-text-field/text-field
      {:label "Item name"
       :value name-val
       :on-change #(swap! form-data assoc
                     :name
                     (-> %
                         .-target
                         .-value))
       :size "small"
       :sx {:flex "2 1 150px"}}]
     [form-control {:size "small" :sx {:flex "1 1 100px"}}
      [input-label "Category"]
      [select
       {:value cat-val
        :label "Category"
        :on-change #(swap! form-data assoc
                      :category
                      (-> %
                          .-target
                          .-value))}
       (for [[cat label] category-labels]
         ^{:key cat} [menu-item {:value cat} label])]]
     [button
      {:variant "contained"
       :size "small"
       :disabled (str/blank? name-val)
       :on-click (fn []
                   (api/create-bar-inventory-item app-state
                                                  {:name name-val
                                                   :category cat-val})
                   (reset! form-data {})
                   (reset! show-form? false))} "Add"]
     [button
      {:variant "outlined"
       :size "small"
       :on-click #(do (reset! form-data {}) (reset! show-form? false))}
      "Cancel"]]))

(defn- category-section
  [app-state category items]
  (let [label (get category-labels category category)
        checked-count (count (filter :have_it items))]
    [box {:sx {:mb 2}}
     [typography
      {:variant "subtitle2"
       :sx {:color "text.secondary"
            :fontWeight 600
            :letterSpacing "0.08em"
            :mb 0.5
            :textTransform "uppercase"
            :fontSize "0.72rem"}}
      (str label " (" checked-count "/" (count items) ")")]
     [box {:sx {:display "flex" :flexWrap "wrap" :gap 0}}
      (for [item items]
        ^{:key (:id item)}
        [form-control-label
         {:label (:name item)
          :sx {:mr 0
               :minWidth "160px"
               "& .MuiFormControlLabel-label"
               {:fontSize "0.875rem"
                :color (if (:have_it item) "text.primary" "text.secondary")}}
          :control (r/as-element [checkbox
                                  {:checked (boolean (:have_it item))
                                   :size "small"
                                   :on-change #(api/toggle-bar-inventory-item
                                                app-state
                                                (:id item)
                                                (-> %
                                                    .-target
                                                    .-checked))}])}])]]))

(defn inventory-tab
  [app-state]
  (let [show-form? (r/atom false)
        form-data (r/atom {})]
    (fn []
      (let [items (get-in @app-state [:bar :inventory-items])
            grouped (group-by :category items)]
        [box
         [box
          {:sx {:display "flex"
                :justifyContent "space-between"
                :alignItems "center"
                :mb 2}} [typography {:variant "h6"} "Mixers & Garnishes"]
          [button
           {:variant "outlined"
            :color "primary"
            :size "small"
            :start-icon (r/as-element [add])
            :on-click #(reset! show-form? true)} "Custom"]]
         (when @show-form? [add-item-form app-state show-form? form-data])
         (for [cat category-order
               :let [cat-items (get grouped cat)]
               :when (seq cat-items)]
           ^{:key cat} [category-section app-state cat cat-items])
         ;; Any categories not in the predefined order
         (for [[cat cat-items] grouped
               :when (not (some #{cat} category-order))]
           ^{:key cat} [category-section app-state cat cat-items])]))))
