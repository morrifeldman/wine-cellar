(ns wine-cellar.utils.tasting-window
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
