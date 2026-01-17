(ns wine-cellar.utils.formatting
  (:require [clojure.string :as str]))

(defn format-date
  [date-string]
  (if (str/blank? date-string)
    ""
    (let [parts (str/split (first (str/split date-string #"T")) #"-")
          year (first parts)
          month (second parts)
          day (last parts)]
      (str month "/" day "/" year))))

(defn format-date-iso
  [date-string]
  (if (str/blank? date-string)
    ""
    (let [parts (str/split (first (str/split date-string #"T")) #"-")
          year (first parts)
          month (second parts)
          day (last parts)]
      (str year "-" month "-" day))))

;; Data transformation helpers
(defn unique-countries
  [classifications]
  (->> classifications
       (map :country)
       distinct
       sort))

(defn regions-for-country
  [classifications country]
  (->> classifications
       (filter #(= country (:country %)))
       (map :region)
       distinct
       sort))

(defn appellations-for-region
  [classifications country region]
  (->> classifications
       (filter #(and (= country (:country %)) (= region (:region %))))
       (map :appellation)
       (remove nil?)
       distinct
       sort))

(defn vineyards-for-region
  [classifications country region]
  (->> classifications
       (filter #(and (= country (:country %)) (= region (:region %))))
       (map :vineyard)
       (remove nil?)
       distinct
       sort))

(defn classifications-for-appellation
  [classifications country region appellation]
  (->> classifications
       (filter #(and (= country (:country %))
                     (= region (:region %))
                     (= appellation (:appellation %))))
       (map :classification)
       (remove nil?)
       distinct
       sort))

(defn designations-for-classification
  [classifications country region appellation classification]
  (or (->> classifications
           (filter #(and (= country (:country %))
                         (= region (:region %))
                         (= appellation (:appellation %))
                         (= classification (:classification %))))
           first
           :designations)
      []))

(defn unique-purveyors
  "Returns a sorted list of unique purveyors from the wines collection"
  [wines]
  (->> wines
       (map :purveyor)
       (remove nil?)
       (remove str/blank?)
       distinct
       sort))

(defn unique-varieties
  "Returns a sorted list of unique variety names from the wines collection"
  [wines]
  (->> wines
       (mapcat :varieties)
       (map :name)
       (remove nil?)
       (remove str/blank?)
       distinct
       sort))

(defn valid-name-producer?
  [wine]
  (or (not (str/blank? (:name wine))) (not (str/blank? (:producer wine)))))
