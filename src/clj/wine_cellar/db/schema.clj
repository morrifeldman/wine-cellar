(ns wine-cellar.db.schema
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]
            [honey.sql :as sql]))

;; Type definitions
(def create-wine-level-type
  {:raw
   ["DO $$ BEGIN "
    "IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'wine_level') THEN "
    "CREATE TYPE wine_level AS ENUM " [:inline (vec common/wine-levels)]
    "; END IF; END $$;"]})

(def create-wine-style-type
  {:raw
   ["DO $$ BEGIN "
    "IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'wine_style') THEN "
    "CREATE TYPE wine_style AS ENUM " [:inline (vec common/wine-styles)]
    "; END IF; END $$;"]})

;; Table schemas
(def classifications-table-schema
  {:create-table [:wine_classifications :if-not-exists]
   :with-columns
   [[:id :integer :generated :by-default :as :identity :primary-key]
    [:country :varchar [:not nil]] [:region :varchar [:not nil]] [:aoc :varchar]
    [:classification :varchar] [:vineyard :varchar] [:levels :wine_level :array]
    [:created_at :timestamp [:default [:now]]]
    [[:constraint :wine_classifications_natural_key] :unique-nulls-not-distinct
     [:composite :country :region :aoc :classification :vineyard]]]})

#_(sql/format classifications-table-schema)

(def wines-table-schema
  {:create-table [:wines :if-not-exists]
   :with-columns
   [[:id :integer :generated :by-default :as :identity :primary-key]
    [:producer :varchar] [:country :varchar [:not nil]]
    [:region :varchar [:not nil]] [:aoc :varchar] [:classification :varchar]
    [:vineyard :varchar] [:level :wine_level] [:name :varchar]
    [:vintage :integer :null] [:style :wine_style] [:location :varchar]
    [:purveyor :varchar] [:quantity :integer [:not nil] [:default 0]]
    [:price :decimal [10 2]] [:purchase_date :date] [:drink_from_year :integer]
    [:drink_until_year :integer] [:alcohol_percentage :decimal [4 2]]
    [:disgorgement_year :integer] [:tasting_window_commentary :text]
    [:label_image :bytea] [:label_thumbnail :bytea] [:back_label_image :bytea]
    [[:constraint :valid_tasting_window]
     [:check
      [:or [:= :drink_from_year] [:= :drink_until_year]
       [:<= :drink_from_year :drink_until_year]]]]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]]})
#_(sql/format wines-table-schema)

(def tasting-notes-table-schema
  {:create-table [:tasting_notes :if-not-exists]
   :with-columns [[:id :integer :generated :by-default :as :identity
                   :primary-key] [:wine_id :integer [:not nil]]
                  [:tasting_date :date] [:notes :text [:not nil]]
                  [:rating :integer
                   [:check [:and [:>= :rating 1] [:<= :rating 100]]]]
                  [:is_external :boolean [:default false]] [:source :varchar]
                  [:created_at :timestamp [:default [:now]]]
                  [:updated_at :timestamp [:default [:now]]]
                  [[:constraint :tasting_date_required_for_personal]
                   [:check
                    [:or [:= :is_external true]
                     [:and [:= :is_external false] [:not= :tasting_date nil]]]]]
                  [[:foreign-key :wine_id] :references [:entity :wines]
                   [:nest :id] :on-delete :cascade]]})
#_(sql/format tasting-notes-table-schema)

;; View schemas
(def wines-with-varieties-and-latest-rating-view-schema
  {:create-or-replace-view [:wines-with-varieties-and-latest-rating]
   :select [:w.*
            [{:select :tn.rating
              :from [[:tasting_notes :tn]]
              :where [:= :tn.wine_id :w.id]
              :order-by [[:tn.tasting_date :desc]]
              :limit [:inline 1]} :latest_rating]
            [{:select [[[:coalesce
                         [:json_agg
                          [:inline
                           [:json_build_object [:inline "id"] :gv.id
                            [:inline "name"] :gv.name [:inline "percentage"]
                            :wgv.percentage] [:raw "ORDER BY"] :gv.name]]
                         [:inline "[]"]]]]
              :from [[:wine_grape_varieties :wgv]]
              :join [[:grape_varieties :gv] [:= :wgv.variety_id :gv.id]]
              :where [:= :wgv.wine_id :w.id]} :varieties]]
   :from [[:wines :w]]})
#_(sql/format wines-with-varieties-and-latest-rating-view-schema)

;; Grape varieties tables
(def grape-varieties-table-schema
  {:create-table [:grape_varieties :if-not-exists]
   :with-columns [[:id :integer :generated :by-default :as :identity
                   :primary-key] [:name :varchar [:not nil] :unique]
                  [:created_at :timestamp [:default [:now]]]]})
#_(sql/format grape-varieties-table-schema)

(def wine-grape-varieties-table-schema
  {:create-table [:wine_grape_varieties :if-not-exists]
   :with-columns [[:wine_id :integer [:not nil]]
                  [:variety_id :integer [:not nil]] [:percentage :decimal [5 2]]
                  [[:constraint :wine_grape_varieties_pkey]
                   [:primary-key :wine_id :variety_id]]
                  [[:foreign-key :wine_id] :references [:wines :id] :on-delete
                   :cascade]
                  [[:foreign-key :variety_id] :references [:grape_varieties :id]
                   :on-delete :cascade]
                  [[:constraint :percentage_range]
                   [:check
                    [:or [:= :percentage nil]
                     [:and [:>= :percentage 0] [:<= :percentage 100]]]]]
                  [:created_at :timestamp [:default [:now]]]]})
#_(sql/format wine-grape-varieties-table-schema)

