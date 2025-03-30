(ns wine-cellar.utils.vintage
  (:require [clojure.string :as str]))

(defn parse-date [date-str]
  (when (and date-str (not (str/blank? date-str)))
    (js/Date. date-str)))

(defn today []
  (let [now (js/Date.)]
    (js/Date. (.getFullYear now) (.getMonth now) (.getDate now))))

(defn get-year-date [year]
  (when year
    (js/Date. (str year "-01-01"))))

(defn tasting-window-status [wine]
  (let [today-date (today)
        ;; Support both date strings and year numbers
        drink-from (or (parse-date (:drink_from wine))
                       (get-year-date (:drink_from_year wine)))
        drink-until (or (parse-date (:drink_until wine))
                        (get-year-date (:drink_until_year wine)))]
    (cond
      ;; No tasting window defined
      (and (nil? drink-from) (nil? drink-until))
      :unknown

      ;; Before drinking window
      (and drink-from (< today-date drink-from))
      :too-young

      ;; After drinking window
      (and drink-until (> today-date drink-until))
      :too-old

      ;; Within drinking window
      :else
      :ready)))

(defn tasting-window-label [status]
  (case status
    :too-young "Too Young"
    :ready "Ready to Drink"
    :too-old "Past Prime"
    :unknown "Unknown"))

(defn tasting-window-color [status]
  (case status
    :too-young "warning.main"
    :ready "success.main"
    :too-old "error.main"
    :unknown "text.secondary"))

(defn matches-tasting-window? [wine status]
  (or (nil? status)
      (= status (tasting-window-status wine))))

(defn current-year []
  (js/parseInt (.getFullYear (js/Date.))))

;; Configuration for drinking window years
(def drink-from-future-years 10)  ;; How many future years to show in "Drink From" dropdown
(def drink-from-past-years 5)     ;; How many past years to show in "Drink From" dropdown
(def drink-until-years 20)        ;; How many future years to show in "Drink Until" dropdown

(defn default-drink-from-years []
  (concat
    ;; Current year and future years for aging potential (show these first)
    (map str (range
               (current-year)
               (+ (current-year) drink-from-future-years)))
    ;; Add past years for already-drinkable wines
    (map str (range (- (current-year) 1)
                    (- (current-year) (inc drink-from-past-years))
                    -1))))

(defn default-drink-until-years []
  (concat
    (map str (range (current-year)
                    (+ (current-year) drink-until-years)))))

(def vintage-range-years 10)  ;; How many recent years to show
(def vintage-range-offset 2)  ;; How many years back from current year to start

(defn default-vintage-years []
  (concat
    ;; Recent years starting a few years back
    (map str (range (- (current-year) vintage-range-offset)
                    (- (- (current-year) vintage-range-offset) vintage-range-years)
                    -1))
    ;; Then decades for older wines
    (map #(str (+ 1900 (* % 10))) (range 9 -1 -1))))
