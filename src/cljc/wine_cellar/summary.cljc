(ns wine-cellar.summary
  "Shared helpers for producing condensed cellar summaries usable in CLJ and CLJS."
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]))

(def ^:private vintage-bands
  [{:label "\u22642000" :max 2000}
   {:label "2001-2010" :min 2001 :max 2010}
   {:label "2011-2018" :min 2011 :max 2018}
   {:label "2019+" :min 2019}])

(def ^:private price-bands
  [{:label "Under $25" :max 25}
   {:label "$25 - $49" :min 25 :max 50}
   {:label "$50 - $99" :min 50 :max 100}
   {:label "$100 - $199" :min 100 :max 200}
   {:label "$200+" :min 200}])

(defn- current-year
  []
  #?(:clj (.getValue (java.time.Year/now))
     :cljs (.getFullYear (js/Date.))))

(defn- normalize-string
  [value]
  (some-> value str str/trim))

(defn- parse-number
  [value]
  (cond
    (nil? value) nil
    (number? value) value
    (string? value)
    (let [trimmed (str/trim value)]
      (when-not (str/blank? trimmed)
        #?(:clj (try (Double/parseDouble trimmed)
                    (catch NumberFormatException _ nil))
           :cljs (let [parsed (js/parseFloat trimmed)]
           (when-not (js/isNaN parsed) parsed)))))
    :else nil))

(defn- round-number
  [value]
  (let [numeric (or value 0)]
    #?(:clj (Math/round (double numeric))
       :cljs (js/Math.round numeric))))

(defn- positive-quantity
  [wine]
  (max 0 (or (:quantity wine) 0)))

(defn wine-quantity
  [wine]
  (positive-quantity wine))

(defn historical-quantity
  [wine]
  (max 0 (or (:original_quantity wine)
             (:quantity wine)
             0)))

(defn- in-stock?
  [wine]
  (pos? (positive-quantity wine)))

