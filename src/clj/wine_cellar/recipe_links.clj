(ns wine-cellar.recipe-links
  "Joining an AI link-resolution result back onto a recipe's ingredients."
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]))

(defn merge-links
  "Rewrites ingredients from a resolve-recipe-links result, joining by index.
   The fresh links are authoritative — :inventory_item_ids and :spirit are
   rebuilt from the result and cleared when absent. :garnish is sticky-true:
   an ingredient stays a garnish if either the result or the incoming
   ingredient says so (extraction saw the source text, so its flag is
   higher-confidence than a re-link's). A spec in a grab-bag category
   (liqueur/other) with neither subcategory nor spirit_id is discarded — such
   a spec is unsatisfiable by construction (bottles-for-spec never matches
   it), so it could only block makeability; the model sometimes emits one for
   dashed bitters despite the prompt."
  [ingredients {:keys [ingredient_links spirit_links]}]
  (let [link-by-idx (into {} (map (juxt :index identity)) ingredient_links)
        spirit-by-idx
        (into {} (map (juxt :ingredient_index identity)) spirit_links)]
    (vec
     (map-indexed
      (fn [i ing]
        (let [{:keys [inventory_item_ids garnish]} (get link-by-idx i)
              {:keys [spirit_id category subcategory preferred_spirit_ids
                      alternate_spirit_ids]}
              (get spirit-by-idx i)
              garnish? (or (true? garnish) (true? (:garnish ing)))
              spec? (and (seq category)
                         (or spirit_id
                             (seq subcategory)
                             (not (common/grab-bag-spirit-categories
                                   (str/lower-case category)))))]
          (cond-> (dissoc ing :inventory_item_ids :garnish :spirit)
            (seq inventory_item_ids) (assoc :inventory_item_ids
                                            (vec inventory_item_ids))
            garnish? (assoc :garnish true)
            spec? (assoc :spirit
                         (cond-> {:category category}
                           (seq subcategory) (assoc :subcategory subcategory)
                           spirit_id (assoc :spirit_id spirit_id)
                           (seq preferred_spirit_ids)
                           (assoc :preferred_spirit_ids
                                  (vec preferred_spirit_ids))
                           (seq alternate_spirit_ids)
                           (assoc :alternate_spirit_ids
                                  (vec alternate_spirit_ids)))))))
      ingredients))))
