(ns wine-cellar.summary
  "Shared helpers for producing condensed cellar summaries usable in CLJ and CLJS."
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]))

(def ^:private vintage-bands
  [{:label "\u22642000" :max 2000}
   {:label "2001-2010" :min 2001 :max 2010}
   {:label "2011-2018" :min 2011 :max 2018}
   {:label "2019+" :min 2019}])

(defn- normalize-string
  [value]
  (some-> value str str/trim))

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
      (when (re-matches #"^\d{4}$" trimmed)
        #?(:clj  (Integer/parseInt trimmed)
           :cljs (js/parseInt trimmed 10))))
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

(defn- increment-aggregate
  [acc label bottles]
  (let [entry (get acc label {:label label :wines 0 :bottles 0})]
    (assoc acc label (-> entry
                         (update :wines inc)
                         (update :bottles + bottles)))))

(defn- aggregate-by
  [wines label-fn]
  (->> wines
       (reduce (fn [acc wine]
                 (let [label (label-fn wine)
                       bottles (positive-quantity wine)
                       valid-label (if (str/blank? label) "Unspecified" label)]
                   (increment-aggregate acc valid-label bottles)))
               {})
       vals
       (sort-by (fn [entry] [(- (:bottles entry)) (- (:wines entry))]))
       vec))

(defn- add-share
  [entries total]
  (mapv (fn [{:keys [bottles] :as entry}]
          (let [share (when (pos? total) (/ bottles (double total)))]
            (cond-> entry share (assoc :bottle-share share))))
        entries))

(defn- top-n-with-other
  [entries total n other-label]
  (let [top (vec (take n entries))
        remainder (drop n entries)
        other (when (seq remainder)
                (reduce (fn [acc {:keys [wines bottles]}]
                          (-> acc
                              (update :wines + wines)
                              (update :bottles + bottles)))
                        {:label other-label :wines 0 :bottles 0}
                        remainder))]
    {:top (add-share top total)
     :other (when (and other (pos? (:bottles other)))
              (-> other
                  (assoc :bottle-share (/ (:bottles other) (double total)))))}))

(defn- summarize-countries
  [wines total-bottles]
  (top-n-with-other (aggregate-by wines #(normalize-string (:country %))) total-bottles 3 "Other"))

(defn- summarize-styles
  [wines total-bottles]
  (top-n-with-other (aggregate-by wines #(canonical-style (:style %))) total-bottles 5 "Other"))

(defn- summarize-vintages
  [wines total-bottles]
  (let [data (reduce (fn [acc wine]
                       (let [year (parse-year (:vintage wine))
                             bottles (positive-quantity wine)]
                         (if-let [band (band-label-for-year year)]
                           (update acc :bands #(increment-aggregate % band bottles))
                           (update acc :non-vintage + bottles))))
                     {:bands {} :non-vintage 0}
                     wines)
        band-entries (->> (:bands data)
                          vals
                          (sort-by (fn [entry] [(- (:bottles entry)) (- (:wines entry))]))
                          vec)
        band-entries (add-share band-entries total-bottles)
        non-vintage (:non-vintage data)]
    {:bands band-entries
     :non-vintage (when (pos? non-vintage)
                    {:label "Non-vintage"
                     :bottles non-vintage
                     :bottle-share (when (pos? total-bottles)
                                     (/ non-vintage (double total-bottles)))})}))

(defn condensed-summary
  "Produce a condensed breakdown for in-stock wines.
   Returns a map with totals and top country/style/vintage information."
  [wines]
  (let [in-stock (filter in-stock? wines)
        total-wines (count in-stock)
        total-bottles (reduce + (map positive-quantity in-stock))]
    {:totals {:wines total-wines :bottles total-bottles}
     :countries (summarize-countries in-stock total-bottles)
     :styles (summarize-styles in-stock total-bottles)
     :vintages (summarize-vintages in-stock total-bottles)}))
