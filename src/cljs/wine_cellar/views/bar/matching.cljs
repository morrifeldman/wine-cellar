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

;; Grab-bag categories whose bottles are NOT interchangeable (Campari ≠ triple
;; sec ≠ Chartreuse; absinthe ≠ aquavit). For these, a recipe spirit must match
;; the exact subcategory or precise bottle — owning some other member of the
;; category does not satisfy it, and we offer no cross-subcategory substitutes.
(def ^:private specific-categories #{"liqueur" "other"})

(defn bottles-for-tag
  "Bottles for a recipe spirit tag, in precedence tiers — most precise first:
   :exact = the spirit whose :id = the tag's :spirit_id (the precise product
            link, surfaced even when out of stock so the link is never hidden);
   :sub   = owned (quantity>0) bottles in the same category (+ same subcategory
            when the tag names one);
   :alts  = owned bottles in other subcategories of the category.
   When the tag names a specific bottle (:spirit_id), only that bottle is
   returned (in :exact) and no substitutes are offered — an out-of-stock named
   bottle therefore leaves the tag unsatisfied; explore alternatives via the
   category/subcategory chip. When no specific bottle is named, the
   category/subcategory IS the spec, so owned matching bottles fill :sub/:alts.
   For specific-categories, bottles aren't interchangeable, so a subcategory-less
   tag matches nothing by category alone and no :alts substitutes are offered."
  [spirits {:keys [category subcategory spirit_id]}]
  (let [owned? (fn [s] (pos? (or (:quantity s) 1)))
        exact (when spirit_id (filterv #(= (:id %) spirit_id) spirits))
        specific? (specific-categories category)]
    (if (seq exact)
      {:exact exact :sub [] :alts []}
      (let [in-cat (filter #(and (= (:category %) category) (owned? %)) spirits)
            tiers (if (str/blank? subcategory)
                    {:sub (if specific? [] (vec in-cat)) :alts []}
                    (let [sub (filter #(= (:subcategory %) subcategory) in-cat)]
                      {:sub (vec sub)
                       :alts (if (or specific? (seq sub))
                               []
                               (vec (remove #(= (:subcategory %) subcategory)
                                            in-cat)))}))]
        (assoc tiers :exact [])))))

;; --- Makeability ("can I make this?") ---

;; Always-available basics that never block a recipe and aren't tracked as
;; inventory items. Everything else must be in the Mixers & Garnishes inventory
;; to count as available.
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
   :have            — on hand or an ignored basic (ice/water);
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
          (contains? ignored-basics n) :have
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

(defn- tag-ingredient-score
  "Heuristic strength that lowercased ingredient name `iname` is the source line
   for `tag`: the tag's resolved bottle name appearing in it is the strongest
   signal, then the category/subcategory words, then the base-spirit heuristic."
  [iname {:keys [category subcategory spirit_id]} spirits]
  (let [has? (fn [needle]
               (and (seq needle) (str/includes? iname (str/lower-case needle))))
        spirit (when spirit_id (first (filter #(= (:id %) spirit_id) spirits)))]
    (cond-> 0
      (and spirit (has? (:name spirit))) (+ 3)
      (has? subcategory) (+ 2)
      (has? category) (+ 2)
      (= category (ingredient-spirit-category iname)) (+ 2))))

(defn ingredient-tag-map
  "Map ingredient-index → spirit tag. Prefers each tag's explicit
   :ingredient_index (set by the AI during extraction/refresh); falls back to a
   heuristic match (tag-ingredient-score) for tags lacking one. Each tag and each
   ingredient line is used at most once; tags that match nothing are dropped."
  [ingredients tags spirits]
  (let [n (count ingredients)
        names (mapv #(str/lower-case (or (:name %) "")) ingredients)
        explicit
        (reduce (fn [m t]
                  (let [i (:ingredient_index t)]
                    (if
                      (and (integer? i) (<= 0 i) (< i n) (not (contains? m i)))
                      (assoc m i t)
                      m)))
                {}
                tags)
        placed (set (vals explicit))]
    (first (reduce
            (fn [[acc used] tag]
              (let [best (->> (range n)
                              (remove used)
                              (map (fn [i] [(tag-ingredient-score (names i)
                                                                  tag
                                                                  spirits) i]))
                              (filter #(pos? (first %)))
                              (sort-by (comp - first))
                              first
                              second)]
                (if best [(assoc acc best tag) (conj used best)] [acc used])))
            [explicit (set (keys explicit))]
            (remove placed tags)))))

(defn tag-satisfied?
  "True when a spirit tag has an owned (or out-of-stock precise) bottle."
  [spirits tag]
  (let [{:keys [exact sub alts]} (bottles-for-tag spirits tag)]
    ;; sub/alts are owned-only; exact may be out of stock
    (boolean
     (or (some #(pos? (or (:quantity %) 1)) exact) (seq sub) (seq alts)))))

(defn recipe-match-report
  "Makeability report for a recipe given current inventory:
   {:makeable? bool
    :missing [labels]            — unsatisfied spirit tags + missing mixers
    :missing-garnishes [names]   — soft; never blocks
    :ingredient-status {name→status}}.
   Spirit ingredient lines are gated by their :spirit_tags (matched to the line
   via ingredient-tag-map); other lines drive the mixer/garnish checks. Recipes
   with no tags have no spirit gate. Garnishes never block."
  [recipe spirits inventory-items]
  (let [ingredients (:ingredients recipe)
        tags (distinct (:spirit_tags recipe))
        tag-map (ingredient-tag-map ingredients tags spirits)
        spirit-idxs (set (keys tag-map))
        tag->idx (into {} (map (fn [[i t]] [t i])) tag-map)
        sat? #(tag-satisfied? spirits %)
        ;; missing spirit tags, labeled by their ingredient name when known
        spirit-missing
        (->> tags
             (remove sat?)
             (map (fn [{:keys [category subcategory] :as t}]
                    (or (when-let [i (tag->idx t)] (:name (nth ingredients i)))
                        (when-not (str/blank? subcategory) subcategory)
                        category)))
             (remove str/blank?)
             distinct
             vec)
        ;; Non-spirit ingredient lines drive the mixer/garnish checks;
        ;; spirit lines are gated above via spirit_tags to avoid
        ;; double-counting.
        mixer-pairs (for [[i ing] (map-indexed vector ingredients)
                          :when (not (spirit-idxs i))]
                      [(:name ing)
                       (ingredient-status spirits inventory-items ing)])
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
        status-map
        (into {}
              (map-indexed
               (fn [i ing] [(:name ing)
                            (if-let [tag (tag-map i)]
                              (if (sat? tag) :have :missing)
                              (ingredient-status spirits inventory-items ing))])
               ingredients))]
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
