(ns wine-cellar.db.schema
  (:require [wine-cellar.common :as common]))

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

(def ensure-red-sparkling-style
  {:raw
   ["ALTER TYPE wine_style ADD VALUE IF NOT EXISTS 'Red Sparkling' AFTER 'Rosé Sparkling'"]})

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
    [:closure_type :varchar] [:purveyor :varchar]
    [:quantity :integer [:not nil] [:default 0]] [:original_quantity :integer]
    [:price :decimal [10 2]] [:purchase_date :date] [:drink_from_year :integer]
    [:drink_until_year :integer] [:alcohol_percentage :decimal [4 2]]
    [:disgorgement_year :integer] [:dosage :decimal [6 2]]
    [:tasting_window_commentary :text] [:verified :boolean [:default false]]
    [:ai_summary :text] [:label_image :bytea] [:label_thumbnail :bytea]
    [:back_label_image :bytea]
    [[:constraint :valid_tasting_window]
     [:check
      [:or [:= :drink_from_year] [:= :drink_until_year]
       [:<= :drink_from_year :drink_until_year]]]]
    [:created_at :timestamp [:default [:now]]]
    [:updated_at :timestamp [:default [:now]]]]})
#_(sql/format wines-table-schema)

(def wines-add-closure-type-column
  {:raw ["ALTER TABLE wines ADD COLUMN IF NOT EXISTS closure_type varchar"]})

(def wines-add-dosage-column
  {:raw ["ALTER TABLE wines ADD COLUMN IF NOT EXISTS dosage decimal(6,2)"]})

(def tasting-notes-table-schema
  {:create-table [:tasting_notes :if-not-exists]
   :with-columns [[:id :integer :generated :by-default :as :identity
                   :primary-key] [:wine_id :integer [:not nil]]
                  [:tasting_date :date] [:notes :text [:not nil]]
                  [:rating :integer
                   [:check [:and [:>= :rating 1] [:<= :rating 100]]]]
                  [:is_external :boolean [:default false]] [:source :varchar]
                  [:wset_data :jsonb] [:created_at :timestamp [:default [:now]]]
                  [:updated_at :timestamp [:default [:now]]]
                  [[:foreign-key :wine_id] :references [:entity :wines]
                   [:nest :id] :on-delete :cascade]]})
#_(sql/format tasting-notes-table-schema)

(def ai-conversations-table-schema
  {:create-table [:ai_conversations :if-not-exists]
   :with-columns [[:id :integer :generated :by-default :as :identity
                   :primary-key] [:user_email :varchar [:not nil]]
                  [:title :varchar] [:wine_ids :integer :array]
                  [:wine_search_state :jsonb] [:auto_tags :varchar :array]
                  [:provider :varchar [:default "anthropic"]]
                  [:pinned :boolean [:default false]]
                  [:total_tokens_used :integer [:default 0]]
                  [:created_at :timestamp [:default [:now]]]
                  [:updated_at :timestamp [:default [:now]]]
                  [:last_message_at :timestamp [:default [:now]]]]})
#_(sql/format ai-conversations-table-schema)

(def ai-conversations-add-pinned-column
  {:raw
   ["ALTER TABLE ai_conversations ADD COLUMN IF NOT EXISTS pinned boolean DEFAULT false"]})

(def ai-conversations-add-provider-column
  {:raw
   ["ALTER TABLE ai_conversations ADD COLUMN IF NOT EXISTS provider varchar"]})


(def ai-conversation-messages-table-schema
  {:create-table [:ai_conversation_messages :if-not-exists]
   :with-columns [[:id :integer :generated :by-default :as :identity
                   :primary-key] [:conversation_id :integer [:not nil]]
                  [:is_user :boolean [:not nil]] [:content :text [:not nil]]
                  [:image_data :bytea] [:tokens_used :integer]
                  [:created_at :timestamp [:default [:now]]]
                  [[:foreign-key :conversation_id] :references
                   [:ai_conversations :id] :on-delete :cascade]]})
#_(sql/format ai-conversation-messages-table-schema)


;; View schemas
(def enriched-wines-view-schema
  {:create-or-replace-view [:enriched-wines]
   :select
   [:w.*
    [{:select :tn.rating
      :from [[:tasting_notes :tn]]
      :where [:and [:= :tn.wine_id :w.id] [:= :tn.is_external false]]
      :order-by [[:tn.tasting_date :desc]]
      :limit [:inline 1]} :latest_internal_rating]
    [{:select [[[:round [:avg :tn.rating]]]]
      :from [[:tasting_notes :tn]]
      :where [:and [:= :tn.wine_id :w.id] [:= :tn.is_external true]
              [:!= :tn.rating]]} :average_external_rating]
    [{:select [[[:coalesce
                 [:json_agg
                  [:inline
                   [:json_build_object [:inline "id"] :gv.id [:inline "name"]
                    :gv.name [:inline "percentage"] :wgv.percentage]
                   [:raw "ORDER BY"] :gv.name]] [:inline "[]"]]]]
      :from [[:wine_grape_varieties :wgv]]
      :join [[:grape_varieties :gv] [:= :wgv.variety_id :gv.id]]
      :where [:= :wgv.wine_id :w.id]} :varieties]
    [{:select
      [[[:coalesce
         [:json_agg
          [:inline
           [:json_build_object [:inline "id"] :tn.id [:inline "notes"] :tn.notes
            [:inline "rating"] :tn.rating [:inline "tasting_date"]
            :tn.tasting_date [:inline "is_external"] :tn.is_external
            [:inline "source"] :tn.source [:inline "wset_data"] :tn.wset_data]
           [:raw "ORDER BY"] :tn.tasting_date [:raw "DESC"]]] [:inline "[]"]]]]
      :from [[:tasting_notes :tn]]
      :where [:= :tn.wine_id :w.id]} :tasting_notes]]
   :from [[:wines :w]]})
#_(sql/format enriched-wines-view-schema)

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
