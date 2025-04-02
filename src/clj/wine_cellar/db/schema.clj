(ns wine-cellar.db.schema
  (:require
   [clojure.string :as str]
   [wine-cellar.common :as common]
   [honey.sql :as sql]))

;; Type definitions
(def create-wine-level-type
  {:raw ["DO $$ BEGIN "
         "IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'wine_level') THEN "
         "CREATE TYPE wine_level AS ENUM "
         [:inline (vec common/wine-levels)]
         "; END IF; END $$;"]})

(def create-wine-style-type
  {:raw ["DO $$ BEGIN "
         "IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'wine_style') THEN "
         "CREATE TYPE wine_style AS ENUM "
         [:inline (vec common/wine-styles)]
         "; END IF; END $$;"]})

;; Table schemas
(def classifications-table-schema
  {:create-table [:wine_classifications :if-not-exists]
   :with-columns
   [[:id :serial :primary-key]
    [:country :varchar [:not nil]]
    [:region :varchar [:not nil]]
    [:aoc :varchar]
    [:communal_aoc :varchar]
    [:classification :varchar]
    [:vineyard :varchar]
    [:levels :wine_level :array]
    [:created_at :timestamp [:default [:now]]]
    [[:constraint :wine_classifications_natural_key]
     :unique-nulls-not-distinct
     [:composite :country :region :aoc :communal_aoc
      :classification :vineyard]]]})

(def wines-table-schema
  {:create-table [:wines :if-not-exists]
   :with-columns
   [[:id :serial :primary-key]
    [:producer :varchar]
    [:country :varchar [:not nil]]
    [:region :varchar [:not nil]]
    [:aoc :varchar]
    [:communal_aoc :varchar]
    [:classification :varchar]
    [:vineyard :varchar]
    [:level :wine_level]  ; nullable by default
    [:name :varchar]
    [:vintage :integer]
    [:style :wine_style]  ; Changed from styles (array) to style (single value)
    [:location :varchar]
    [:purveyor :varchar]  ; New field for tracking where the wine was purchased
    [:quantity :integer [:not nil] [:default 0]]
    [:price :decimal [10 2]]
    [:drink_from_year :integer]  ; When the wine is ready to drink (year)
    [:drink_until_year :integer] ; When the wine should be consumed by (year)
    [[:constraint :valid_tasting_window]
     [:check
      [:or
      [:= :drink_from_year]
      [:= :drink_until_year]
      [:<= :drink_from_year :drink_until_year]]]]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]]})
#_(sql/format wines-table-schema)
(def tasting-notes-table-schema
  {:create-table [:tasting_notes :if-not-exists]
   :with-columns
   [[:id :serial :primary-key]
    [:wine_id :integer [:not nil]]
    [:tasting_date :date] 
    [:notes :text [:not nil]]
    [:rating :integer [:check [:and [:>= :rating 1] [:<= :rating 100]]]]
    [:is_external :boolean [:default false]]
    [:source :varchar]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]
    [[:constraint :tasting_date_required_for_personal]
     [:check [:or
              [:= :is_external true]
              [:and
               [:= :is_external false]
               [:not= :tasting_date nil]]]]]
    [[:foreign-key :wine_id]
     :references [:entity :wines] [:nest :id]
     :on-delete :cascade]]})

#_(sql/format tasting-notes-table-schema)

;; View schemas
(def wines-with-ratings-view-schema
  {:create-or-replace-view [:wines-with-ratings]
   :select [:w.*
            [{:select :tn.rating
              :from [[:tasting_notes :tn]]
              :where [:= :tn.wine_id :w.id]
              :order-by [[:tn.tasting_date :desc]]
              :limit [:inline 1]} :latest_rating]]
   :from [[:wines :w]]})

;; Helper functions for SQL generation
(defn ->pg-array [coll]
  {:raw (str "'{" (str/join "," coll) "}'")})

(defn sql-cast [sql-type field]
  [:cast field sql-type])

