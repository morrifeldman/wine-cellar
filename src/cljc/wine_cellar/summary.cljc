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

(defn- positive-quantity
  [wine]
  (max 0 (or (:quantity wine) 0)))

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

(defn- unique-varieties
  [wine]
  (->> (:varieties wine)
       (keep (fn [variety]
               (normalize-string (or (:name variety) (:variety variety)))))
       (remove str/blank?)
       set))

(defn- summarize-varieties
  [wines total-bottles]
  (let [entries (mapcat (fn [wine]
                          (let [qty (positive-quantity wine)]
                            (for [variety (unique-varieties wine)]
                              {:label variety :wines 1 :bottles qty})))
                        wines)
        aggregated (aggregate-custom entries)]
    (top-n-with-other aggregated total-bottles 4 "Other varieties")))

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
