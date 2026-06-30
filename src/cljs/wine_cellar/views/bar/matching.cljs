(ns wine-cellar.views.bar.matching
  "Pure matching logic shared between the Spirits and Recipes tabs: mapping
   cocktail ingredients to the spirit taxonomy, finding owned bottles for a
   recipe's spirit tags, and computing whether a recipe is makeable from the
   current bar inventory. Leaf namespace — requires only clojure.string and the
   text-normalization helper, so both spirits.cljs and recipes.cljs can use it
   without a circular dependency."
  (:require [clojure.string :as str]
            [wine-cellar.utils.filters :refer [normalize-text]]))

;; Map a cocktail ingredient to a Spirits-tab category (reuses that taxonomy so
;; the recipe spirit filter shares chip styling with the Spirits tab). Ordered;
;; first matching bucket wins.
(def ^:private spirit-aliases
  [["gin" ["gin"]] ["whiskey" ["whiskey" "whisky" "bourbon" "rye" "scotch"]]
   ["rum" ["rum" "rhum" "cachaca" "cachaça"]] ["tequila" ["tequila"]]
   ["mezcal" ["mezcal"]] ["vodka" ["vodka"]]
   ["brandy" ["brandy" "cognac" "armagnac" "calvados" "applejack" "pisco"]]
   ;; Catch-all for less common spirituous bases (kept last so the specific
   ;; spirits above win). Maps to the Spirits-tab "other" category.
   ["other"
    ["shochu" "soju" "sake" "aquavit" "akvavit" "absinthe" "pastis" "baijiu"
     "arak" "raki" "ouzo"]]])

(defn ingredient-spirit-category
  "Canonical spirit category for an ingredient name, or nil. Word-boundary
  matched so e.g. \"gin\" does not match \"ginger\"."
  [name]
  (when name
    (let [lc (str/lower-case name)]
      (some (fn [[category aliases]]
              (when (some #(re-find (re-pattern (str "\\b" % "\\b")) lc)
                          aliases)
                category))
            spirit-aliases))))

