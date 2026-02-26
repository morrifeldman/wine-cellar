(ns wine-cellar.views.bar.spirits
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.form-control :refer [form-control]]
            [reagent-mui.material.input-label :refer [input-label]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.edit :refer [edit]]
            [wine-cellar.api :as api]))

(def spirit-categories
  ["whiskey" "gin" "rum" "vodka" "tequila" "mezcal" "brandy" "liqueur" "other"])

(defn- text-field
  [label value on-change & {:keys [type required]}]
  [mui-text-field/text-field
   {:label label
    :value (or value "")
    :on-change #(on-change (-> %
                               .-target
                               .-value))
    :type (or type "text")
    :required (boolean required)
    :size "small"
    :full-width true
    :sx {:mb 1.5}}])

(defn spirit-form
  [app-state]
  (let [bar (get @app-state :bar)
        editing-id (:editing-spirit-id bar)
        spirit (if editing-id
                 (first (filter #(= (:id %) editing-id) (:spirits bar)))
                 (:new-spirit bar))
        update-field!
        (fn [field val]
          (if editing-id
            (swap! app-state update-in
              [:bar :spirits]
              (fn [spirits]
                (mapv #(if (= (:id %) editing-id) (assoc % field val) %)
                      spirits)))
            (swap! app-state assoc-in [:bar :new-spirit field] val)))
        cancel! (fn []
                  (if editing-id
                    (swap! app-state assoc-in [:bar :editing-spirit-id] nil)
                    (do
                      (swap! app-state assoc-in [:bar :show-spirit-form?] false)
                      (swap! app-state assoc-in [:bar :new-spirit] {}))))
        submit! (fn [e]
                  (.preventDefault e)
                  (if editing-id
                    (api/update-spirit app-state editing-id spirit)
                    (api/create-spirit app-state spirit)))]
    [paper
     {:elevation 2 :sx {:p 2 :mb 2 :borderLeft "4px solid rgba(114,47,55,0.5)"}}
     [:form {:on-submit submit!}
      [typography {:variant "h6" :sx {:mb 2 :color "primary.main"}}
       (if editing-id "Edit Spirit" "Add Spirit")]
      [box {:sx {:display "flex" :gap 1.5 :flexWrap "wrap"}}
       [box {:sx {:flex "1 1 200px"}}
        [text-field "Name" (:name spirit) #(update-field! :name %) :required
         true]]
       [box {:sx {:flex "1 1 160px"}}
        [form-control
         {:size "small" :full-width true :required true :sx {:mb 1.5}}
         [input-label "Category"]
         [select
          {:value (or (:category spirit) "")
           :label "Category"
           :on-change #(update-field! :category
                                      (-> %
                                          .-target
                                          .-value))}
          (for [cat spirit-categories]
            ^{:key cat}
            [menu-item {:value cat} (str (subs cat 0 1) (subs cat 1))])]]]
       [box {:sx {:flex "1 1 160px"}}
        [text-field "Distillery" (:distillery spirit)
         #(update-field! :distillery %)]]
       [box {:sx {:flex "1 1 120px"}}
        [text-field "Country" (:country spirit) #(update-field! :country %)]]
       [box {:sx {:flex "1 1 120px"}}
        [text-field "Region" (:region spirit) #(update-field! :region %)]]
       [box {:sx {:flex "1 1 100px"}}
        [text-field "Age" (:age_statement spirit)
         #(update-field! :age_statement %)]]
       [box {:sx {:flex "1 1 80px"}}
        [text-field "ABV %" (:abv spirit)
         #(update-field! :abv (js/parseFloat %)) :type "number"]]
       [box {:sx {:flex "1 1 70px"}}
        [text-field "Qty" (:quantity spirit)
         #(update-field! :quantity (js/parseInt %)) :type "number"]]
       [box {:sx {:flex "1 1 100px"}}
        [text-field "Price" (:price spirit)
         #(update-field! :price (js/parseFloat %)) :type "number"]]
       [box {:sx {:flex "1 1 140px"}}
        [mui-text-field/text-field
         {:label "Purchase Date"
          :value (or (:purchase_date spirit) "")
          :on-change #(update-field! :purchase_date
                                     (-> %
                                         .-target
                                         .-value))
          :type "date"
          :size "small"
          :full-width true
          :InputLabelProps {:shrink true}
          :sx {:mb 1.5}}]]
       [box {:sx {:flex "1 1 120px"}}
        [text-field "Location" (:location spirit) #(update-field! :location %)]]
       [box {:sx {:flex "2 1 300px"}}
        [mui-text-field/text-field
         {:label "Notes"
          :value (or (:notes spirit) "")
          :on-change #(update-field! :notes
                                     (-> %
                                         .-target
                                         .-value))
          :multiline true
          :rows 2
          :size "small"
          :full-width true
          :sx {:mb 1.5}}]]]
      [box {:sx {:display "flex" :gap 1 :justifyContent "flex-end"}}
       [button {:variant "outlined" :on-click cancel!} "Cancel"]
       [button {:type "submit" :variant "contained" :color "primary"}
        (if editing-id "Save" "Add")]]]]))

(defn- spirit-meta
  [spirit]
  (->> [(:category spirit) (:distillery spirit) (:country spirit)
        (when (:age_statement spirit) (:age_statement spirit))
        (when (:abv spirit) (str (:abv spirit) "% ABV"))
        (when (:quantity spirit) (str "qty: " (:quantity spirit)))]
       (filter identity)
       (str/join " · ")))

(defn spirit-card
  [app-state spirit]
  [paper
   {:elevation 1
    :sx {:p 1.5
         :mb 1
         :display "flex"
         :alignItems "center"
         :justifyContent "space-between"
         :gap 1}}
   [box {:sx {:flex 1 :minWidth 0}}
    [typography {:variant "body1" :sx {:fontWeight 600 :lineHeight 1.2}}
     (:name spirit)]
    [typography
     {:variant "body2"
      :sx {:color "text.secondary" :fontSize "0.8rem" :mt 0.25}}
     (spirit-meta spirit)]
    (when (and (:notes spirit) (not= (:notes spirit) ""))
      [typography
       {:variant "body2"
        :sx {:color "text.secondary"
             :fontSize "0.75rem"
             :mt 0.5
             :fontStyle "italic"
             :overflow "hidden"
             :textOverflow "ellipsis"
             :whiteSpace "nowrap"}} (:notes spirit)])]
   [box {:sx {:display "flex" :gap 0.5 :flexShrink 0}}
    [icon-button
     {:size "small"
      :on-click
      #(swap! app-state assoc-in [:bar :editing-spirit-id] (:id spirit))}
     [edit {:fontSize "small"}]]
    [icon-button
     {:size "small"
      :color "error"
      :on-click #(when (js/confirm (str "Delete " (:name spirit) "?"))
                   (api/delete-spirit app-state (:id spirit)))}
     [delete {:fontSize "small"}]]]])

(defn spirits-tab
  [app-state]
  (let [bar @(r/cursor app-state [:bar])
        spirits (:spirits bar)
        show-form? (:show-spirit-form? bar)
        editing-id (:editing-spirit-id bar)
        loading? (:loading? bar)]
    [box
     [box {:sx {:display "flex" :justifyContent "space-between" :mb 2}}
      [typography {:variant "h6"} (str "Spirits (" (count spirits) ")")]
      (when-not (or show-form? editing-id)
        [button
         {:variant "outlined"
          :color "primary"
          :start-icon (r/as-element [add])
          :on-click #(swap! app-state assoc-in [:bar :show-spirit-form?] true)}
         "Add Spirit"])]
     (when (or show-form? editing-id) [spirit-form app-state])
     (if loading?
       [box {:sx {:display "flex" :justifyContent "center" :py 4}}
        [circular-progress {:color "primary"}]]
       (if (empty? spirits)
         [typography {:sx {:color "text.secondary" :textAlign "center" :py 4}}
          "No spirits yet. Add your first bottle!"]
         (for [spirit spirits]
           ^{:key (:id spirit)} [spirit-card app-state spirit])))]))
