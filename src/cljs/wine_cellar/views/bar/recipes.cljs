(ns wine-cellar.views.bar.recipes
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.edit :refer [edit]]
            [wine-cellar.api :as api]))

(defn- text-field
  [label value on-change & {:keys [type multiline rows]}]
  [mui-text-field/text-field
   {:label label
    :value (or value "")
    :on-change #(on-change (-> %
                               .-target
                               .-value))
    :type (or type "text")
    :multiline (boolean multiline)
    :rows rows
    :size "small"
    :full-width true
    :sx {:mb 1.5}}])

(defn- ingredient-row
  [ingredient idx on-update on-remove]
  [box {:sx {:display "flex" :gap 1 :mb 1 :alignItems "center"} :key idx}
   [mui-text-field/text-field
    {:label "Ingredient"
     :value (or (:name ingredient) "")
     :on-change #(on-update idx
                            (assoc ingredient
                                   :name
                                   (-> %
                                       .-target
                                       .-value)))
     :size "small"
     :sx {:flex 3}}]
   [mui-text-field/text-field
    {:label "Amount"
     :value (or (:amount ingredient) "")
     :on-change #(on-update idx
                            (assoc ingredient
                                   :amount
                                   (-> %
                                       .-target
                                       .-value)))
     :size "small"
     :sx {:flex 2}}]
   [mui-text-field/text-field
    {:label "Unit"
     :value (or (:unit ingredient) "")
     :on-change #(on-update idx
                            (assoc ingredient
                                   :unit
                                   (-> %
                                       .-target
                                       .-value)))
     :size "small"
     :sx {:flex 1.5}}]
   [icon-button {:size "small" :color "error" :on-click #(on-remove idx)}
    [delete {:fontSize "small"}]]])

