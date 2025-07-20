(ns wine-cellar.utils.filters
  (:require [clojure.string :as str]
            [wine-cellar.utils.vintage :refer [matches-tasting-window?]]))

;; Filter helper functions
(defn matches-text-search?
  [wine search-term]
  (if (empty? search-term)
    true ;; No search term, match all wines
    (let [search-lower (str/lower-case search-term)
          ;; Extract grape variety names from the varieties collection
          variety-names (when (:varieties wine) (map :name (:varieties wine)))
          ;; All searchable text fields
          searchable-fields
          [(or (:name wine) "") (or (:producer wine) "") (or (:region wine) "")
           (or (:aoc wine) "") (or (:country wine) "")
           (or (:classification wine) "") (or (:vineyard wine) "")
           (or (:style wine) "") (or (:location wine) "")
           (or (:purveyor wine) "") (or (:tasting_window_commentary wine) "")
           (or (:ai_summary wine) "")]
          ;; Combine text fields with variety names
          all-searchable (concat searchable-fields variety-names)]
      (some #(str/includes? (str/lower-case (str %)) search-lower)
            all-searchable))))

(defn matches-country?
  [wine country]
  (or (nil? country) (= country (:country wine))))

(defn matches-region?
  [wine region]
  (or (nil? region) (= region (:region wine))))

(defn matches-style? [wine style] (or (nil? style) (= style (:style wine))))

(defn matches-variety?
  [wine variety]
  (or (nil? variety) (some #(= variety (:name %)) (:varieties wine))))

(defn matches-price-range?
  [wine price-range]
  (or (nil? price-range)
      (let [price (:price wine)
            [min-price max-price] price-range]
        (and price (>= price min-price) (<= price max-price)))))

(defn parse-location
  "Parse location string like 'F12' into sortable components [column row]"
  [location]
  (when location
    (let [location-str (str/trim (str location))
          matches (re-matches #"^([A-J])(\d+)$" location-str)]
      (if matches
        [(nth matches 1) (js/parseInt (nth matches 2))]
        [location-str 0])))) ;; Fallback for non-standard locations

(defn matches-verification-status?
  [wine verification-filter]
  (case verification-filter
    :unverified-only (not (:verified wine))
    :verified-only (:verified wine)
    nil true ; Show all when no filter selected
    true))

(defn matches-columns?
  [wine selected-columns]
  (if (empty? selected-columns)
    true ; No column filter, show all
    (let [[column _] (parse-location (:location wine))]
      (contains? selected-columns column))))

(defn apply-sorting
  [wines field direction]
  (if field
    (let [sorted
          (sort-by
           (fn [wine]
             (let [val (get wine field)]
               (cond
                 ;; Handle location sorting with proper alphanumeric
                 ;; parsing
                 (= field :location) (let [[column row] (parse-location val)]
                                       [(or column "ZZ") (or row 999)]) ;; Sort
                                                                        ;; unknown
                                                                        ;; locations
                                                                        ;; last
                 ;; Handle nil ratings specifically
                 (and (#{:latest_internal_rating :average_external_rating}
                       field)
                      (nil? val))
                 -1 ;; Sort null ratings last
                 ;; Handle nil vintage specifically for NV wines
                 (and (= field :vintage) (nil? val)) 0 ;; Sort NV wines
                                                       ;; first
                 ;; Handle timestamps for date sorting
                 (and (#{:updated_at :created_at} field) val) (js/Date. val)
                 (nil? val) "" ;; For other fields, use empty string
                 (number? val) val
                 :else (str/lower-case (str val)))))
           wines)]
      (if (= :desc direction) (reverse sorted) sorted))
    wines))

;; Main filtering and sorting function
(defn filtered-sorted-wines
  [app-state]
  (let [wines (:wines @app-state)
        {:keys [search country region style tasting-window variety price-range
                verification columns]}
        (:filters @app-state)
        {:keys [field direction]} (:sort @app-state)
        show-out-of-stock? (:show-out-of-stock? @app-state)]
    (as-> wines w
      ;; Filter out zero-quantity wines if show-out-of-stock? is false
      (if show-out-of-stock? w (filter #(pos? (:quantity %)) w))
      ;; Apply all filters
      (filter #(matches-text-search? % search) w)
      (filter #(matches-country? % country) w)
      (filter #(matches-region? % region) w)
      (filter #(matches-style? % style) w)
      (filter #(matches-variety? % variety) w)
      (filter #(matches-price-range? % price-range) w)
      (filter #(matches-tasting-window? % tasting-window) w)
      (filter #(matches-verification-status? % verification) w)
      (filter #(matches-columns? % columns) w)
      ;; Apply sorting
      (apply-sorting w field direction))))

