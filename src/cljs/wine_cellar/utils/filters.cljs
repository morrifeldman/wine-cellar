(ns wine-cellar.utils.filters
  (:require [clojure.string :as str]))

;; Filter helper functions
(defn matches-text-search? [wine search-term]
  (if (empty? search-term)
    true  ;; No search term, match all wines
    (let [search-lower (str/lower-case search-term)
          searchable-fields [(or (:name wine) "")
                             (or (:producer wine) "")
                             (or (:region wine) "")
                             (or (:aoc wine) "")]]
      (some #(str/includes?
              (str/lower-case %)
              search-lower)
            searchable-fields))))

(defn matches-country? [wine country]
  (or (nil? country) (= country (:country wine))))

(defn matches-region? [wine region]
  (or (nil? region) (= region (:region wine))))

(defn matches-style? [wine style]
  (or (nil? style) 
      (and (:styles wine)
           (some #(= style %) (:styles wine)))))

(defn apply-sorting [wines field direction]
  (if field
    (let [sorted (sort-by (fn [wine]
                           (let [val (get wine field)]
                             (cond
                               ;; Handle nil ratings specifically
                               (and (= field :latest_rating) (nil? val)) -1  ;; Sort null ratings last
                               (nil? val) ""  ;; For other fields, use empty string
                               (number? val) val
                               :else (str/lower-case (str val)))))
                         wines)]
      (if (= :desc direction) (reverse sorted) sorted))
    wines))

;; Main filtering and sorting function
(defn filtered-sorted-wines [app-state]
  (let [wines (:wines @app-state)
        {:keys [search country region styles]} (:filters @app-state)
        {:keys [field direction]} (:sort @app-state)
        show-out-of-stock? (:show-out-of-stock? @app-state)]

    (as-> wines w
      ;; Filter out zero-quantity wines if show-out-of-stock? is false
      (if show-out-of-stock? w (filter #(pos? (:quantity %)) w))
      ;; Apply all filters
      (filter #(matches-text-search? % search) w)
      (filter #(matches-country? % country) w)
      (filter #(matches-region? % region) w)
      (filter #(matches-style? % styles) w)
      ;; Apply sorting
      (apply-sorting w field direction))))
