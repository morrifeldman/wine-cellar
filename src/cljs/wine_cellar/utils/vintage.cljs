(ns wine-cellar.utils.vintage
  (:require [clojure.string :as str]))

(defn parse-date
  [date-str]
  (when (and date-str (not (str/blank? date-str))) (js/Date. date-str)))

(defn today
  []
  (let [now (js/Date.)]
    (js/Date. (.getFullYear now) (.getMonth now) (.getDate now))))

(defn get-year-date [year] (when year (js/Date. (str year "-01-01"))))

;; Enhanced to handle both the wine map structure from detail.cljs and the
;; existing structure
(defn tasting-window-status
  [wine]
  (let [today-date (today)
        ;; Support both date strings and year numbers
        drink-from (or (parse-date (:drink_from wine))
                       (get-year-date (:drink_from_year wine)))
        drink-until (or (parse-date (:drink_until wine))
                        (get-year-date (:drink_until_year wine)))]
    (cond
      ;; No tasting window defined
      (and (nil? drink-from) (nil? drink-until)) :unknown
      ;; Before drinking window
      (and drink-from (< today-date drink-from)) :too-young
      ;; After drinking window
      (and drink-until (> today-date drink-until)) :too-old
      ;; Within drinking window
      :else :ready)))

(defn tasting-window-label
  [status]
  (case status
    :too-young "Too Young"
    :ready "Ready to Drink"
    :too-old "Past Prime"
    :unknown "Unknown"))

(defn tasting-window-color
  [status]
  (case status
    :too-young "warning.main"
    :ready "success.main"
    :too-old "error.main"
    :unknown "text.secondary"))

(defn matches-tasting-window?
  [wine status]
  (or (nil? status) (= status (tasting-window-status wine))))

(defn current-year [] (js/parseInt (.getFullYear (js/Date.))))

;; Configuration for drinking window years
(def drink-from-future-years 10)  ;; How many future years to show in "Drink From" dropdown
(def drink-from-past-years 5)     ;; How many past years to show in "Drink From" dropdown
(def drink-until-years 20)        ;; How many future years to show in "Drink Until" dropdown

(defn default-drink-from-years
  []
  (concat
   ;; Current year and future years for aging potential (show these first)
   (map str (range (current-year) (+ (current-year) drink-from-future-years)))
   ;; Add past years for already-drinkable wines
   (map str
        (range (- (current-year) 1)
               (- (current-year) (inc drink-from-past-years))
               -1))))

(defn default-drink-until-years
  []
  (concat (map str
               (range (current-year) (+ (current-year) drink-until-years)))))

(def vintage-range-years 10)  ;; How many recent years to show
(def vintage-range-offset 2)  ;; How many years back from current year to start

(defn default-vintage-years
  []
  (concat
   ;; Recent years starting a few years back
   (map str
        (range (- (current-year) vintage-range-offset)
               (- (- (current-year) vintage-range-offset) vintage-range-years)
               -1))
   ;; Then decades for older wines
   (map #(str (+ 1900 (* % 10))) (range 9 -1 -1))))

(defn valid-vintage?
  "Validates that a vintage is a valid year (between 1800 and current year) or nil for NV"
  [year]
  (cond (nil? year) nil ; nil is valid for NV wines
        (js/isNaN year) "Vintage must be a valid year or NV"
        (< year 1800) "Vintage must be after 1800"
        (> year (current-year)) "Vintage cannot be in the future"
        :else nil))

(defn valid-tasting-year?
  [year]
  (cond (js/isNaN year) "Tasting year must be a valid year"
        (< year 1800) "Tasting year must be after 1800"
        (> year 2100) "Tasting year cannot be in the future"
        :else nil))

(defn valid-tasting-window?
  [drink-from-year drink-until-year]
  (if (or (nil? drink-from-year) (nil? drink-until-year))
    nil
    (or (valid-tasting-year? drink-from-year)
        (valid-tasting-year? drink-until-year)
        (cond (> drink-from-year drink-until-year)
              "Drink from year must be less than or equal to drink until year"
              :else nil))))

(defn format-tasting-window-text
  [wine]
  (let [from-year (:drink_from_year wine)
        until-year (:drink_until_year wine)]
    (cond (and from-year until-year) (str from-year " to " until-year)
          from-year (str "From " from-year)
          until-year (str "Until " until-year)
          :else "")))
