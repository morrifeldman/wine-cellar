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
            [reagent-mui.material.rating :refer [rating]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.checkbox :refer [checkbox]]
            [reagent-mui.material.form-control-label :refer
             [form-control-label]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.local-bar :refer [local-bar]]
            [reagent-mui.icons.inventory :refer [inventory]]
            [reagent-mui.icons.menu-book :refer [menu-book]]
            [reagent-mui.icons.notes :refer [notes] :rename {notes notes-icon}]
            [wine-cellar.utils.filters :refer [normalize-text]]
            [wine-cellar.views.bar.matching :as matching]
            [wine-cellar.views.bar.spirits :refer
             [category-filter-bar subcategory-filter-bar category-colors
              category-labels spirit-categories]]
            [wine-cellar.views.components :refer
             [editable-text-field editable-autocomplete-field search-text-field
              detail-section]]
            [wine-cellar.api :as api]))

(defn- recipe-search-text
  [r]
  (->> (concat [(:name r) (:source r) (:description r) (:instructions r)
                (:notes r)]
               (:tags r)
               (map :name (:ingredients r)))
       (filter some?)
       (str/join " ")))

(defn- view-spirit-from-recipe!
  "Switch to the Spirits tab with `spirit-id` open, pushing a history entry so
   the browser Back button returns to the recipe that was open."
  [app-state recipe-id spirit-id]
  (.replaceState js/history
                 #js {:barNav #js {:activeTab "recipes"
                                   :viewingRecipeId recipe-id}}
                 ""
                 (.-pathname js/location))
  (.pushState js/history
              #js {:barNav #js {:activeTab "spirits"
                                :editingSpiritId spirit-id}}
              ""
              (.-pathname js/location))
  (swap! app-state #(-> %
                        (assoc-in [:bar :viewing-recipe-id] nil)
                        (assoc-in [:bar :active-tab] :spirits)
                        (assoc-in [:bar :editing-spirit-id] spirit-id))))

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
         #(update-field! :description %) :multiline true :rows 4]]]
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
      [text-field "Notes" (:notes recipe) #(update-field! :notes %) :multiline
       true :rows 3]
      [box {:sx {:display "flex" :gap 1 :justifyContent "flex-end"}}
       [button {:variant "outlined" :on-click cancel!} "Cancel"]
       [button {:type "submit" :variant "contained" :color "primary"}
        (if editing-id "Save" "Add")]]]]))

(defn- save-field!
  [app-state recipe field value]
  (api/update-cocktail-recipe app-state (:id recipe) {field value}))

(defn- recipe-rating
  [{:keys [value on-change read-only? size]}]
  [rating
   {:value (when value (/ value 2))
    :precision 0.5
    :max 5
    :size (or size "small")
    :read-only (boolean read-only?)
    :sx {"& .MuiRating-iconFilled" {:color "#FFD54F"}
         "& .MuiRating-iconHover" {:color "#FFE082"}
         "& .MuiRating-iconEmpty" {:color "text.secondary" :opacity 0.55}}
    :on-change (when-not read-only?
                 (fn [_ v] (on-change (when v (int (* v 2))))))}])

(defn- ingredient-mark
  "Glyph + color for an ingredient's makeability status."
  [status]
  (case status
    :missing {:glyph "✗" :color "rgba(255,167,38,0.95)"}
    :missing-garnish {:glyph "~" :color "text.secondary"}
    {:glyph "✓" :color "rgba(139,195,74,0.85)"}))

(defn- ingredients-list
  [recipe statuses]
  [box {:component "ul" :sx {:mt 0 :mb 0 :pl 2.5 :listStyleType "none"}}
   (map-indexed (fn [idx {:keys [amount unit name]}]
                  (let [{:keys [glyph color]} (ingredient-mark (get statuses
                                                                    name))]
                    ^{:key idx}
                    [:li
                     [typography {:variant "body2"}
                      [box
                       {:component "span"
                        :sx {:color color :fontWeight 600 :mr 0.75 :ml -2}}
                       glyph] (str/join " " (filter seq [amount unit name]))]]))
                (:ingredients recipe))])

