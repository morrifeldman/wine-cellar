(ns wine-cellar.views.bar.inventory
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.checkbox :refer [checkbox]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.check :refer [check]]
            [reagent-mui.icons.close :refer [close]]
            [reagent-mui.icons.filter-vintage :refer [filter-vintage]]
            [reagent-mui.icons.local-drink :refer [local-drink]]
            [reagent-mui.icons.sports-bar :refer [sports-bar]]
            [reagent-mui.icons.opacity :refer [opacity]]
            [reagent-mui.icons.science :refer [science]]
            [reagent-mui.icons.local-florist :refer [local-florist]]
            [reagent-mui.icons.more-horiz :refer [more-horiz]]
            [wine-cellar.api :as api]))

(def category-labels
  {"fruit" "Fruit"
   "juice" "Juices"
   "soda" "Sodas"
   "syrup" "Syrups"
   "bitters" "Bitters"
   "garnish" "Garnishes"
   "other" "Other"})

(def category-order
  ["fruit" "juice" "soda" "syrup" "bitters" "garnish" "other"])

(def category-meta
  {"fruit" {:icon filter-vintage :color "rgba(255,152,0,0.7)"}
   "juice" {:icon local-drink :color "rgba(255,213,79,0.7)"}
   "soda" {:icon sports-bar :color "rgba(100,181,246,0.7)"}
   "syrup" {:icon opacity :color "rgba(240,98,146,0.7)"}
   "bitters" {:icon science :color "rgba(128,203,196,0.7)"}
   "garnish" {:icon local-florist :color "rgba(139,195,74,0.7)"}
   "other" {:icon more-horiz :color "rgba(144,164,174,0.7)"}})

(defn- section-header
  [icon-component label border-color]
  [box
   {:sx {:display "flex"
         :alignItems "center"
         :mb 1
         :pb 1
         :borderBottom "1px solid rgba(255,255,255,0.06)"}}
   [box {:sx {:color border-color :display "flex" :mr 1 :opacity 0.85}}
    [icon-component {:fontSize "small"}]]
   [typography
    {:variant "overline"
     :sx {:fontWeight 700
          :letterSpacing "0.1em"
          :color "text.secondary"
          :lineHeight 1}} label]])

(defn- add-item-form
  [app-state form-data]
  (let [name-val (:name @form-data "")
        cat-val (:category @form-data "other")
        close-form!
        #(swap! app-state assoc-in [:bar :show-inventory-form?] false)]
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
                   (api/create-bar-inventory-item
                    app-state
                    {:name (str/trim name-val) :category cat-val :have_it true})
                   (reset! form-data {})
                   (close-form!))} "Add"]
     [button
      {:variant "outlined"
       :size "small"
       :on-click #(do (reset! form-data {}) (close-form!))} "Cancel"]]))

(defn- inventory-item
  [_app-state _item _editing-id]
  (let [edit-name (r/atom nil)]
    (fn [app-state item editing-id]
      (if (= (:id item) @editing-id)
        [box {:sx {:display "flex" :alignItems "center" :gap 0.5 :mb 0.5}}
         [checkbox
          {:checked (boolean (:have_it item)) :size "small" :disabled true}]
         [mui-text-field/text-field
          {:value (or @edit-name (:name item))
           :on-change #(reset! edit-name (-> %
                                             .-target
                                             .-value))
           :size "small"
           :auto-focus true
           :sx {:flex 1}
           :on-key-down (fn [e]
                          (when (= (.-key e) "Enter")
                            (let [n (str/trim (or @edit-name (:name item)))]
                              (when (seq n)
                                (api/update-bar-inventory-item app-state
                                                               (:id item)
                                                               {:name n})))
                            (reset! edit-name nil)
                            (reset! editing-id nil))
                          (when (= (.-key e) "Escape")
                            (reset! edit-name nil)
                            (reset! editing-id nil)))}]
         [icon-button
          {:size "small"
           :color "primary"
           :on-click (fn []
                       (let [n (str/trim (or @edit-name (:name item)))]
                         (when (seq n)
                           (api/update-bar-inventory-item app-state
                                                          (:id item)
                                                          {:name n})))
                       (reset! edit-name nil)
                       (reset! editing-id nil))} [check {:fontSize "small"}]]
         [icon-button
          {:size "small"
           :on-click #(do (reset! edit-name nil) (reset! editing-id nil))}
          [close {:fontSize "small"}]]]
        [box {:sx {:display "flex" :alignItems "center" :gap 0}}
         [checkbox
          {:checked (boolean (:have_it item))
           :size "small"
           :on-change #(api/toggle-bar-inventory-item app-state
                                                      (:id item)
                                                      (-> %
                                                          .-target
                                                          .-checked))}]
         [typography
          {:variant "body2"
           :on-click #(reset! editing-id (:id item))
           :sx {:cursor "pointer"
                :flex 1
                :fontSize "0.875rem"
                :color (if (:have_it item) "text.primary" "text.secondary")
                "&:hover" {:textDecoration "underline"}}} (:name item)]
         [icon-button
          {:size "small"
           :color "error"
           :on-click #(when (js/confirm (str "Delete \"" (:name item) "\"?"))
                        (api/delete-bar-inventory-item app-state (:id item)))
           :sx {:opacity 0.4 "&:hover" {:opacity 1}}}
          [delete {:sx {:fontSize "0.85rem"}}]]]))))

(defn- category-section
  [app-state category items editing-id]
  (let [label (get category-labels category category)
        {:keys [icon color]} (get category-meta
                                  category
                                  {:icon more-horiz
                                   :color "rgba(144,164,174,0.7)"})]
    [box {:sx {:mt 3 :borderLeft (str "3px solid " color) :pl 1.5 :pb 1}}
     [section-header icon label color]
     [box {:sx {:display "flex" :flexWrap "wrap" :gap 0}}
      (for [item items]
        ^{:key (:id item)} [inventory-item app-state item editing-id])]]))

(defn inventory-tab
  [app-state]
  (let [form-data (r/atom {})
        editing-id (r/atom nil)]
    (fn []
      (let [items (get-in @app-state [:bar :inventory-items])
            show-form? (get-in @app-state [:bar :show-inventory-form?])
            grouped (group-by :category items)]
        [box (when show-form? [add-item-form app-state form-data])
         (for [cat category-order
               :let [cat-items (get grouped cat)]
               :when (seq cat-items)]
           ^{:key cat} [category-section app-state cat cat-items editing-id])
         ;; Any categories not in the predefined order
         (for [[cat cat-items] grouped
               :when (not (some #{cat} category-order))]
           ^{:key cat}
           [category-section app-state cat cat-items editing-id])]))))