(defn- recipe-spirit-categories
  "Set of spirit categories present in a recipe's ingredients."
  [r]
  (into #{} (keep (comp ingredient-spirit-category :name)) (:ingredients r)))

(defn recipe-spirit-cats
  "Spirit categories for a recipe: from AI-assigned :spirit_tags when present,
  otherwise the ingredient-name heuristic (manual/pre-backfill recipes)."
  [r]
  (if (seq (:spirit_tags r))
    (into #{} (keep :category) (:spirit_tags r))
    (recipe-spirit-categories r)))

(defn recipe-spirit-subcats
  "Set of subcategory strings from a recipe's stored :spirit_tags."
  [r]
  (into #{} (keep :subcategory) (:spirit_tags r)))

(defn recipe-subpairs
  "{:subcat :cat} pairs from a recipe's stored :spirit_tags (subcategory only)."
  [r]
  (into []
        (comp (filter :subcategory)
              (map (fn [p] {:subcat (:subcategory p) :cat (:category p)})))
        (:spirit_tags r)))

(defn bottles-for-tag
  "Bottles for a recipe spirit tag, in precedence tiers — most precise first:
   :exact = the spirit whose :id = the tag's :spirit_id (the precise product
            link, surfaced even when out of stock so the link is never hidden);
   :sub   = owned (quantity>0) bottles in the same category (+ same subcategory
            when the tag names one);
   :alts  = owned bottles in other subcategories of the category.
   An in-stock :exact link suppresses the lower tiers; an out-of-stock :exact
   link still lists in-stock substitutes below it."
  [spirits {:keys [category subcategory spirit_id]}]
  (let [owned? (fn [s] (pos? (or (:quantity s) 1)))
        exact (when spirit_id (filterv #(= (:id %) spirit_id) spirits))]
    (if (and (seq exact) (some owned? exact))
      {:exact exact :sub [] :alts []}
      (let [in-cat (filter #(and (= (:category %) category) (owned? %)) spirits)
            tiers (if (str/blank? subcategory)
                    {:sub (vec in-cat) :alts []}
                    (let [sub (filter #(= (:subcategory %) subcategory) in-cat)]
                      {:sub (vec sub)
                       :alts (if (empty? sub)
                               (vec (remove #(= (:subcategory %) subcategory)
                                            in-cat))
                               [])}))]
        (assoc tiers :exact (vec exact))))))

;; --- Makeability ("can I make this?") ---

;; Always-available basics that never block a recipe and aren't tracked as
;; inventory items.
(def ^:private ignored-basics #{"ice" "water"})

(defn name-matches?
  "True when the inventory item name appears as a whole-word substring of the
   ingredient name (both accent-normalized and lowercased), so item \"lime
   juice\" matches ingredient \"fresh lime juice\"."
  [item-name ingredient-name]
  (let [item (or (normalize-text item-name) "")
        ing (or (normalize-text ingredient-name) "")]
    (and (seq item)
         (boolean (re-find (re-pattern (str "\\b"
                                            (str/replace item
                                                         #"[.*+?^${}()|\[\]\\]"
                                                         "\\\\$0")
                                            "\\b"))
                           ing)))))

(defn ingredient-status
  "Per-line status for a recipe ingredient, for display marks:
   :have            — on hand, an ignored basic, or maps to no tracked item;
   :missing-garnish — a garnish-category item we don't have (never blocks);
   :missing         — a non-garnish tracked item we don't have.
   Spirit-named ingredients reflect whether any owned bottle of that category is
   on hand."
  [spirits inventory-items {:keys [name inventory_item_ids]}]
  (let [n (or (normalize-text name) "")
        id-set (set inventory_item_ids)]
    (cond (str/blank? n) :have
          (contains? ignored-basics n) :have
          (ingredient-spirit-category name)
          (let [cat (ingredient-spirit-category name)]
            (if (some #(and (= (:category %) cat) (pos? (or (:quantity %) 1)))
                      spirits)
              :have
              :missing))
          ;; Prefer the precise id links (survive renames/fuzzy wording),
          ;; with
          ;; OR semantics — on hand if ANY linked item is. Fall back to the
          ;; name-substring scan for un-linked ingredients.
          (seq id-set) (let [linked (filter #(id-set (:id %)) inventory-items)]
                         (cond (empty? linked) :have ; all links dangling —
                                                     ; assume available
                               (some :have_it linked) :have
                               (every? #(= "garnish" (:category %)) linked)
                               :missing-garnish
                               :else :missing))
          :else (if-let [item (some #(when (name-matches? (:name %) name) %)
                                    inventory-items)]
                  (cond (:have_it item) :have
                        (= "garnish" (:category item)) :missing-garnish
                        :else :missing)
                  ;; Maps to no tracked item — assume available.
                  :have))))

(defn recipe-match-report
  "Makeability report for a recipe given current inventory:
   {:makeable? bool
    :missing [labels]            — unsatisfied spirit tags + missing mixers
    :missing-garnishes [names]   — soft; never blocks
    :ingredient-status {name→status}}.
   Spirits are gated by :spirit_tags (owned bottle, exact or alt); recipes with
   no tags have no spirit gate. Garnishes never block."
  [recipe spirits inventory-items]
  (let [tags (distinct (:spirit_tags recipe))
        tag-satisfied?
        (fn [tag]
          (let [{:keys [exact sub alts]} (bottles-for-tag spirits tag)]
            ;; sub/alts are owned-only; exact may be out of stock
            (boolean (or (some #(pos? (or (:quantity %) 1)) exact)
                         (seq sub)
                         (seq alts)))))
        spirit-missing
        (->> tags
             (remove tag-satisfied?)
             (map (fn [{:keys [category subcategory]}]
                    (if (str/blank? subcategory) category subcategory)))
             (remove str/blank?)
             distinct
             vec)
        ;; Non-spirit ingredient lines drive the mixer/garnish checks;
        ;; spirit lines are gated above via spirit_tags to avoid
        ;; double-counting.
        mixer-pairs (for [{:keys [name] :as ing} (:ingredients recipe)
                          :when (not (ingredient-spirit-category name))]
                      [name (ingredient-status spirits inventory-items ing)])
        mixer-missing (->> mixer-pairs
                           (filter #(= :missing (second %)))
                           (map first)
                           (remove str/blank?)
                           distinct
                           vec)
        missing-garnishes (->> mixer-pairs
                               (filter #(= :missing-garnish (second %)))
                               (map first)
                               (remove str/blank?)
                               distinct
                               vec)
        status-map (into {}
                         (for [{:keys [name] :as ing} (:ingredients recipe)]
                           [name
                            (ingredient-status spirits inventory-items ing)]))]
    {:makeable? (and (empty? spirit-missing) (empty? mixer-missing))
     :missing (vec (concat spirit-missing mixer-missing))
     :missing-garnishes missing-garnishes
     :ingredient-status status-map}))

(defn recipe-makeable?
  "Thin predicate over recipe-match-report for the Makeable list filter."
  [recipe spirits inventory-items]
  (:makeable? (recipe-match-report recipe spirits inventory-items)))

(defn recipe-matches-spirit?
  "True when any of the recipe's :spirit_tags links this spirit directly by
   :spirit_id, or matches its category (and subcategory when the tag names one;
   a category-only tag matches any bottle of that category)."
  [recipe spirit]
  (boolean (some (fn [{:keys [category subcategory spirit_id]}]
                   (or (and spirit_id (= spirit_id (:id spirit)))
                       (and (= category (:category spirit))
                            (or (str/blank? subcategory)
                                (= subcategory (:subcategory spirit))))))
                 (:spirit_tags recipe))))
