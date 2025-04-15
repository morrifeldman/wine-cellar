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

(defn aocs-for-region
  [classifications country region]
  (->> classifications
       (filter #(and (= country (:country %)) (= region (:region %))))
       (map :aoc)
       (remove nil?)
       distinct
       sort))

(defn classifications-for-aoc
  [classifications country region aoc]
  (->> classifications
       (filter
        #(and (= country (:country %)) (= region (:region %)) (= aoc (:aoc %))))
       (map :classification)
       (remove nil?)
       distinct
       sort))

(defn levels-for-classification
  [classifications country region aoc classification]
  (or (->> classifications
           (filter #(and (= country (:country %))
                         (= region (:region %))
                         (= aoc (:aoc %))
                         (= classification (:classification %))))
           first
           :levels)
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

(defn valid-name-producer?
  [wine]
  (or (not (str/blank? (:name wine))) (not (str/blank? (:producer wine)))))
