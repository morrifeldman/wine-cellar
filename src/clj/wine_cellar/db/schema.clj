(ns wine-cellar.db.schema
  (:require [wine-cellar.common :as common]))

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
    [:styles :wine_style :array]
    [:location :varchar]
    [:quantity :integer [:not nil] [:default 0]]
    [:price :decimal [10 2]]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]]})

(def tasting-notes-table-schema
  {:create-table [:tasting_notes :if-not-exists]
   :with-columns
   [[:id :serial :primary-key]
    [:wine_id :integer [:not nil]]
    [:tasting_date :date [:not nil]]
    [:notes :text [:not nil]]
    [:rating :integer [:check [:and [:>= :rating 1] [:<= :rating 100]]]]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]
    [[:foreign-key :wine_id]
     :references [:entity :wines] [:nest :id]
     :on-delete :cascade]]})

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
  {:raw (str "'{" (clojure.string/join "," coll) "}'")})

(defn sql-cast [sql-type field]
  [:cast field sql-type])
