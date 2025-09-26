(ns wine-cellar.utils.stats
  (:require [clojure.string :as str]))

(def price-bands
  [{:label "Under $25" :max 25}
   {:label "$25 - $49" :min 25 :max 50}
   {:label "$50 - $99" :min 50 :max 100}
   {:label "$100 - $199" :min 100 :max 200}
   {:label "$200+" :min 200}])

(defn- parse-number
  [value]
  (cond
    (nil? value) nil
    (number? value) value
    (string? value)
    (let [trimmed (str/trim value)
          parsed (js/parseFloat trimmed)]
      (when-not (js/isNaN parsed) parsed))
    :else nil))

(defn price->band-label
  [price]
  (let [numeric (parse-number price)]
    (if (number? numeric)
      (or (some (fn [{:keys [label min max]}]
                  (when (and (or (nil? min) (>= numeric min))
                             (or (nil? max) (< numeric max)))
                    label))
                price-bands)
          "Other")
      "Unspecified")))

(defn- normalized-label
  [value fallback]
  (let [label (some-> value str str/trim)]
    (if (str/blank? label) fallback label)))

(defn style-breakdown
  [wines]
  (->> wines
       (map #(normalized-label (:style %) "Unspecified"))
       (remove nil?)
       frequencies
       (map (fn [[label count]] {:label label :count count}))
       (sort-by (juxt (comp - :count) :label))))

(defn country-breakdown
  [wines]
  (->> wines
       (map #(normalized-label (:country %) "Unspecified"))
       (remove nil?)
       frequencies
       (map (fn [[label count]] {:label label :count count}))
       (sort-by (juxt (comp - :count) :label))))

(defn price-breakdown
  [wines]
  (let [items (->> wines
                   (map price->band-label)
                   (remove nil?)
                   frequencies
                   (map (fn [[label count]] {:label label :count count}))
                   (sort-by (juxt (comp - :count) :label)))]
    {:items items
     :total (reduce + (map :count items))}))

(defn unique-varieties
  [wine]
  (->> (:varieties wine)
       (keep :name)
       (map #(normalized-label % nil))
       (remove nil?)
       set))

(defn variety-breakdown
  [wines]
  (let [items (->> wines
                   (mapcat unique-varieties)
                   (remove nil?)
                   frequencies
                   (map (fn [[label count]] {:label label :count count}))
                   (sort-by (juxt (comp - :count) :label)))]
    {:items items
     :total (reduce + (map :count items))}))

(defn- current-year
  []
  (.getFullYear (js/Date.)))

(defn- drinking-window-status
  [wine]
  (let [year (current-year)
        from (:drink_from_year wine)
        to (:drink_until_year wine)
        from-num (when (number? from) from)
        to-num (when (number? to) to)]
    (cond
      (and to-num (> year to-num)) "Past window"
      (and from-num (< year from-num)) "Hold for later"
      (or from-num to-num) "Ready to drink"
      :else "No window set")))

(defn drinking-window-breakdown
  [wines]
  (let [items (->> wines
                   (map drinking-window-status)
                   frequencies
                   (map (fn [[label count]] {:label label :count count}))
                   (sort-by (juxt (comp - :count) :label)))]
    {:items items
     :total (reduce + (map :count items))}))

(defn total-bottles
  [wines]
  (reduce + 0 (map #(max 0 (or (:quantity %) 0)) wines)))

(defn total-value
  [wines]
  (reduce
   (fn [acc wine]
     (let [price (parse-number (:price wine))
           qty (max 0 (or (:quantity wine) 0))]
       (+ acc (* (or price 0) qty))))
   0 wines))

(defn average-internal-rating
  [wines]
  (let [ratings (->> wines (keep :latest_rating) (filter number?))]
    (when (seq ratings)
      (js/Math.round (/ (reduce + ratings) (count ratings))))))

(defn- purchase-year
  [wine]
  (when-let [date (:purchase_date wine)]
    (let [value (normalized-label date nil)]
      (when (and value (>= (count value) 4))
        (let [year-str (subs value 0 4)
              parsed (js/parseInt year-str 10)]
          (when-not (js/isNaN parsed) parsed))))))

(defn inventory-by-year
  [wines]
  (->> wines
       (map (fn [wine]
              (when-let [year (purchase-year wine)]
                {:year year
                 :remaining (max 0 (or (:quantity wine) 0))
                 :purchased (max 0 (or (:original_quantity wine)
                                      (:quantity wine) 0))})))
       (remove nil?)
       (reduce (fn [acc {:keys [year remaining purchased]}]
                 (update acc year
                         (fnil (fn [data]
                                 (-> data
                                     (update :remaining (fnil + 0) remaining)
                                     (update :purchased (fnil + 0) purchased)))
                               {:year year :remaining 0 :purchased 0})))
               {})
       vals
       (sort-by :year)))

(defn collection-stats
  ([wines] (collection-stats wines {}))
  ([wines {:keys [visible-wines]}]
   (let [visible (or visible-wines wines)
         totals {:total-wines (count wines)
                 :visible-wines (count visible)
                 :total-bottles (total-bottles wines)
                 :avg-rating (average-internal-rating wines)
                 :total-value (js/Math.round (total-value wines))}
         style-items (style-breakdown wines)
         country-items (country-breakdown wines)
         price-data (price-breakdown wines)
         window-data (drinking-window-breakdown wines)
         variety-data (variety-breakdown wines)
         inventory (inventory-by-year wines)]
     {:totals totals
      :style {:items style-items :total (:total-wines totals)}
      :country {:items country-items :total (:total-wines totals)}
      :price price-data
      :drinking-window window-data
      :varieties variety-data
      :inventory inventory})))
