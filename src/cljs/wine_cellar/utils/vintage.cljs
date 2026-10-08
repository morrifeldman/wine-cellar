(ns wine-cellar.utils.vintage)

(defn current-year [] (.getFullYear (js/Date.)))

;; Enhanced to handle both the wine map structure from detail.cljs and the
;; existing structure
(defn tasting-window-status
  [wine]
  (let [this-year (current-year)
        ;; Support both date strings and year numbers
        drink-from (:drink_from_year wine)
        drink-until (:drink_until_year wine)]
    (cond
      ;; No tasting window defined
      (and (nil? drink-from) (nil? drink-until)) :unknown
      ;; Before drinking window
      (and drink-from (< this-year drink-from)) :too-young
      ;; After drinking window
      (and drink-until (> this-year drink-until)) :too-old
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
    :too-young "drinkingWindow.tooYoung"
    :ready "drinkingWindow.ready"
    :too-old "drinkingWindow.tooOld"
    :unknown "text.secondary"))

(defn matches-tasting-window?
  [wine status]
  (or (nil? status) (= status (tasting-window-status wine))))

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
  ([] (default-vintage-years vintage-range-offset))
  ([offset]
   (concat
    ;; Recent years starting a few years back
    (map str
         (range (- (current-year) offset)
                (- (- (current-year) offset) vintage-range-years)
                -1))
    ;; Then decades for older wines
    (map #(str (+ 1900 (* % 10))) (range 9 -1 -1)))))

(defn format-tasting-window-text
  [wine]
  (let [from-year (:drink_from_year wine)
        until-year (:drink_until_year wine)]
    (cond (and from-year until-year) (str from-year " to " until-year)
          from-year (str "From " from-year)
          until-year (str "Until " until-year)
          :else "")))
