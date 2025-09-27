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

(defn- series-key
  [group-by label]
  (let [prefix (name (or group-by :overall))
        base (-> (str label)
                 str/lower-case
                 (str/replace #"[^a-z0-9]+" "-")
                 (str/replace #"^-+|-+$" ""))]
    (if (str/blank? base)
      prefix
      (str prefix "-" base))))

(defn- wine-quantity
  [wine]
  (max 0 (or (:quantity wine) 0)))

(defn- historical-quantity
  [wine]
  (max 0 (or (:original_quantity wine)
             (:quantity wine)
             0)))

(defn- aggregate-labels
  [entries metric]
  (let [aggregate
        (reduce (fn [acc {:keys [label wines bottles]}]
                  (if (str/blank? label)
                    acc
                    (let [current (get acc label {:label label :wines 0 :bottles 0})
                          wines (or wines 0)
                          bottles (or bottles 0)]
                      (assoc acc label
                             (-> current
                                 (update :wines + wines)
                                 (update :bottles + bottles))))))
                {}
                entries)
        items (->> aggregate
                   vals
                   (sort-by #(get % metric 0) >))
        totals {:wines (reduce + (map :wines items))
                :bottles (reduce + (map :bottles items))}]
    {:items items :totals totals}))

(defn style-breakdown
  [wines]
  (aggregate-labels
   (map (fn [wine]
          {:label (normalized-label (:style wine) "Unspecified")
           :wines 1
           :bottles (wine-quantity wine)})
        wines)
    :wines))

(defn country-breakdown
  [wines]
  (aggregate-labels
   (map (fn [wine]
          {:label (normalized-label (:country wine) "Unspecified")
           :wines 1
           :bottles (wine-quantity wine)})
        wines)
    :wines))

(defn price-breakdown
  [wines]
  (aggregate-labels
   (map (fn [wine]
          {:label (price->band-label (:price wine))
           :wines 1
           :bottles (wine-quantity wine)})
        wines)
    :wines))

(defn unique-varieties
  [wine]
  (->> (:varieties wine)
       (keep :name)
       (map #(normalized-label % nil))
       (remove nil?)
       set))

(defn variety-breakdown
  [wines]
  (aggregate-labels
   (mapcat (fn [wine]
             (let [qty (wine-quantity wine)]
               (for [variety (unique-varieties wine)]
                 {:label variety
                  :wines 1
                  :bottles qty})))
           wines)
    :wines))

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

(defn- normalized-window
  [wine]
  (let [from (:drink_from_year wine)
        to (:drink_until_year wine)
        from-num (when (number? from) from)
        to-num (when (number? to) to)]
    (cond
      (and from-num to-num)
      {:from (min from-num to-num)
       :to (max from-num to-num)}
      from-num
      {:from from-num :to from-num}
      to-num
      {:from to-num :to to-num}
      :else nil)))

(defn drinking-window-breakdown
  [wines]
  (aggregate-labels
   (map (fn [wine]
          {:label (drinking-window-status wine)
           :wines 1
           :bottles (wine-quantity wine)})
        wines)
    :wines))

(defn- bounded-range
  [{min-value :min max-value :max}]
  (let [current (current-year)
        min-value (if (number? min-value) min-value current)
        max-value (if (number? max-value) max-value current)
        floor (js/Math.min min-value current)
        ceil (js/Math.max max-value current)
        start (js/Math.max (- floor 1) (- current 5))
        end (js/Math.max (+ ceil 1) (+ current 2))]
    {:start start :end end}))

(defn- window-entry
  [group-by wine]
  (let [label (case group-by
                :style (normalized-label (:style wine) "Unspecified")
                :country (normalized-label (:country wine) "Unspecified")
                :price (price->band-label (:price wine))
                "All wines")]
    {:wine wine
     :label label
     :window (normalized-window wine)
     :quantity (wine-quantity wine)}))

(defn- tally-unscheduled
  [entries]
  (reduce (fn [{:keys [wines bottles] :as acc} {:keys [window quantity]}]
            (if window
              acc
              {:wines (inc wines)
               :bottles (+ bottles quantity)}))
          {:wines 0 :bottles 0}
          entries))

(defn- ensure-overall-group
  [grouped entries add-overall?]
  (if add-overall?
    (assoc grouped "All wines" entries)
    grouped))

(defn- points-for-year
  [year entries]
  (let [matching (filter (fn [{:keys [window]}]
                           (let [{:keys [from to]} window]
                             (and (<= from year)
                                  (<= year to))))
                         entries)
        total-bottles (reduce + 0 (map :quantity matching))]
    {:year year
     :wines (count matching)
     :bottles total-bottles}))

(defn- build-series
  [group-by years grouped]
  (->> grouped
       (map (fn [[label entries]]
              {:key (series-key group-by label)
               :label label
               :points (vec (map #(points-for-year % entries) years))}))
       (sort-by (fn [{:keys [label]}]
                  (if (= label "All wines") "" label)))))

(defn optimal-window-timeline
  ([wines] (optimal-window-timeline wines {}))
  ([wines {:keys [group-by]}]
   (let [entries (map #(window-entry group-by %) wines)
         windows (filter :window entries)
         current (current-year)
         unscheduled (tally-unscheduled entries)]
     (if (seq windows)
      (let [min-year (apply min (map (comp :from :window) windows))
            max-year (apply max (map (comp :to :window) windows))
            {:keys [start end]} (bounded-range {:min min-year :max max-year})
            years (range start (inc end))
             grouped (ensure-overall-group
                      (cljs.core/group-by :label windows)
                      windows
                      (some? group-by))
            series (build-series group-by years grouped)]
         {:series (vec series)
          :years (vec years)
          :current-year current
          :unscheduled unscheduled
          :group-by group-by})
       {:series []
        :years []
        :current-year current
        :unscheduled unscheduled
        :group-by group-by}))))

(defn total-bottles
  ([wines]
   (total-bottles wines wine-quantity))
  ([wines quantity-fn]
   (reduce + 0 (map quantity-fn wines))))

(defn total-value
  ([wines]
   (total-value wines wine-quantity))
  ([wines quantity-fn]
  (reduce
   (fn [acc wine]
     (let [price (parse-number (:price wine))
           qty (quantity-fn wine)]
       (+ acc (* (or price 0) qty))))
   0 wines)))

(defn average-internal-rating
  [wines]
  (let [ratings (->> wines
                     (keep (fn [wine]
                             (parse-number (:latest_internal_rating wine))))
                     (filter number?))]
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
   (let [visible (->> (or visible-wines wines)
                      (vec))
         in-stock-wines (->> wines
                             (filter #(pos? (or (:quantity %) 0)))
                             (vec))
         subsets {:all wines
                  :in-stock in-stock-wines
                  :selected visible}
         chart-wines in-stock-wines
         counts (into {}
                      (map (fn [[subset coll]]
                             [subset (count coll)])
                           subsets))
         bottle-totals (into {}
                             (map (fn [[subset coll]]
                                    (let [quantity-fn (if (= subset :all)
                                                        historical-quantity
                                                        wine-quantity)]
                                      [subset (total-bottles coll quantity-fn)]))
                                  subsets))
         rating-totals (into {}
                             (map (fn [[subset coll]]
                                    [subset (average-internal-rating coll)])
                                  subsets))
         value-totals (into {}
                            (map (fn [[subset coll]]
                                   (let [quantity-fn (if (= subset :all)
                                                       historical-quantity
                                                       wine-quantity)]
                                     [subset (js/Math.round (total-value coll quantity-fn))]))
                                 subsets))
         totals {:counts counts
                 :bottles bottle-totals
                 :avg-rating rating-totals
                 :value value-totals}
         style-data (style-breakdown chart-wines)
         country-data (country-breakdown chart-wines)
         price-data (price-breakdown chart-wines)
         window-data (drinking-window-breakdown chart-wines)
         variety-data (variety-breakdown chart-wines)
         inventory (inventory-by-year chart-wines)
         optimal-window {:overall (optimal-window-timeline chart-wines)
                          :style (optimal-window-timeline chart-wines {:group-by :style})
                          :country (optimal-window-timeline chart-wines {:group-by :country})
                          :price (optimal-window-timeline chart-wines {:group-by :price})}]
     {:totals totals
      :style style-data
      :country country-data
      :price price-data
      :drinking-window window-data
      :varieties variety-data
      :inventory inventory
      :optimal-window optimal-window})))