(defn recipe-form
  [app-state]
  (let [bar (get @app-state :bar)
        editing-id (:editing-recipe-id bar)
        recipe (if editing-id
                 (first (filter #(= (:id %) editing-id) (:recipes bar)))
                 (:new-recipe bar))
        update-field!
        (fn [field val]
          (if editing-id
            (swap! app-state update-in
              [:bar :recipes]
              (fn [recipes]
                (mapv #(if (= (:id %) editing-id) (assoc % field val) %)
                      recipes)))
            (swap! app-state assoc-in [:bar :new-recipe field] val)))
        update-ingredient!
        (fn [idx ingredient]
          (update-field! :ingredients
                         (assoc (vec (:ingredients recipe)) idx ingredient)))
        remove-ingredient!
        (fn [idx]
          (update-field! :ingredients
                         (vec (concat (subvec (vec (:ingredients recipe)) 0 idx)
                                      (subvec (vec (:ingredients recipe))
                                              (inc idx))))))
        add-ingredient! #(update-field! :ingredients
                                        (conj (vec (:ingredients recipe)) {}))
        cancel!
        (fn []
          (if editing-id
            (swap! app-state assoc-in [:bar :editing-recipe-id] nil)
            (do
              (swap! app-state assoc-in [:bar :show-recipe-form?] false)
              (swap! app-state assoc-in [:bar :new-recipe] {:ingredients []}))))
        submit! (fn [e]
                  (.preventDefault e)
                  (let [tags-raw (get recipe :tags-input "")
                        tags (when (seq tags-raw)
                               (mapv str/trim (str/split tags-raw #",")))
                        payload (-> recipe
                                    (dissoc :tags-input)
                                    (assoc :tags tags))]
                    (if editing-id
                      (api/update-cocktail-recipe app-state editing-id payload)
                      (api/create-cocktail-recipe app-state payload))))]
    [paper
     {:elevation 2 :sx {:p 2 :mb 2 :borderLeft "4px solid rgba(114,47,55,0.5)"}}
     [:form {:on-submit submit!}
      [typography {:variant "h6" :sx {:mb 2 :color "primary.main"}}
       (if editing-id "Edit Recipe" "Add Recipe")]
      [box {:sx {:display "flex" :gap 1.5 :flexWrap "wrap"}}
       [box {:sx {:flex "2 1 200px"}}
        [text-field "Recipe Name" (:name recipe) #(update-field! :name %)]]
       [box {:sx {:flex "1 1 200px"}}
        [text-field "Source" (:source recipe) #(update-field! :source %)]]
       [box {:sx {:flex "1 1 200px"}}
        [text-field "Tags (comma-separated)"
         (or (:tags-input recipe)
             (when (seq (:tags recipe)) (str/join ", " (:tags recipe)))
             "") #(update-field! :tags-input %)]]
       [box {:sx {:flex "3 1 400px"}}
        [text-field "Description" (:description recipe)
         #(update-field! :description %) :multiline true :rows 2]]]
      [typography {:variant "subtitle2" :sx {:mb 1 :mt 0.5 :fontWeight 600}}
       "Ingredients"]
      (map-indexed (fn [idx ingredient]
                     ^{:key idx}
                     [ingredient-row ingredient idx update-ingredient!
                      remove-ingredient!])
                   (:ingredients recipe))
      [button
       {:variant "outlined"
        :size "small"
        :start-icon (r/as-element [add])
        :on-click add-ingredient!
        :sx {:mb 2}} "Add Ingredient"]
      [text-field "Instructions" (:instructions recipe)
       #(update-field! :instructions %) :multiline true :rows 4]
      [box {:sx {:display "flex" :gap 1 :justifyContent "flex-end"}}
       [button {:variant "outlined" :on-click cancel!} "Cancel"]
       [button {:type "submit" :variant "contained" :color "primary"}
        (if editing-id "Save" "Add")]]]]))

(defn- recipe-card
  [app-state recipe]
  (let [tags (:tags recipe)
        n-ingredients (count (:ingredients recipe))]
    [paper {:elevation 1 :sx {:p 1.5 :mb 1}}
     [box
      {:sx {:display "flex"
            :alignItems "flex-start"
            :justifyContent "space-between"
            :gap 1}}
      [box {:sx {:flex 1 :minWidth 0}}
       [typography {:variant "body1" :sx {:fontWeight 600}} (:name recipe)]
       [box
        {:sx {:display "flex"
              :alignItems "center"
              :gap 0.5
              :flexWrap "wrap"
              :color "text.secondary"
              :fontSize "0.8rem"}}
        [typography
         {:variant "body2"
          :component "span"
          :sx {:color "text.secondary" :fontSize "0.8rem"}}
         (str n-ingredients " ingredient" (when (not= n-ingredients 1) "s"))]
        (when (seq tags)
          [:<>
           [typography
            {:variant "body2"
             :component "span"
             :sx {:color "text.secondary" :fontSize "0.8rem"}} "·"]
           (for [tag tags]
             ^{:key tag}
             [chip
              {:label tag
               :size "small"
               :sx {:height 18 :fontSize "0.7rem"}}])])]
       (when (:description recipe)
         [typography
          {:variant "body2"
           :sx {:color "text.secondary" :fontSize "0.8rem" :mt 0.5}}
          (:description recipe)])]
      [box {:sx {:display "flex" :gap 0.5 :flexShrink 0}}
       [icon-button
        {:size "small"
         :on-click
         #(swap! app-state assoc-in [:bar :editing-recipe-id] (:id recipe))}
        [edit {:fontSize "small"}]]
       [icon-button
        {:size "small"
         :color "error"
         :on-click #(when (js/confirm (str "Delete \"" (:name recipe) "\"?"))
                      (api/delete-cocktail-recipe app-state (:id recipe)))}
        [delete {:fontSize "small"}]]]]]))

(defn save-recipe-dialog
  [app-state]
  (let [save-state (get-in @app-state [:chat :save-recipe])
        open? (boolean (:open? save-state))
        recipe (:recipe save-state)
        close! #(swap! app-state assoc-in [:chat :save-recipe] {})
        save! (fn []
                (api/create-cocktail-recipe app-state
                                            (assoc recipe :source "AI Chat"))
                (close!)
                (swap! app-state assoc-in [:bar :active-tab] :recipes))]
    [dialog {:open open? :on-close close! :max-width "sm" :full-width true}
     [dialog-title "Save Recipe"]
     [dialog-content {:sx {:pt "12px !important"}}
      [text-field "Recipe Name" (or (:name recipe) "")
       #(swap! app-state assoc-in [:chat :save-recipe :recipe :name] %)]
      (when (seq (:ingredients recipe))
        [:<>
         [typography {:variant "subtitle2" :sx {:mt 1.5 :mb 0.5}} "Ingredients"]
         [typography {:variant "body2" :sx {:color "text.secondary"}}
          (str/join " · "
                    (map (fn [{:keys [amount unit name]}]
                           (str/join " " (filter seq [amount unit name])))
                         (:ingredients recipe)))]])]
     [dialog-actions [button {:on-click close!} "Cancel"]
      [button {:variant "contained" :on-click save!} "Save to Recipes"]]]))

(defn recipes-tab
  [app-state]
  (let [bar @(r/cursor app-state [:bar])
        recipes (:recipes bar)
        show-form? (:show-recipe-form? bar)
        editing-id (:editing-recipe-id bar)]
    [box
     [box {:sx {:display "flex" :justifyContent "space-between" :mb 2}}
      [typography {:variant "h6"} (str "Recipes (" (count recipes) ")")]
      (when-not (or show-form? editing-id)
        [button
         {:variant "outlined"
          :color "primary"
          :start-icon (r/as-element [add])
          :on-click #(swap! app-state assoc-in [:bar :show-recipe-form?] true)}
         "Add Recipe"])]
     (when (or show-form? editing-id) [recipe-form app-state])
     (if (empty? recipes)
       [typography {:sx {:color "text.secondary" :textAlign "center" :py 4}}
        "No recipes yet. Save your first cocktail!"]
       (for [recipe recipes]
         ^{:key (:id recipe)} [recipe-card app-state recipe]))]))
