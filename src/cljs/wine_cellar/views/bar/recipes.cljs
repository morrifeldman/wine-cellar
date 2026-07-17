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
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.material.rating :refer [rating]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.checkbox :refer [checkbox]]
            [reagent-mui.icons.local-florist :refer [local-florist]]
            [reagent-mui.material.form-control-label :refer
             [form-control-label]]
            [reagent-mui.icons.add :refer [add]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.local-bar :refer [local-bar]]
            [reagent-mui.icons.star :refer [star] :rename {star star-icon}]
            [reagent-mui.icons.menu-book :refer [menu-book]]
            [reagent-mui.icons.notes :refer [notes] :rename {notes notes-icon}]
            [wine-cellar.utils.filters :refer [normalize-text]]
            [wine-cellar.views.bar.matching :as matching]
            [wine-cellar.views.bar.inventory :as inv]
            [wine-cellar.views.bar.spirits :refer
             [category-filter-bar subcategory-filter-bar category-colors
              category-labels]]
            [wine-cellar.views.components :refer
             [editable-text-field editable-autocomplete-field search-text-field
              detail-section]]
            [wine-cellar.views.components.form :refer
             [uncontrolled-text-field uncontrolled-text-area-field]]
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

(defn- view-category-from-recipe!
  "Switch to the Spirits tab filtered to a spirit category/subcategory, pushing a
   history entry so Back returns to the recipe that was open."
  [app-state recipe-id category subcategory]
  (.replaceState js/history
                 #js {:barNav #js {:activeTab "recipes"
                                   :viewingRecipeId recipe-id}}
                 ""
                 (.-pathname js/location))
  (.pushState js/history
              #js {:barNav #js {:activeTab "spirits"
                                :spiritsFilter #js {:category category
                                                    :subcategory (or subcategory
                                                                     "")}}}
              ""
              (.-pathname js/location))
  (swap! app-state #(-> %
                        (assoc-in [:bar :viewing-recipe-id] nil)
                        (assoc-in [:bar :active-tab] :spirits)
                        (assoc-in [:bar :editing-spirit-id] nil)
                        (assoc-in [:bar :spirits-initial-filter]
                                  {:categories #{category}
                                   :subcategories (if (seq subcategory)
                                                    #{subcategory}
                                                    #{})}))))

(defn- ref-value
  [input-ref]
  (some-> @input-ref
          .-value))

(defn- make-row
  "Row-structure entry for an ingredient: stable identity + initial values +
   DOM refs. The typed text lives only in the DOM until save."
  [ingredient]
  {:row-id (random-uuid)
   :ingredient ingredient ; preserves keys like :spirit,
                          ; :inventory_item_ids
   :name-ref (r/atom nil)
   :amount-ref (r/atom nil)
   :unit-ref (r/atom nil)})

(defn- ingredient-row
  [{:keys [ingredient name-ref amount-ref unit-ref]} on-remove]
  [box {:sx {:display "flex" :gap 1 :mb 1 :alignItems "center"}}
   [uncontrolled-text-field
    {:label "Ingredient"
     :initial-value (:name ingredient)
     :input-ref name-ref
     :sx {:flex 3}}]
   [uncontrolled-text-field
    {:label "Amount"
     :initial-value (:amount ingredient)
     :input-ref amount-ref
     :sx {:flex 2}}]
   [uncontrolled-text-field
    {:label "Unit"
     :initial-value (:unit ingredient)
     :input-ref unit-ref
     :sx {:flex 1.5}}]
   [icon-button {:size "small" :color "error" :on-click on-remove}
    [delete {:fontSize "small"}]]])

(defn recipe-form
  [app-state]
  (r/with-let
   [editing-id (get-in @app-state [:bar :editing-recipe-id]) recipe
    (if editing-id
      (first (filter #(= (:id %) editing-id)
                     (get-in @app-state [:bar :recipes])))
      (get-in @app-state [:bar :new-recipe])) rows
    (r/atom (mapv make-row (:ingredients recipe))) name-ref (r/atom nil)
    source-ref (r/atom nil) tags-ref (r/atom nil) description-ref (r/atom nil)
    instructions-ref (r/atom nil) notes-ref (r/atom nil) add-ingredient!
    #(swap! rows conj (make-row {})) remove-ingredient!
    (fn [row-id]
      (swap! rows (fn [rs] (vec (remove #(= (:row-id %) row-id) rs))))) cancel!
    (fn []
      (if editing-id
        (swap! app-state assoc-in [:bar :editing-recipe-id] nil)
        (do (swap! app-state assoc-in [:bar :show-recipe-form?] false)
            (swap! app-state assoc-in [:bar :new-recipe] {:ingredients []}))))
    submit!
    (fn [e]
      (.preventDefault e)
      (let [ingredients (mapv (fn [{:keys [ingredient name-ref amount-ref
                                           unit-ref]}]
                                (assoc ingredient
                                       :name (ref-value name-ref)
                                       :amount (ref-value amount-ref)
                                       :unit (ref-value unit-ref)))
                              @rows)
            tags-raw (or (ref-value tags-ref) "")
            tags (when (seq tags-raw) (mapv str/trim (str/split tags-raw #",")))
            payload (assoc recipe
                           :name (ref-value name-ref)
                           :source (ref-value source-ref)
                           :description (ref-value description-ref)
                           :instructions (ref-value instructions-ref)
                           :notes (ref-value notes-ref)
                           :tags tags
                           :ingredients ingredients)]
        (if editing-id
          (-> (api/update-cocktail-recipe app-state editing-id payload)
              ;; keep the recipe open (back to its detail view)
              ;; rather than collapsing after a save.
              (.then #(swap! app-state assoc-in
                        [:bar :viewing-recipe-id]
                        editing-id)))
          (api/create-cocktail-recipe app-state payload))))]
   [paper
    {:elevation 2 :sx {:p 2 :mb 2 :borderLeft "4px solid rgba(114,47,55,0.5)"}}
    [:form {:on-submit submit!}
     [typography {:variant "h6" :sx {:mb 2 :color "primary.main"}}
      (if editing-id "Edit Recipe" "Add Recipe")]
     [box {:sx {:display "flex" :gap 1.5 :flexWrap "wrap"}}
      [box {:sx {:flex "2 1 200px"}}
       [uncontrolled-text-field
        {:label "Recipe Name"
         :initial-value (:name recipe)
         :input-ref name-ref
         :sx {:width "100%"}}]]
      [box {:sx {:flex "1 1 200px"}}
       [uncontrolled-text-field
        {:label "Source"
         :initial-value (:source recipe)
         :input-ref source-ref
         :sx {:width "100%"}}]]
      [box {:sx {:flex "1 1 200px"}}
       [uncontrolled-text-field
        {:label "Tags (comma-separated)"
         :initial-value (when (seq (:tags recipe))
                          (str/join ", " (:tags recipe)))
         :input-ref tags-ref
         :sx {:width "100%"}}]]
      [box {:sx {:flex "3 1 400px"}}
       [uncontrolled-text-area-field
        {:label "Description"
         :initial-value (:description recipe)
         :rows 4
         :input-ref description-ref
         :sx {:width "100%"}}]]]
     [typography {:variant "subtitle2" :sx {:mb 1 :mt 0.5 :fontWeight 600}}
      "Ingredients"]
     (for [{:keys [row-id] :as row} @rows]
       ^{:key (str row-id)} [ingredient-row row #(remove-ingredient! row-id)])
     [button
      {:variant "outlined"
       :size "small"
       :start-icon (r/as-element [add])
       :on-click add-ingredient!
       :sx {:mb 2}} "Add Ingredient"]
     [uncontrolled-text-area-field
      {:label "Instructions"
       :initial-value (:instructions recipe)
       :rows 4
       :input-ref instructions-ref
       :sx {:width "100%" :mb 1}}]
     [uncontrolled-text-area-field
      {:label "Notes"
       :initial-value (:notes recipe)
       :rows 3
       :input-ref notes-ref
       :sx {:width "100%" :mb 1}}]
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

(defn- category-rgb
  "The bare \"R,G,B\" triple from an inventory category-meta rgba() color, so we
   can build chip bg/border/text at our own alpha levels."
  [category]
  (let [color (:color (get inv/category-meta
                           category
                           (get inv/category-meta "other")))]
    (second (re-find #"rgba\(([\d, ]+?),\s*[\d.]+\)" color))))

(defn- ingredient-link-chips
  "Informational (non-clickable) chips for the inventory items an ingredient is
   linked to, colored and iconed by the item's category to match the Mixers &
   Garnishes page. Dimmed with an \"out of stock\" suffix when not on hand.
   Dangling ids (item since deleted) are skipped."
  [inventory-items ingredient]
  (let [id-set (set (:inventory_item_ids ingredient))
        ;; in-stock first, out-of-stock pushed to the right (stable within
        ;; each)
        linked (->> inventory-items
                    (filter #(id-set (:id %)))
                    (sort-by (complement :have_it)))]
    (when (seq linked)
      [box {:sx {:display "flex" :flexWrap "wrap" :gap 0.5 :mt 0.25 :mb 0.25}}
       (for [item linked
             :let [out? (not (:have_it item))
                   rgb (category-rgb (:category item))
                   icon (:icon (get inv/category-meta
                                    (:category item)
                                    (get inv/category-meta "other")))]]
         ^{:key (:id item)}
         [chip
          {:label (str (:name item) (when out? " · out of stock"))
           :size "small"
           :icon (when icon
                   (r/as-element
                    [icon
                     {:sx {:fontSize "0.85rem"
                           :color (str "rgba(" rgb ",0.95) !important")}}]))
           :sx {:height 22
                :fontSize "0.7rem"
                :letterSpacing "0.02em"
                :opacity (if out? 0.55 1)
                :bgcolor (str "rgba(" rgb ",0.08)")
                :color (str "rgba(" rgb ",0.95)")
                :border (str "1px solid rgba(" rgb ",0.3)")}}])])))

(defn- bottle-chip
  "Clickable chip for a bottle under a spirit ingredient. `:dim?` dims it and
   prefixes a `~`; `:suffix` appends a ` · <suffix>` note (\"out of stock\" for
   an unavailable link); `:star?` marks a recipe-preferred bottle with a gold
   star."
  [app-state recipe-id spirit {:keys [dim? suffix star?]}]
  (let [base (str/join " · "
                       (filter seq [(:distillery spirit) (:name spirit)]))]
    [chip
     {:label (str (when dim? "~ ") base (when (seq suffix) (str " · " suffix)))
      :size "small"
      :clickable true
      :icon (when star?
              (r/as-element [star-icon
                             {:sx {:fontSize "0.8rem"
                                   :color
                                   "rgba(255,213,79,0.85) !important"}}]))
      :on-click #(view-spirit-from-recipe! app-state recipe-id (:id spirit))
      :sx {:height 22
           :fontSize "0.7rem"
           :letterSpacing "0.02em"
           :opacity (if dim? 0.55 1)
           :bgcolor "rgba(232,195,200,0.08)"
           :color "rgba(232,195,200,0.95)"
           :border "1px solid rgba(232,195,200,0.22)"
           "@media (hover: hover)" {"&:hover" {:bgcolor
                                               "rgba(232,195,200,0.16)"}}}}]))

(def ^:private max-bottle-chips
  "Bottle chips shown per ingredient before collapsing behind '+N more'."
  4)

(defn- bottle-chip-list
  "Renders a seq of bottle chips, collapsing everything past `limit` behind a
   '+N more' toggle chip so a deep bench (a dozen bourbons) doesn't flood the
   ingredient line. A single overflow chip isn't worth a toggle, so the
   collapse only kicks in past limit+1."
  [_limit _chips]
  (let [expanded? (r/atom false)]
    (fn [limit chips]
      (let [chips (vec chips)
            n (count chips)
            over? (> n (inc limit))
            shown
            (if (and over? (not @expanded?)) (subvec chips 0 limit) chips)]
        [:<> (seq shown)
         (when over?
           [chip
            {:label (if @expanded? "show fewer" (str "+" (- n limit) " more"))
             :size "small"
             :clickable true
             :on-click #(swap! expanded? not)
             :sx {:height 22
                  :fontSize "0.7rem"
                  :letterSpacing "0.02em"
                  :color "rgba(232,195,200,0.7)"
                  :bgcolor "transparent"
                  :border "1px dashed rgba(232,195,200,0.35)"
                  "@media (hover: hover)"
                  {"&:hover" {:bgcolor "rgba(232,195,200,0.1)"}}}}])]))))

(defn- spirit-bottle-chips
  "Bottle chips for an ingredient's spirit spec, from precomputed
   bottles-for-spec tiers. A named bottle (:spirit_id) shows alone, even when
   out of stock; otherwise owned category/subcategory matches show. No
   cross-subcategory substitutes — the category chip is the browse path.
   Bottles the recipe text itself recommends (the spec's
   :preferred_spirit_ids) sort first with a gold star, and when any are on
   hand the collapsed view shows just them — the rest of the bench waits
   behind '+N more'. nil when nothing's on hand."
  [app-state recipe-id {:keys [preferred_spirit_ids]} {:keys [exact sub]}]
  (let [owned? (fn [b] (pos? (or (:quantity b) 1)))
        exact-in (filter owned? exact)
        exact-out (remove owned? exact)
        pref? (comp (set preferred_spirit_ids) :id)
        sub (sort-by (complement pref?) sub)
        n-pref (count (filter pref? sub))]
    (cond (seq exact-in) [bottle-chip-list max-bottle-chips
                          (for [b exact-in]
                            ^{:key (str "s-" (:id b))}
                            [bottle-chip app-state recipe-id b {}])]
          ;; Named bottle out of stock: show only it (dimmed, "out of
          ;; stock"); explore alternatives via the category chip.
          (seq exact-out) [bottle-chip-list max-bottle-chips
                           (for [b exact-out]
                             ^{:key (str "x-" (:id b))}
                             [bottle-chip app-state recipe-id b
                              {:dim? true :suffix "out of stock"}])]
          (seq sub) [bottle-chip-list (if (pos? n-pref) n-pref max-bottle-chips)
                     (for [b sub]
                       ^{:key (str "s-" (:id b))}
                       [bottle-chip app-state recipe-id b
                        {:star? (boolean (pref? b))}])]
          :else nil)))

(defn- spirit-category-chip
  "Clickable taxonomy chip (e.g. \"Dry Gin\") that opens the Spirits tab
   filtered to that category/subcategory. When nothing matching the spec is
   owned (per the bottles-for-spec tiers), opens the whole category instead so
   the user can browse it and judge substitutions themselves."
  [app-state recipe-id {:keys [category subcategory]} {:keys [exact sub]}]
  (let [{:keys [base text]}
        (get category-colors category {:base "160,160,160" :text "#c0c0c0"})
        owned? (fn [b] (pos? (or (:quantity b) 1)))
        none-owned? (and (empty? (filter owned? exact)) (empty? sub))
        ;; "other" is a catch-all; when a subcategory names the spirit
        ;; (e.g. "Absinthe") show just that rather than "Absinthe Other".
        label (if (and (= category "other") (seq subcategory))
                subcategory
                (str (when (seq subcategory) (str subcategory " "))
                     (get category-labels category category)))]
    [chip
     {:label label
      :size "small"
      :clickable true
      :on-click #(view-category-from-recipe! app-state
                                             recipe-id
                                             category
                                             (when-not none-owned? subcategory))
      :sx {:height 22
           :fontSize "0.7rem"
           :letterSpacing "0.02em"
           :bgcolor (str "rgba(" base ",0.14)")
           :color text
           :border (str "1px solid rgba(" base ",0.35)")
           "@media (hover: hover)" {"&:hover"
                                    {:bgcolor (str "rgba(" base ",0.24)")}}}}]))

(defn- ingredients-list
  [app-state recipe statuses inventory-items spirits]
  [box {:component "ul" :sx {:mt 0 :mb 0 :pl 2.5 :listStyleType "none"}}
   (map-indexed
    (fn [idx {:keys [amount unit name spirit] :as ingredient}]
      (let [{:keys [glyph color]} (ingredient-mark (get statuses name))]
        ^{:key idx}
        [:li
         [typography {:variant "body2"}
          [box
           {:component "span"
            :sx {:color color :fontWeight 600 :mr 0.75 :ml -2}} glyph]
          (str/join " " (filter seq [amount unit name]))]
         (if spirit
           (let [tiers (matching/bottles-for-spec spirits spirit)]
             [box
              {:sx {:display "flex"
                    :flexWrap "wrap"
                    :alignItems "center"
                    :gap 0.5
                    :mt 0.25
                    :mb 0.25}}
              [spirit-category-chip app-state (:id recipe) spirit tiers]
              (or (spirit-bottle-chips app-state (:id recipe) spirit tiers)
                  [typography
                   {:variant "body2"
                    :sx {:color "text.secondary"
                         :fontSize "0.78rem"
                         :fontStyle "italic"}} "none on hand"])])
           [ingredient-link-chips inventory-items ingredient])]))
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
        [ingredients-list app-state recipe (:ingredient-status report)
         (:inventory-items bar) (:spirits bar)]
        [typography
         {:variant "body2" :sx {:color "text.secondary" :fontStyle "italic"}}
         "No ingredients yet — use Edit to add some."])]
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
      #(do (swap! app-state assoc-in [:bar :viewing-recipe-id] (:id recipe))
           (js/setTimeout
            (fn []
              (when-let [el (.getElementById js/document
                                             (str "recipe-" (:id recipe)))]
                (let [top (-> (.. el getBoundingClientRect -top)
                              (+ (.-pageYOffset js/window))
                              (- 16))]
                  (.scrollTo js/window #js {:top top :behavior "smooth"}))))
            100))}
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
  (let [selected (r/atom nil)
        dialog-name-ref (r/atom nil)]
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
                      (let [recipe (nth recipes idx)
                            recipe (if multi?
                                     recipe
                                     (assoc recipe
                                            :name
                                            (or (ref-value dialog-name-ref)
                                                (:name recipe))))]
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
               [uncontrolled-text-field
                {:label "Recipe Name"
                 :initial-value (:name recipe)
                 :reset-key (str "save-recipe-" (:name recipe))
                 :input-ref dialog-name-ref}]
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
  (let [selected @selected-tags]
    [box
     {:sx
      {:display "flex" :gap 0.5 :flexWrap "wrap" :alignItems "center" :mb 1.5}}
     (for [tag all-tags]
       (let [active? (contains? selected tag)]
         ^{:key tag}
         [chip
          {:label tag
           :size "small"
           :clickable true
           :on-click
           #(swap! selected-tags
              (fn [s] (if (contains? s tag) (disj s tag) (conj s tag))))
           :sx {:height 24
                :fontSize "0.72rem"
                :letterSpacing "0.02em"
                :bgcolor
                (if active? "rgba(232,195,200,0.22)" "rgba(232,195,200,0.06)")
                :color "rgba(232,195,200,0.95)"
                :border (str "1px solid "
                             (if active?
                               "rgba(232,195,200,0.6)"
                               "rgba(232,195,200,0.2)"))
                "@media (hover: hover)"
                {"&:hover" {:bgcolor "rgba(232,195,200,0.18)"}}}}]))
     (when (seq selected)
       [button
        {:size "small"
         :sx {:ml 0.5 :fontSize "0.7rem" :minWidth 0 :px 1}
         :on-click #(reset! selected-tags #{})} "clear"])]))

(defn- makeable-filter-chip
  "Single three-state makeability toggle: clicking cycles
   All recipes (neutral) → Makeable (green) → Missing (orange) → back."
  [makeable-filter]
  (let [mode @makeable-filter
        active? (some? mode)
        [label rgb] (case mode
                      :makeable ["Makeable" "139,195,74"]
                      :missing ["Missing" "255,167,38"]
                      ["All recipes" "232,195,200"])]
    [chip
     {:label label
      :size "small"
      :clickable true
      :on-click
      #(swap! makeable-filter {nil :makeable :makeable :missing :missing nil})
      :sx {:height 24
           :fontSize "0.72rem"
           :letterSpacing "0.02em"
           :mb 1.5
           :bgcolor (str "rgba(" rgb "," (if active? "0.22" "0.06") ")")
           :color (str "rgba(" rgb ",0.95)")
           :border (str "1px solid rgba(" rgb "," (if active? "0.6" "0.25") ")")
           "@media (hover: hover)" {"&:hover" {:bgcolor
                                               (str "rgba(" rgb ",0.18)")}}}}]))

(defn- ingredient-filter-toggle-chip
  "Collapsed entry point for the ingredient filter: expands/collapses the chip
   bar and shows the selection count while collapsed."
  [show-filter? sel-count]
  (let [open? @show-filter?
        hot? (or open? (pos? sel-count))
        label (cond open? "Ingredients ▴"
                    (pos? sel-count) (str "Ingredients · " sel-count)
                    :else "Ingredients ▾")]
    [chip
     {:label label
      :size "small"
      :clickable true
      :on-click #(swap! show-filter? not)
      :sx {:height 24
           :fontSize "0.72rem"
           :letterSpacing "0.02em"
           :mb 1.5
           :bgcolor (if hot? "rgba(232,195,200,0.22)" "rgba(232,195,200,0.06)")
           :color "rgba(232,195,200,0.95)"
           :border (str
                    "1px solid "
                    (if hot? "rgba(232,195,200,0.6)" "rgba(232,195,200,0.2)"))
           "@media (hover: hover)" {"&:hover" {:bgcolor
                                               "rgba(232,195,200,0.18)"}}}}]))

(defn- garnish-toggle-chip
  "Toggle for counting garnish-role ingredients in the filter; shown as a leaf
   icon next to the Ingredients chip while the filter is open. `recipes` and
   `inventory-items` are needed to prune stale selections when garnishes are
   toggled off."
  [include-garnishes? selected-ingredients open-cat recipes inventory-items]
  (let [on? @include-garnishes?]
    [chip
     {:label (r/as-element [local-florist
                            {:sx {:fontSize 16 :display "block"}}])
      :title "Include garnishes"
      :size "small"
      :clickable true
      :on-click
      (fn []
        (swap! include-garnishes? not)
        ;; Turning garnishes off: prune selections no longer reachable
        ;; without garnish lines/items so they can't silently zero the
        ;; list, and close the Garnishes category if it's open.
        (when on?
          (let [idx (matching/recipe-item-index recipes inventory-items false)
                reachable (into #{} (mapcat val) idx)]
            (swap! selected-ingredients #(into #{} (filter reachable) %))
            (swap! open-cat #(when-not (= % "garnish") %)))))
      :sx {:height 24
           :mb 1.5
           "& .MuiChip-label" {:px 0.75}
           :bgcolor (str "rgba(139,195,74," (if on? "0.22" "0.06") ")")
           :color "rgba(174,213,129,0.95)"
           :border (str "1px solid rgba(139,195,74," (if on? "0.6" "0.25") ")")
           "@media (hover: hover)" {"&:hover" {:bgcolor
                                               "rgba(139,195,74,0.15)"}}}}]))

(defn- ingredient-category-bar
  "First level of the ingredient filter: one chip per inventory category
   present in `vocab`, in inv/category-order. Clicking opens/closes that
   category's item chips; a chip shows how many of its items are selected."
  [open-cat selected-ingredients vocab]
  (let [by-cat (group-by :category vocab)
        sel @selected-ingredients
        open @open-cat]
    [box
     {:sx {:display "flex"
           :gap 0.5
           :flexWrap "wrap"
           :alignItems "center"
           :mb 1.5
           :ml 1}}
     (for [cat inv/category-order
           :when (contains? by-cat cat)]
       (let [n (count (filter #(contains? sel (:id %)) (by-cat cat)))
             open? (= open cat)
             hot? (or open? (pos? n))
             rgb (category-rgb cat)]
         ^{:key cat}
         [chip
          {:label (str (get inv/category-labels cat cat)
                       (when (pos? n) (str " · " n)))
           :size "small"
           :clickable true
           :on-click #(swap! open-cat (fn [c] (when-not (= c cat) cat)))
           :sx {:height 22
                :fontSize "0.7rem"
                :letterSpacing "0.02em"
                :bgcolor (str "rgba(" rgb "," (if hot? "0.22" "0.06") ")")
                :color (str "rgba(" rgb ",0.95)")
                :border
                (str "1px solid rgba(" rgb "," (if hot? "0.6" "0.25") ")")
                "@media (hover: hover)"
                {"&:hover" {:bgcolor (str "rgba(" rgb ",0.15)")}}}}]))
     (when (seq sel)
       [button
        {:size "small"
         :sx {:ml 0.5 :fontSize "0.7rem" :minWidth 0 :px 1}
         :on-click #(reset! selected-ingredients #{})} "clear"])]))

(defn- ingredient-item-bar
  "Second level of the ingredient filter: item chips for the open category."
  [selected-ingredients items]
  (let [sel @selected-ingredients]
    [box
     {:sx {:display "flex"
           :gap 0.5
           :flexWrap "wrap"
           :alignItems "center"
           :mb 1.5
           :ml 2}}
     (for [item items]
       (let [active? (contains? sel (:id item))
             rgb (category-rgb (:category item))]
         ^{:key (:id item)}
         [chip
          {:label (:name item)
           :size "small"
           :clickable true
           :on-click #(swap! selected-ingredients (fn [s]
                                                    (if (contains? s (:id item))
                                                      (disj s (:id item))
                                                      (conj s (:id item)))))
           :sx {:height 22
                :fontSize "0.7rem"
                :letterSpacing "0.02em"
                :bgcolor (str "rgba(" rgb "," (if active? "0.22" "0.06") ")")
                :color (str "rgba(" rgb ",0.95)")
                :border
                (str "1px solid rgba(" rgb "," (if active? "0.6" "0.25") ")")
                "@media (hover: hover)"
                {"&:hover" {:bgcolor (str "rgba(" rgb ",0.15)")}}}}]))]))

(defn- refresh-all-bar
  "Re-resolve every recipe's spirit/ingredient links sequentially, with a live
   count naming the recipe being resolved and a stop control."
  [app-state]
  (let [{:keys [running? done total current]} (get-in @app-state
                                                      [:bar :refresh-progress])]
    [box {:sx {:display "flex" :alignItems "center" :gap 0.5 :mt 2}}
     [button
      {:variant "text"
       :size "small"
       :disabled (boolean running?)
       :start-icon (when running? (r/as-element [circular-progress {:size 14}]))
       :on-click #(api/refresh-all-recipe-links app-state)
       :sx {:color "text.secondary" :textTransform "none" :fontSize "0.78rem"}}
      (if running?
        (str "Refreshing… "
             done
             "/"
             total
             (when (seq current) (str " · " current)))
        "Refresh all links")]
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
        selected-ingredients (r/atom #{})
        include-garnishes? (r/atom false)
        show-ingredient-filter? (r/atom false)
        open-ingredient-cat (r/atom nil)
        makeable-filter (r/atom nil)]
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
            sel-ingredients @selected-ingredients
            item-index (matching/recipe-item-index recipes
                                                   inventory-items
                                                   @include-garnishes?)
            used-item-ids (into #{} (mapcat val) item-index)
            cat-order
            (into {} (map-indexed (fn [i c] [c i])) inv/category-order)
            ingredient-vocab
            (->> inventory-items
                 (filter #(used-item-ids (:id %)))
                 (sort-by (fn [item] [(get cat-order (:category item) 99)
                                      (normalize-text (:name item))])))
            mk @makeable-filter
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
              (seq sel-ingredients)
              (filter #(every? (get item-index (:id %) #{}) sel-ingredients))
              (= mk :makeable)
              (filter #(matching/recipe-makeable? % spirits inventory-items))
              (= mk :missing)
              (remove #(matching/recipe-makeable? % spirits inventory-items))
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
            [box {:sx {:display "flex" :gap 0.75 :flexWrap "wrap"}}
             [makeable-filter-chip makeable-filter]
             (when (seq ingredient-vocab)
               [ingredient-filter-toggle-chip show-ingredient-filter?
                (count sel-ingredients)])
             (when (and @show-ingredient-filter? (seq ingredient-vocab))
               [garnish-toggle-chip include-garnishes? selected-ingredients
                open-ingredient-cat recipes inventory-items])]
            (when (and @show-ingredient-filter? (seq ingredient-vocab))
              [ingredient-category-bar open-ingredient-cat selected-ingredients
               ingredient-vocab])
            (when @show-ingredient-filter?
              (let [items (filter #(= (:category %) @open-ingredient-cat)
                                  ingredient-vocab)]
                (when (seq items)
                  [ingredient-item-bar selected-ingredients items])))])
         (if (empty? recipes)
           [typography {:sx {:color "text.secondary" :textAlign "center" :py 4}}
            "No recipes yet. Save your first cocktail!"]
           (for [recipe filtered]
             (with-meta (cond (= (:id recipe) editing-id) [recipe-form
                                                           app-state]
                              (= (:id recipe) viewing-id) [recipe-display
                                                           app-state recipe]
                              :else [recipe-card app-state recipe])
                        {:key (:id recipe)})))
         (when (and (seq recipes) (not show-form?))
           [refresh-all-bar app-state])]))))