(defn- tags-editor
  [_app-state _recipe _all-tags]
  (let [editing? (r/atom false)]
    (fn [app-state recipe all-tags]
      (let [tags (:tags recipe)]
        (if @editing?
          [box {:sx {:mt 1}}
           [editable-autocomplete-field
            {:value (when (seq tags) tags)
             :options all-tags
             :multiple true
             :free-solo true
             :force-edit-mode? true
             :on-save (fn [v]
                        (-> (save-field! app-state recipe :tags (vec (or v [])))
                            (.then #(reset! editing? false))))
             :on-cancel #(reset! editing? false)
             :empty-text ""}]]
          [box
           {:sx {:display "flex"
                 :gap 0.5
                 :flexWrap "wrap"
                 :mt 1.25
                 :alignItems "center"
                 :cursor "pointer"
                 :borderRadius 1
                 :px 0.5
                 :mx -0.5
                 :py 0.25
                 "&:hover" {:bgcolor "action.hover"}}
            :on-click #(reset! editing? true)}
           (if (seq tags)
             (for [tag tags]
               ^{:key tag}
               [chip
                {:label tag
                 :size "small"
                 :sx {:bgcolor "rgba(232,195,200,0.10)"
                      :color "rgba(232,195,200,0.95)"
                      :border "1px solid rgba(232,195,200,0.25)"
                      :height 22
                      :fontSize "0.72rem"
                      :letterSpacing "0.02em"
                      :cursor "pointer"}}])
             [typography
              {:variant "body2"
               :sx {:color "text.secondary"
                    :fontSize "0.78rem"
                    :fontStyle "italic"}} "+ Add tags"])])))))

(defn- bottle-chip
  "Clickable chip for a bottle in the recipe spirits section. `:dim?` dims it
   and prefixes a `~`; `:suffix` appends a ` · <suffix>` note (the actual
   subcategory for a substitute, or \"out of stock\" for an unavailable link)."
  [app-state recipe-id spirit {:keys [dim? suffix]}]
  (let [base (str/join " · "
                       (filter seq [(:distillery spirit) (:name spirit)]))]
    [chip
     {:label (str (when dim? "~ ") base (when (seq suffix) (str " · " suffix)))
      :size "small"
      :clickable true
      :on-click #(view-spirit-from-recipe! app-state recipe-id (:id spirit))
      :sx {:height 24
           :fontSize "0.72rem"
           :letterSpacing "0.02em"
           :opacity (if dim? 0.55 1)
           :bgcolor "rgba(232,195,200,0.08)"
           :color "rgba(232,195,200,0.95)"
           :border "1px solid rgba(232,195,200,0.22)"
           "@media (hover: hover)" {"&:hover" {:bgcolor
                                               "rgba(232,195,200,0.16)"}}}}]))

(defn- recipe-spirit-row
  [app-state recipe-id spirits {:keys [category subcategory] :as tag}]
  (let [{:keys [base text]}
        (get category-colors category {:base "160,160,160" :text "#c0c0c0"})
        {:keys [exact sub alts]} (matching/bottles-for-tag spirits tag)
        owned? (fn [b] (pos? (or (:quantity b) 1)))
        exact-in (filter owned? exact)
        exact-out (remove owned? exact)
        sub-chips (fn [bs]
                    (for [b bs]
                      ^{:key (str "s-" (:id b))}
                      [bottle-chip app-state recipe-id b {}]))
        alt-chips (fn [bs]
                    (for [b bs]
                      ^{:key (str "a-" (:id b))}
                      [bottle-chip app-state recipe-id b
                       {:dim? true :suffix (:subcategory b)}]))]
    [box
     {:sx
      {:display "flex" :flexWrap "wrap" :alignItems "center" :gap 0.75 :mb 1}}
     [chip
      {:label (str (get category-labels category category)
                   (when (seq subcategory) (str " · " subcategory)))
       :size "small"
       :sx {:height 24
            :fontSize "0.72rem"
            :letterSpacing "0.02em"
            :bgcolor (str "rgba(" base ",0.14)")
            :color text
            :border (str "1px solid rgba(" base ",0.35)")}}]
     (cond
       ;; In-stock precise link — show it alone.
       (seq exact-in) (sub-chips exact-in)
       ;; Out-of-stock precise link — show it dimmed, plus in-stock
       ;; substitutes.
       (seq exact-out) (concat (for [b exact-out]
                                 ^{:key (str "x-" (:id b))}
                                 [bottle-chip app-state recipe-id b
                                  {:dim? true :suffix "out of stock"}])
                               (sub-chips sub)
                               (alt-chips alts))
       (seq sub) (sub-chips sub)
       (seq alts) (alt-chips alts)
       :else [typography
              {:variant "body2"
               :sx {:color "text.secondary"
                    :fontSize "0.78rem"
                    :fontStyle "italic"}} "none on hand"])]))

(defn- recipe-spirits-section
  "Per-spirit-tag rows showing which owned bottles satisfy the recipe."
  [app-state recipe]
  (let [tags (->> (:spirit_tags recipe)
                  distinct
                  (sort-by (fn [{:keys [category subcategory]}]
                             [(.indexOf (to-array spirit-categories)
                                        (or category ""))
                              (or subcategory "")])))
        spirits (get-in @app-state [:bar :spirits])]
    (when (seq tags)
      [detail-section
       {:icon inventory :label "Your bottles" :color "rgba(232,195,200,0.7)"}
       (for [tag tags]
         ^{:key (str (:category tag) "/" (:subcategory tag))}
         [recipe-spirit-row app-state (:id recipe) spirits tag])])))

(defn- makeable-badge
  "Green 'Ready to make' / amber 'Missing: …' chip from a match report, with a
   soft garnish note appended when otherwise makeable."
  [{:keys [makeable? missing missing-garnishes]}]
  [box
   {:sx
    {:display "inline-flex" :alignItems "center" :flexWrap "wrap" :gap 0.75}}
   [chip
    {:label
     (if makeable? "Ready to make" (str "Missing: " (str/join ", " missing)))
     :size "small"
     :sx {:height 24
          :fontSize "0.72rem"
          :letterSpacing "0.02em"
          :bgcolor
          (if makeable? "rgba(139,195,74,0.16)" "rgba(255,167,38,0.14)")
          :color (if makeable? "rgba(174,213,129,0.95)" "rgba(255,183,77,0.95)")
          :border
          (str "1px solid "
               (if makeable? "rgba(139,195,74,0.4)" "rgba(255,167,38,0.4)"))}}]
   (when (and makeable? (seq missing-garnishes))
     [typography
      {:variant "body2"
       :sx {:color "text.secondary" :fontSize "0.72rem" :fontStyle "italic"}}
      (str "no garnish: " (str/join ", " missing-garnishes))])])

(defn- recipe-display
  [app-state recipe]
  (let [all-tags (->> (get-in @app-state [:bar :recipes])
                      (mapcat :tags)
                      (remove str/blank?)
                      distinct
                      sort
                      vec)
        bar (get @app-state :bar)
        report (matching/recipe-match-report recipe
                                             (:spirits bar)
                                             (:inventory-items bar))]
    [paper
     {:elevation 0
      :id (str "recipe-" (:id recipe))
      :sx {:p 2 :mb 2 :bgcolor "transparent"}}
     ;; Identity row
     [box {:sx {:mb 2}}
      [box
       {:sx {:cursor "pointer"
             :borderRadius 1
             :mx -0.5
             :px 0.5
             :py 0.25
             "&:hover" {:bgcolor "action.hover"}}
        :on-click #(swap! app-state assoc-in [:bar :viewing-recipe-id] nil)}
       [typography
        {:sx {:fontSize "1.35rem" :fontWeight 600 :color "primary.main"}}
        (:name recipe)]]
      [box {:sx {:mt 0.5}}
       [editable-text-field
        {:value (:source recipe)
         :on-save #(save-field! app-state recipe :source %)
         :empty-text "Add source"
         :inline? true
         :display-sx {:color "text.secondary" :fontSize "0.85rem"}}]]
      [box {:sx {:mt 0.75 :display "flex" :alignItems "center"}}
       [recipe-rating
        {:value (:rating recipe)
         :size "medium"
         :on-change #(save-field! app-state recipe :rating %)}]]
      (when (seq (:ingredients recipe))
        [box {:sx {:mt 1}} [makeable-badge report]])
      [tags-editor app-state recipe all-tags]]
     ;; Description (inline editable, no section header — sits as the lede)
     [box {:sx {:mb 1}}
      [editable-text-field
       {:value (:description recipe)
        :on-save #(save-field! app-state recipe :description %)
        :empty-text "Add a short description..."
        :text-field-props {:multiline true :rows 2}
        :display-sx {:fontStyle "italic" :color "text.secondary"}}]]
     ;; Ingredients section
     [detail-section
      {:icon local-bar :label "Ingredients" :color "rgba(139,195,74,0.7)"}
      (if (seq (:ingredients recipe))
        [ingredients-list recipe (:ingredient-status report)]
        [typography
         {:variant "body2" :sx {:color "text.secondary" :fontStyle "italic"}}
         "No ingredients yet — use Edit to add some."])]
     ;; Your bottles section (omitted when the recipe has no spirit tags)
     [recipe-spirits-section app-state recipe]
     ;; Instructions section
     [detail-section
      {:icon menu-book :label "Instructions" :color "rgba(100,181,246,0.7)"}
      [editable-text-field
       {:value (:instructions recipe)
        :on-save #(save-field! app-state recipe :instructions %)
        :empty-text "Add preparation steps..."
        :text-field-props {:multiline true :rows 3}}]]
     ;; Notes section
     [detail-section
      {:icon notes-icon :label "Notes" :color "rgba(255,213,79,0.7)"}
      [editable-text-field
       {:value (:notes recipe)
        :on-save #(save-field! app-state recipe :notes %)
        :empty-text "Add tasting notes, tweaks, occasions..."
        :text-field-props {:multiline true :rows 3}}]]
     ;; Actions
     [box {:sx {:display "flex" :gap 1 :alignItems "center" :mt 2}}
      [button
       {:variant "outlined"
        :color "error"
        :on-click #(when (js/confirm (str "Delete \"" (:name recipe) "\"?"))
                     (swap! app-state assoc-in [:bar :viewing-recipe-id] nil)
                     (api/delete-cocktail-recipe app-state (:id recipe)))}
       "Delete"] [box {:sx {:flex 1}}]
      (let [refreshing? (= (:id recipe)
                           (get-in @app-state [:bar :refreshing-recipe-id]))]
        [button
         {:variant "outlined"
          :disabled refreshing?
          :start-icon (when refreshing?
                        (r/as-element [circular-progress {:size 16}]))
          :on-click
          (fn []
            (swap! app-state assoc-in [:bar :refreshing-recipe-id] (:id recipe))
            (-> (api/refresh-recipe-links app-state (:id recipe))
                (.finally #(swap! app-state assoc-in
                             [:bar :refreshing-recipe-id]
                             nil))))}
         (if refreshing? "Refreshing…" "Refresh links")])
      [button
       {:variant "outlined"
        :on-click
        (fn []
          (swap! app-state assoc-in [:bar :viewing-recipe-id] nil)
          (swap! app-state assoc-in [:bar :editing-recipe-id] (:id recipe)))}
       "Edit"]
      [button
       {:variant "contained"
        :color "primary"
        :on-click #(swap! app-state assoc-in [:bar :viewing-recipe-id] nil)}
       "Done"]]]))

(defn- recipe-card
  [app-state recipe]
  (let [tags (:tags recipe)]
    [paper
     {:elevation 1
      :sx {:p 1.5 :mb 1 :cursor "pointer" "&:hover" {:bgcolor "action.hover"}}
      :on-click
      #(swap! app-state assoc-in [:bar :viewing-recipe-id] (:id recipe))}
     [box
      {:sx {:display "flex"
            :alignItems "flex-start"
            :justifyContent "space-between"
            :gap 1}}
      [box {:sx {:flex 1 :minWidth 0}}
       [box {:sx {:display "flex" :alignItems "center" :gap 1 :flexWrap "wrap"}}
        [typography {:variant "body1" :sx {:fontWeight 600}} (:name recipe)]
        (when (:rating recipe)
          [recipe-rating {:value (:rating recipe) :read-only? true}])]
       (when (seq tags)
         [box
          {:sx {:display "flex"
                :alignItems "center"
                :gap 0.5
                :flexWrap "wrap"
                :mt 0.25}}
          (for [tag tags]
            ^{:key tag}
            [chip
             {:label tag :size "small" :sx {:height 18 :fontSize "0.7rem"}}])])
       (when (:description recipe)
         [typography
          {:variant "body2"
           :sx {:color "text.secondary" :fontSize "0.8rem" :mt 0.5}}
          (:description recipe)])]]]))

(defn save-recipe-dialog
  [_app-state]
  (let [selected (r/atom nil)]
    (fn [app-state]
      (let [save-state (get-in @app-state [:chat :save-recipe])
            open? (boolean (:open? save-state))
            recipes (:recipes save-state)
            multi? (> (count recipes) 1)
            close! (fn []
                     (reset! selected nil)
                     (swap! app-state assoc-in [:chat :save-recipe] {}))
            _ (when (and open? (nil? @selected) (seq recipes))
                (reset! selected (set (range (count recipes)))))
            sel @selected
            save! (fn []
                    (doseq [idx (sort sel)]
                      (let [recipe (nth recipes idx)]
                        (api/create-cocktail-recipe
                         app-state
                         (assoc recipe :source "AI Chat"))))
                    (close!)
                    (swap! app-state assoc-in [:bar :active-tab] :recipes))]
        [dialog {:open open? :on-close close! :max-width "sm" :full-width true}
         [dialog-title (if multi? "Save Recipes" "Save Recipe")]
         [dialog-content {:sx {:pt "12px !important"}}
          (if multi?
            (map-indexed
             (fn [idx recipe]
               ^{:key idx}
               [box {:sx {:mb 1}}
                [form-control-label
                 {:control (r/as-element
                            [checkbox
                             {:checked (boolean (contains? sel idx))
                              :on-change (fn []
                                           (swap! selected
                                             (fn [s]
                                               (if (contains? s idx)
                                                 (disj s idx)
                                                 (conj s idx)))))}])
                  :label
                  (r/as-element
                   [box
                    [typography {:variant "body1" :sx {:fontWeight 600}}
                     (:name recipe)]
                    [typography {:variant "body2" :sx {:color "text.secondary"}}
                     (str (count (:ingredients recipe)) " ingredients")]])}]])
             recipes)
            (let [recipe (first recipes)]
              [:<>
               [text-field "Recipe Name" (or (:name recipe) "")
                #(swap! app-state assoc-in
                   [:chat :save-recipe :recipes 0 :name]
                   %)]
               (when (seq (:ingredients recipe))
                 [:<>
                  [typography {:variant "subtitle2" :sx {:mt 1.5 :mb 0.5}}
                   "Ingredients"]
                  [typography {:variant "body2" :sx {:color "text.secondary"}}
                   (str/join " · "
                             (map (fn [{:keys [amount unit name]}]
                                    (str/join " "
                                              (filter seq [amount unit name])))
                                  (:ingredients recipe)))]])]))]
         [dialog-actions [button {:on-click close!} "Cancel"]
          [button
           {:variant "contained"
            :on-click save!
            :disabled (and multi? (empty? sel))}
           (if multi?
             (str "Save Selected (" (count sel) ")")
             "Save to Recipes")]]]))))

(defn- tag-filter-bar
  [selected-tags all-tags]
  [box
   {:sx
    {:display "flex" :gap 0.5 :flexWrap "wrap" :alignItems "center" :mb 1.5}}
   (for [tag all-tags]
     (let [active? (contains? @selected-tags tag)]
       ^{:key tag}
       [chip
        {:label tag
         :size "small"
         :clickable true
         :on-click #(swap! selected-tags
                      (fn [s] (if (contains? s tag) (disj s tag) (conj s tag))))
         :sx {:height 24
              :fontSize "0.72rem"
              :letterSpacing "0.02em"
              :bgcolor
              (if active? "rgba(232,195,200,0.22)" "rgba(232,195,200,0.06)")
              :color "rgba(232,195,200,0.95)"
              :border
              (str "1px solid "
                   (if active? "rgba(232,195,200,0.6)" "rgba(232,195,200,0.2)"))
              "@media (hover: hover)" {"&:hover"
                                       {:bgcolor "rgba(232,195,200,0.18)"}}}}]))
   (when (seq @selected-tags)
     [button
      {:size "small"
       :sx {:ml 0.5 :fontSize "0.7rem" :minWidth 0 :px 1}
       :on-click #(reset! selected-tags #{})} "clear"])])

(defn- makeable-filter-chip
  [makeable-only?]
  (let [active? @makeable-only?]
    [chip
     {:label "Makeable"
      :size "small"
      :clickable true
      :on-click #(swap! makeable-only? not)
      :sx {:height 24
           :fontSize "0.72rem"
           :letterSpacing "0.02em"
           :mb 1.5
           :bgcolor (if active? "rgba(139,195,74,0.22)" "rgba(139,195,74,0.06)")
           :color "rgba(174,213,129,0.95)"
           :border (str
                    "1px solid "
                    (if active? "rgba(139,195,74,0.6)" "rgba(139,195,74,0.25)"))
           "@media (hover: hover)" {"&:hover" {:bgcolor
                                               "rgba(139,195,74,0.18)"}}}}]))

(defn- refresh-all-bar
  "Re-resolve every recipe's spirit/ingredient links sequentially, with a live
   progress count and a stop control."
  [app-state]
  (let [{:keys [running? done total]} (get-in @app-state
                                              [:bar :refresh-progress])]
    [box {:sx {:display "flex" :alignItems "center" :gap 0.5 :mb 1.5}}
     [button
      {:variant "text"
       :size "small"
       :disabled (boolean running?)
       :start-icon (when running? (r/as-element [circular-progress {:size 14}]))
       :on-click #(api/refresh-all-recipe-links app-state)
       :sx {:color "text.secondary" :textTransform "none" :fontSize "0.78rem"}}
      (if running? (str "Refreshing… " done "/" total) "Refresh all links")]
     (when running?
       [button
        {:variant "text"
         :size "small"
         :color "error"
         :on-click
         #(swap! app-state assoc-in [:bar :refresh-progress :stop?] true)
         :sx {:textTransform "none" :fontSize "0.78rem"}} "Stop"])]))

(defn recipes-tab
  [_app-state]
  (let [search-text (r/atom "")
        selected-tags (r/atom #{})
        selected-spirits (r/atom #{})
        selected-subspirits (r/atom #{})
        makeable-only? (r/atom false)]
    (fn [app-state]
      (let [bar @(r/cursor app-state [:bar])
            recipes (:recipes bar)
            spirits (:spirits bar)
            inventory-items (:inventory-items bar)
            show-form? (:show-recipe-form? bar)
            editing-id (:editing-recipe-id bar)
            viewing-id (:viewing-recipe-id bar)
            term (normalize-text @search-text)
            all-tags (->> recipes
                          (mapcat :tags)
                          (remove str/blank?)
                          distinct
                          sort
                          vec)
            present-spirits
            (into #{} (mapcat matching/recipe-spirit-cats) recipes)
            present-subpairs
            (into #{} (mapcat matching/recipe-subpairs) recipes)
            sel @selected-tags
            sel-spirits @selected-spirits
            sel-subspirits @selected-subspirits
            mk @makeable-only?
            filtered
            (cond->> recipes
              (seq term) (filter #(str/includes? (normalize-text
                                                  (recipe-search-text %))
                                                 term))
              (seq sel) (filter #(every? (set (:tags %)) sel))
              (seq sel-spirits) (filter #(some (matching/recipe-spirit-cats %)
                                               sel-spirits))
              (seq sel-subspirits)
              (filter #(some (matching/recipe-spirit-subcats %) sel-subspirits))
              mk (filter #(matching/recipe-makeable? % spirits inventory-items))
              :always (sort-by (juxt #(if (:rating %) 0 1)
                                     #(- (or (:rating %) 0)))))]
        [box (when show-form? [recipe-form app-state])
         (when (and (seq recipes) (not show-form?))
           [:<>
            [search-text-field
             {:search-atom search-text :label "Search recipes"}]
            (when (seq present-spirits)
              [category-filter-bar selected-spirits present-spirits])
            (when (seq sel-spirits)
              (let [sub (filter #(contains? sel-spirits (:cat %))
                                present-subpairs)]
                (when (seq sub)
                  [subcategory-filter-bar selected-subspirits sub])))
            (when (seq all-tags) [tag-filter-bar selected-tags all-tags])
            [makeable-filter-chip makeable-only?] [refresh-all-bar app-state]])
         (if (empty? recipes)
           [typography {:sx {:color "text.secondary" :textAlign "center" :py 4}}
            "No recipes yet. Save your first cocktail!"]
           (for [recipe filtered]
             ^{:key (:id recipe)}
             (cond (= (:id recipe) editing-id) [recipe-form app-state]
                   (= (:id recipe) viewing-id) [recipe-display app-state recipe]
                   :else [recipe-card app-state recipe])))]))))
