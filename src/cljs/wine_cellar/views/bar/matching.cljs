(ns wine-cellar.views.bar.matching
  "Pure matching logic shared between the Spirits and Recipes tabs: mapping
   cocktail ingredients to the spirit taxonomy, finding owned bottles for a
   recipe's spirit requirements, and computing whether a recipe is makeable
   from the current bar inventory. A spirit requirement lives on its
   ingredient line as :spirit {:category :subcategory :spirit_id} — the
   line↔requirement link is structural, not positional. Leaf namespace —
   requires only clojure.string and the text-normalization helper, so both
   spirits.cljs and recipes.cljs can use it without a circular dependency."
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

(defn same-category?
  "Case-insensitive spirit-category equality. Spirits store categories lowercase
  (\"gin\"), but specs may capitalize them (\"Gin\", from the AI's capitalized
  bar context), so raw = would never match category-only specs."
  [a b]
  (and a b (= (str/lower-case a) (str/lower-case b))))

(defn same-subcategory?
  "Case- and whitespace-insensitive subcategory equality. Specs come from the
  AI and can drift in capitalization from the spirits taxonomy; a drifted
  string must not demote an owned bottle of the exact style."
  [a b]
  (and a b (= (str/lower-case (str/trim a)) (str/lower-case (str/trim b)))))

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

(defn recipe-spirit-specs
  "Spirit specs ({:category :subcategory :spirit_id}) embedded on a recipe's
  ingredient lines."
  [r]
  (into [] (keep :spirit) (:ingredients r)))

(defn recipe-spirit-cats
  "Spirit categories for a recipe: from embedded :spirit specs when present,
  otherwise the ingredient-name heuristic (manual/pre-backfill recipes)."
  [r]
  (let [specs (recipe-spirit-specs r)]
    (if (seq specs)
      (into #{} (keep :category) specs)
      (recipe-spirit-categories r))))

(defn recipe-spirit-subcats
  "Set of subcategory strings from a recipe's embedded spirit specs."
  [r]
  (into #{} (keep :subcategory) (recipe-spirit-specs r)))

(defn recipe-subpairs
  "{:subcat :cat} pairs from a recipe's embedded spirit specs (subcategory
  only)."
  [r]
  (into []
        (comp (filter :subcategory)
              (map (fn [p] {:subcat (:subcategory p) :cat (:category p)})))
        (recipe-spirit-specs r)))

;; Grab-bag categories whose bottles are NOT interchangeable (Campari ≠ triple
;; sec ≠ Chartreuse; absinthe ≠ aquavit). For these, a spirit spec must match
;; the exact subcategory or precise bottle — owning some other member of the
;; category does not satisfy it.
(def ^:private specific-categories #{"liqueur" "other"})

(defn bottles-for-spec
  "Bottles for an ingredient's spirit spec, in precedence tiers:
   :exact = the spirit whose :id = the spec's :spirit_id (the precise product
            link, surfaced even when out of stock so the link is never hidden);
   :sub   = owned (quantity>0) bottles in the same category (+ same subcategory
            when the spec names one).
   No cross-subcategory substitutes are offered: subcategories are often
   different ingredients that share a category (sweet vs dry vermouth), not
   interchangeable styles — the category chip is the browse-and-judge path.
   When the spec names a specific bottle (:spirit_id), only that bottle is
   returned (in :exact). When no specific bottle is named, the
   category/subcategory IS the spec, so owned matching bottles fill :sub.
   For specific-categories, bottles aren't interchangeable, so a
   subcategory-less spec matches nothing by category alone."
  [spirits {:keys [category subcategory spirit_id]}]
  (let [owned? (fn [s] (pos? (or (:quantity s) 1)))
        exact (when spirit_id (filterv #(= (:id %) spirit_id) spirits))
        specific? (specific-categories (some-> category
                                               str/lower-case))]
    (if (seq exact)
      {:exact exact :sub []}
      (let [in-cat (filter #(and (same-category? (:category %) category)
                                 (owned? %))
                           spirits)
            sub (if (str/blank? subcategory)
                  (if specific? [] (vec in-cat))
                  (filterv #(same-subcategory? (:subcategory %) subcategory)
                           in-cat))]
        {:exact [] :sub sub}))))

;; --- Makeability ("can I make this?") ---

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

(defn recipe-item-index
  "Map recipe-id → set of inventory-item ids the recipe uses, for the
   ingredient filter. Prefers a line's precise :inventory_item_ids links,
   falling back to a name-matches?-style whole-word scan for un-linked lines.
   When include-garnishes? is false, garnish-role lines (:garnish flag) are
   skipped and garnish-category items are excluded."
  [recipes inventory-items include-garnishes?]
  (let [items (if include-garnishes?
                inventory-items
                (remove #(= "garnish" (:category %)) inventory-items))
        item-ids (into #{} (map :id) items)
        ;; Precompile one whole-word regex per item so the fallback scan
        ;; doesn't rebuild patterns per line×item.
        matchers
        (into []
              (keep (fn [{:keys [id name]}]
                      (let [n (or (normalize-text name) "")]
                        (when (seq n)
                          [id
                           (re-pattern
                            (str "\\b"
                                 (str/replace n #"[.*+?^${}()|\[\]\\]" "\\\\$0")
                                 "\\b"))]))))
              items)
        line-ids (fn [{:keys [name garnish inventory_item_ids]}]
                   (when (or include-garnishes? (not garnish))
                     (if (seq inventory_item_ids)
                       (filter item-ids inventory_item_ids)
                       (let [n (or (normalize-text name) "")]
                         (when (seq n)
                           (keep (fn [[id re]] (when (re-find re n) id))
                                 matchers))))))]
    (into {}
          (map (juxt :id
                     (fn [r] (into #{} (mapcat line-ids) (:ingredients r)))))
          recipes)))

(defn ingredient-status
  "Per-line status for a recipe ingredient, for display marks:
   :have            — on hand (a linked/named inventory item we have);
   :missing-garnish — a garnish-category item we don't have (never blocks);
   :missing         — a non-garnish item we don't have, including ingredients
                      not present in the Mixers & Garnishes inventory at all.
   Spirit-named ingredients reflect whether any owned bottle of that category is
   on hand."
  [spirits inventory-items {:keys [name garnish inventory_item_ids]}]
  (let [n (or (normalize-text name) "")
        id-set (set inventory_item_ids)
        ;; Recipe-side garnish role: a missing garnish never blocks, no
        ;; matter how the linked inventory item is categorized (a lemon
        ;; twist links to the "fruit" lemon, not a "garnish" item).
        miss (if garnish :missing-garnish :missing)]
    (cond (str/blank? n) :have
          (ingredient-spirit-category name)
          (let [cat (ingredient-spirit-category name)]
            (if (some #(and (= (:category %) cat) (pos? (or (:quantity %) 1)))
                      spirits)
              :have
              miss))
          ;; Prefer the precise id links (survive renames/fuzzy wording),
          ;; with
          ;; OR semantics — on hand if ANY linked item is. Fall back to the
          ;; name-substring scan for un-linked ingredients.
          (seq id-set) (let [linked (filter #(id-set (:id %)) inventory-items)]
                         (cond (empty? linked) :have ; all links dangling —
                                                     ; assume available
                               (some :have_it linked) :have
                               (or garnish
                                   (every? #(= "garnish" (:category %)) linked))
                               :missing-garnish
                               :else :missing))
          :else (if-let [item (some #(when (name-matches? (:name %) name) %)
                                    inventory-items)]
                  (cond (:have_it item) :have
                        (or garnish (= "garnish" (:category item)))
                        :missing-garnish
                        :else :missing)
                  ;; Not in the Mixers & Garnishes inventory — don't assume
                  ;; available; flag as missing so it can be added/stocked.
                  miss))))

(defn spec-satisfied?
  "True when a spirit spec has an owned :exact or :sub bottle. An out-of-stock
  named bottle does NOT satisfy — you can't pour from an empty bottle, and we
  don't silently substitute."
  [spirits spec]
  (let [{:keys [exact sub]} (bottles-for-spec spirits spec)]
    (boolean (or (some #(pos? (or (:quantity %) 1)) exact) (seq sub)))))

(defn- spec-label
  "Display label for a spirit line's requirement: the line's own wording, else
  the spec's subcategory/category."
  [{:keys [name spirit]}]
  (or (when-not (str/blank? name) name)
      (let [{:keys [category subcategory]} spirit]
        (if (str/blank? subcategory) category subcategory))))

(defn recipe-match-report
  "Makeability report for a recipe given current inventory:
   {:makeable? bool
    :missing [labels]            — unsatisfied spirit lines + missing mixers
    :missing-garnishes [names]   — soft; never blocks
    :ingredient-status {name→status}}.
   Lines with an embedded :spirit spec are gated by bottles-for-spec; other
   lines drive the mixer/garnish checks. Garnishes never block."
  [recipe spirits inventory-items]
  (let [pairs (map (fn [{:keys [spirit] :as ing}]
                     [ing
                      (if spirit
                        (if (spec-satisfied? spirits spirit) :have :missing)
                        (ingredient-status spirits inventory-items ing))])
                   (:ingredients recipe))
        spirit-missing (->> pairs
                            (filter (fn [[ing status]]
                                      (and (:spirit ing) (= status :missing))))
                            (map (comp spec-label first))
                            (remove str/blank?)
                            distinct
                            vec)
        mixer-missing (->> pairs
                           (filter (fn [[ing status]]
                                     (and (not (:spirit ing))
                                          (= status :missing))))
                           (map (comp :name first))
                           (remove str/blank?)
                           distinct
                           vec)
        missing-garnishes (->> pairs
                               (filter #(= :missing-garnish (second %)))
                               (map (comp :name first))
                               (remove str/blank?)
                               distinct
                               vec)]
    {:makeable? (and (empty? spirit-missing) (empty? mixer-missing))
     :missing (vec (concat spirit-missing mixer-missing))
     :missing-garnishes missing-garnishes
     :ingredient-status
     (into {} (map (fn [[ing status]] [(:name ing) status])) pairs)}))

(defn recipe-makeable?
  "Thin predicate over recipe-match-report for the Makeable list filter."
  [recipe spirits inventory-items]
  (:makeable? (recipe-match-report recipe spirits inventory-items)))

(defn recipe-matches-spirit?
  "True when any of the recipe's spirit specs links this spirit directly by
   :spirit_id, or matches its category (and subcategory when the spec names
   one; a category-only spec matches any bottle of that category)."
  [recipe spirit]
  (boolean (some (fn [{:keys [category subcategory spirit_id]}]
                   (or (and spirit_id (= spirit_id (:id spirit)))
                       (and (same-category? category (:category spirit))
                            (or (str/blank? subcategory)
                                (same-subcategory? subcategory
                                                   (:subcategory spirit))))))
                 (recipe-spirit-specs recipe))))