(defn- parse-year
  [value]
  (cond
    (number? value) (int value)
    (string? value)
    (let [trimmed (str/trim value)]
      (when-let [digits (re-find #"\d{4}" trimmed)]
        #?(:clj (Integer/parseInt digits)
           :cljs (js/parseInt digits 10))))
    :else nil))

(defn- band-label-for-year
  [year]
  (when (number? year)
    (some (fn [{:keys [label min max]}]
            (when (and (or (nil? min) (>= year min))
                       (or (nil? max) (<= year max)))
              label))
          vintage-bands)))

(defn- canonical-style
  [style]
  (let [value (normalize-string style)]
    (cond
      (str/blank? value) "Unspecified"
      (contains? common/wine-styles value) value
      :else value)))

(defn- aggregate-custom
  [entries]
  (->> entries
       (reduce (fn [acc {:keys [label wines bottles]}]
                 (let [clean-label (if (str/blank? label) "Unspecified" label)
                       entry (get acc clean-label {:label clean-label
                                                   :wines 0
                                                   :bottles 0})
                       wine-count (max 0 (or wines 0))
                       bottle-count (max 0 (or bottles 0))]
                   (assoc acc clean-label
                          (-> entry
                              (update :wines + wine-count)
                              (update :bottles + bottle-count)))))
               {})
       vals
       (sort-by (fn [{:keys [bottles wines]}]
                  [(- bottles) (- wines)]))
       vec))

(defn- aggregate-by
  [wines label-fn]
  (aggregate-custom
   (map (fn [wine]
          {:label (label-fn wine)
           :wines 1
           :bottles (positive-quantity wine)})
        wines)))

(defn- totals-from-items
  [items]
  {:wines (reduce + 0 (map :wines items))
   :bottles (reduce + 0 (map :bottles items))})

(defn style-breakdown
  "Aggregate wines by style, returning {:items [...], :totals {...}}."
  [wines]
  (let [items (aggregate-by wines #(canonical-style (:style %)))]
    {:items items :totals (totals-from-items items)}))

(defn country-breakdown
  [wines]
  (let [items (aggregate-by wines #(normalize-string (:country %)))]
    {:items items :totals (totals-from-items items)}))

(defn- price->band-label
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

(defn price-breakdown
  [wines]
  (let [items (aggregate-by wines #(price->band-label (:price %)))]
    {:items items :totals (totals-from-items items)}))

(defn- add-share
  [entries total]
  (mapv (fn [{:keys [bottles] :as entry}]
          (let [share (when (pos? total) (/ bottles (double total)))]
            (cond-> entry share (assoc :bottle-share share))))
        entries))

(defn- top-n-with-other
  [entries total n other-label]
  (let [top (vec (take n entries))
        remainder (vec (drop n entries))
        derived-total (reduce + 0 (map :bottles entries))
        total-bottles (if (pos? total) total derived-total)
        top-with-share (add-share top total-bottles)
        other (when (seq remainder)
                (let [other-wines (reduce + 0 (map :wines remainder))
                      other-bottles (reduce + 0 (map :bottles remainder))
                      share (when (pos? total-bottles)
                              (/ other-bottles (double total-bottles)))]
                  (when (pos? other-bottles)
                    {:label other-label
                     :wines other-wines
                     :bottles other-bottles
                     :bottle-share share})))]
    {:top top-with-share
     :other other}))

(defn- summarize-countries
  [wines total-bottles]
  (top-n-with-other (aggregate-by wines #(normalize-string (:country %))) total-bottles 3 "Other"))

(defn- summarize-styles
  [wines total-bottles]
  (top-n-with-other (aggregate-by wines #(canonical-style (:style %))) total-bottles 3 "Other"))

(defn- summarize-regions
  [wines total-bottles]
  (top-n-with-other (aggregate-by wines #(normalize-string (:region %))) total-bottles 3 "Other"))

(defn- unique-varieties
  [wine]
  (->> (:varieties wine)
       (keep (fn [variety]
               (normalize-string (or (:name variety)
                                     (:variety variety)))))
       (remove str/blank?)
       set))

(defn variety-breakdown
  [wines]
  (let [entries (mapcat (fn [wine]
                          (let [qty (positive-quantity wine)]
                            (for [variety (unique-varieties wine)]
                              {:label variety :wines 1 :bottles qty})))
                        wines)
        items (aggregate-custom entries)]
    {:items items :totals (totals-from-items items)}))

(defn- summarize-vintages
  [wines total-bottles]
  (let [{:keys [entries non-vintage]}
        (reduce (fn [{:keys [entries non-vintage]} wine]
                  (let [qty (positive-quantity wine)
                        band (some-> (:vintage wine) parse-year band-label-for-year)]
                    (if band
                      {:entries (conj entries {:label band :wines 1 :bottles qty})
                       :non-vintage non-vintage}
                      {:entries entries
                       :non-vintage (+ non-vintage qty)})))
                {:entries [] :non-vintage 0}
                wines)
        band-entries (add-share (aggregate-custom entries) total-bottles)
        non-vintage-entry (when (pos? non-vintage)
                            {:label "Non-vintage"
                             :bottles non-vintage
                             :bottle-share (when (pos? total-bottles)
                                             (/ non-vintage (double total-bottles)))})]
    {:bands band-entries
     :non-vintage non-vintage-entry}))

(defn- summarize-varieties
  [wines total-bottles]
  (let [entries (mapcat (fn [wine]
                          (let [qty (positive-quantity wine)]
                            (for [variety (unique-varieties wine)]
                              {:label variety :wines 1 :bottles qty})))
                        wines)
        aggregated (aggregate-custom entries)]
    (top-n-with-other aggregated total-bottles 4 "Other varieties")))

(defn- summarize-price-bands
  [wines total-bottles]
  (top-n-with-other (aggregate-by wines #(price->band-label (:price %))) total-bottles 3 "Other price bands"))

(defn- normalized-window
  [wine]
  (let [from (parse-year (:drink_from_year wine))
        to (parse-year (:drink_until_year wine))]
    (cond
      (and from to) {:from (min from to) :to (max from to)}
      from {:from from :to nil}
      to {:from nil :to to}
      :else nil)))

(defn- in-window?
  [year {:keys [from to]}]
  (and (or (nil? from) (<= from year))
       (or (nil? to) (>= to year))))

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
  (let [items (aggregate-by wines #(drinking-window-status %))]
    {:items items :totals (totals-from-items items)}))

(defn- ready-to-drink-summary
  [wines]
  (let [year (current-year)
        ready-wines (filter (fn [wine]
                              (let [qty (positive-quantity wine)
                                    window (normalized-window wine)]
                                (and (pos? qty) window (in-window? year window))))
                            wines)
        ready-bottles (reduce + 0 (map positive-quantity ready-wines))]
    (when (pos? ready-bottles)
      {:year year
       :total-bottles ready-bottles
       :styles (add-share (aggregate-by ready-wines #(canonical-style (:style %))) ready-bottles)})))

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
                     (keep (comp parse-number :latest_internal_rating))
                     (filter number?))]
    (when (seq ratings)
      (let [denominator (max 1 (count ratings))
            total (reduce + ratings)]
        (round-number (/ total denominator))))))

(defn- purchase-year
  [wine]
  (when-let [date (:purchase_date wine)]
    (let [value (normalize-string date)]
      (when (and value (>= (count value) 4))
        (let [year-str (subs value 0 4)
              parsed (parse-year year-str)]
          (when parsed parsed))))))

(defn inventory-by-year
  [wines]
  (->> wines
       (map (fn [wine]
              (when-let [year (purchase-year wine)]
                {:year year
                 :remaining (wine-quantity wine)
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

(defn- normalized-label
  [value fallback]
  (let [label (normalize-string value)]
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

(defn- bounded-range
  [{min-value :min max-value :max}]
  (let [current (current-year)
        min-value (if (number? min-value) min-value current)
        max-value (if (number? max-value) max-value current)
        floor (min min-value current)
        ceil (max max-value current)
        start (max (- floor 1) (- current 5))
        end (max (+ ceil 1) (+ current 2))]
    {:start start :end end}))

(defn- window-entry
  [group-by wine]
  (let [label (case group-by
                :style (canonical-style (:style wine))
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
                             (and (<= (or from year) year)
                                  (<= year (or to year)))))
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
  ([wines {grouping :group-by}]
   (let [entries (map #(window-entry grouping %) wines)
         windows (filter :window entries)
         current (current-year)
         unscheduled (tally-unscheduled entries)]
     (if (seq windows)
       (let [min-year (apply min (map (comp :from :window) windows))
             max-year (apply max (map (comp :to :window) windows))
             {:keys [start end]} (bounded-range {:min min-year :max max-year})
             years (range start (inc end))
             grouped (ensure-overall-group
                      (clojure.core/group-by :label windows)
                      windows
                      (some? grouping))
             series (build-series grouping years grouped)]
         {:series (vec series)
          :years (vec years)
          :current-year current
          :unscheduled unscheduled
          :group-by grouping})
        {:series []
         :years []
         :current-year current
         :unscheduled unscheduled
         :group-by grouping}))))

(defn collection-stats
  "Compute detailed collection stats for front-end displays.
   Mirrors the previous cljs implementation so CLJ/CLJS can share logic."
  ([wines] (collection-stats wines {}))
  ([wines {:keys [visible-wines]}]
   (let [all-wines (vec (or wines []))
         visible (vec (or visible-wines all-wines))
         in-stock-wines (vec (filter in-stock? all-wines))
         subsets {:all all-wines
                  :in-stock in-stock-wines
                  :selected visible}
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
                                     [subset (round-number (total-value coll quantity-fn))]))
                                 subsets))
         totals {:counts counts
                 :bottles bottle-totals
                 :avg-rating rating-totals
                 :value value-totals}
         chart-wines in-stock-wines
         style-data (style-breakdown chart-wines)
         country-data (country-breakdown chart-wines)
         price-data (price-breakdown chart-wines)
         drinking-window-data (drinking-window-breakdown chart-wines)
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
      :drinking-window drinking-window-data
      :varieties variety-data
      :inventory inventory
      :optimal-window optimal-window})))

(defn condensed-summary
  "Produce a condensed breakdown for in-stock wines.
   Returns a map with totals and top breakdown information ready for prompt formatting."
  [wines]
  (let [in-stock (filter in-stock? wines)
        total-wines (count in-stock)
        total-bottles (reduce + 0 (map positive-quantity in-stock))]
    {:totals {:wines total-wines :bottles total-bottles}
     :countries (summarize-countries in-stock total-bottles)
     :styles (summarize-styles in-stock total-bottles)
     :regions (summarize-regions in-stock total-bottles)
     :vintages (summarize-vintages in-stock total-bottles)
     :varieties (summarize-varieties in-stock total-bottles)
     :price-bands (summarize-price-bands in-stock total-bottles)
     :drinking-window {:ready (ready-to-drink-summary in-stock)}}))
