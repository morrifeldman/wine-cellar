(ns wine-cellar.specs
  "Request body specs. Numeric ranges and vocabularies come from common, which
  the frontend forms and the schema CHECKs share."
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [wine-cellar.common :as common]))

;; Specs for individual fields. Numeric ranges come from common, which the
;; frontend forms and the schema CHECKs share.
(defn- in-range [range] #(common/in-range? range %))

(s/def ::producer string?)
(s/def ::country string?)
(s/def ::region string?)
(s/def ::appellation (s/nilable string?))
(s/def ::appellation_tier (s/nilable string?))
(s/def ::classification (s/nilable string?))
(s/def ::vineyard (s/nilable string?))
(s/def ::name string?)
(s/def ::vintage (s/nilable (s/and int? #(nil? (common/vintage-error %)))))
(s/def ::style (set common/wine-styles))
(s/def ::designation (s/nilable (set common/wine-designations)))
(s/def ::designations (s/coll-of (set common/wine-designations)))
(s/def ::location (s/nilable (s/and string? #(common/valid-location? %))))
(s/def ::quantity nat-int?)
(s/def ::original_quantity (s/nilable nat-int?))
(s/def ::price (s/nilable (s/and number? (complement neg?))))
(s/def ::purchase_date (s/nilable string?)) ;; Will be parsed to a date
(s/def ::tasting_date (s/nilable string?)) ;; Will be parsed to a date
(s/def ::rating (s/nilable (s/and int? (in-range common/rating-range))))
(s/def ::drink_from_year
  (s/nilable (s/and int? (in-range common/tasting-year-range))))
(s/def ::drink_until_year
  (s/nilable (s/and int? (in-range common/tasting-year-range))))
(s/def ::alcohol_percentage
  (s/nilable (s/and number? (in-range common/alcohol-range))))
(s/def ::disgorgement_year
  (s/nilable
   (s/and int? #(<= common/earliest-disgorgement % (common/current-year)))))
(s/def ::dosage (s/nilable (s/and number? (in-range common/dosage-range))))
(s/def ::tasting_window_commentary (s/nilable string?))
(s/def ::verified boolean?)
(s/def ::ai_summary (s/nilable string?))
(s/def ::closure_type (s/nilable (set common/closure-types)))
(s/def ::bottle_format (s/nilable (set common/bottle-formats)))
(s/def ::purveyor string?)
(s/def ::is_external boolean?)
(s/def ::source (s/nilable string?))
(s/def ::wset_data (s/nilable map?))
(s/def ::label_image (s/nilable string?))
(s/def ::label_thumbnail (s/nilable string?))
(s/def ::include_images boolean?)
(s/def ::back_label_image (s/nilable string?))
(s/def ::variety_id int?)
(s/def ::wine_id int?)
(s/def ::variety_name string?)
(s/def ::percentage (s/nilable (s/and number? #(<= 0 % 100))))
(s/def ::wine_variety (s/keys :req-un [::variety_id] :opt-un [::percentage]))
(s/def ::message string?)
(s/def ::wine-ids (s/coll-of int?))
(s/def ::wine map?)
(s/def ::conversation-history vector?)
(s/def ::image (s/nilable string?))
(s/def ::provider (s/and keyword? common/ai-providers))
(s/def ::effort (set common/ai-effort-levels))
(s/def ::title (s/nilable string?))
(s/def ::wine_search_state (s/nilable map?))
(s/def ::auto_tags (s/nilable (s/coll-of string?)))
(s/def ::pinned boolean?)
(s/def ::include-bar? boolean?)
(s/def ::chat_type string?)
(s/def ::conversation-create
  (s/keys :req-un [::provider]
          :opt-un [::title ::wine_ids ::wine_search_state ::auto_tags ::pinned
                   ::chat_type]))
(s/def ::conversation-update
  (s/keys :opt-un [::provider ::title ::wine_ids ::wine_search_state ::auto_tags
                   ::pinned]))
(s/def ::context_note (s/nilable map?))
(s/def ::conversation-message
  (s/keys :req-un [::is_user ::content]
          :opt-un [::image ::tokens_used ::context_note]))
(s/def ::truncate_after? boolean?)
(s/def ::message_count pos-int?)
(s/def ::conversation-message-update
  (s/keys :req-un [::content]
          :opt-un [::image ::tokens_used ::truncate_after? ::context_note]))
(s/def ::tasting-source string?)
(s/def ::tasting-sources (s/coll-of ::tasting-source))
(s/def ::enabled? boolean?)
(s/def ::device_id (s/and string? (complement str/blank?)))
(s/def ::measured_at (s/nilable string?))
(s/def ::temperatures (s/nilable map?))
(s/def ::humidity_pct (s/nilable number?))
(s/def ::pressure_hpa (s/nilable number?))
(s/def ::illuminance_lux (s/nilable number?))
(s/def ::co2_ppm (s/nilable number?))
(s/def ::battery_mv (s/nilable int?))
(s/def ::leak_detected (s/nilable boolean?))
(s/def ::notes (s/nilable string?))
(s/def ::reason (s/nilable string?))
(s/def ::adjustment int?)
(s/def ::change_amount int?)
(s/def ::occurred_at (s/nilable string?)) ;; Will be parsed to a timestamp
(s/def ::wine_ids (s/nilable (s/coll-of int?)))
(s/def ::is_user boolean?)
(s/def ::content string?)
(s/def ::tokens_used (s/nilable int?))
(s/def ::oz (s/and number? pos?))
(s/def ::bucket #{"raw" "15m" "1h" "6h" "1d"})
(s/def ::from ::measured_at)
(s/def ::to ::measured_at)
(s/def ::claim_code (s/and string? (complement str/blank?)))
(s/def ::refresh_token (s/and string? (complement str/blank?)))
(s/def ::firmware_version (s/nilable string?))
(s/def ::capabilities (s/nilable map?))
;; An IANA zone name such as "America/Los_Angeles"; Postgres rejects unknown
;; ones.
(s/def ::tz (s/and string? #(re-matches #"[A-Za-z0-9_+\-/]{1,64}" %)))
(s/def ::series-query (s/keys :opt-un [::device_id ::bucket ::from ::to ::tz]))
(s/def ::metadata (s/nilable map?))
(s/def ::sensor_config (s/nilable map?))
(s/def ::sensor-reading
  (s/keys :req-un [::device_id]
          :opt-un [::measured_at ::temperatures ::humidity_pct ::pressure_hpa
                   ::illuminance_lux ::co2_ppm ::battery_mv ::leak_detected
                   ::notes]))
(s/def ::device-claim
  (s/keys :req-un [::device_id ::claim_code]
          :opt-un [::firmware_version ::capabilities]))
(s/def ::device-token-request (s/keys :req-un [::device_id ::refresh_token]))
(s/def ::limit
  (s/and int?
         pos?
         #(<= % 500)))
(s/def ::sensor-reading-query (s/keys :opt-un [::device_id ::limit]))
(s/def ::query string?)
(s/def ::search-text (s/nilable string?))

;; Bar specs
;; Spirits and the mixer shelf each have their own category vocabulary.
(s/def :wine-cellar.specs.spirit/category (set common/spirit-categories))
(s/def :wine-cellar.specs.bar-item/category
  (set common/bar-inventory-categories))
(s/def ::subcategory (s/nilable string?))
(s/def ::distillery (s/nilable string?))
(s/def ::age_statement (s/nilable string?))
(s/def ::proof (s/nilable int?))
(s/def ::have_it boolean?)
(s/def ::sort_order (s/nilable int?))
(s/def ::amount (s/nilable string?))
(s/def ::unit (s/nilable string?))
(s/def ::spirit_id pos-int?)
(s/def ::garnish boolean?)
(s/def ::inventory_item_ids (s/coll-of pos-int?))
(s/def ::preferred_spirit_ids (s/coll-of pos-int?))
(s/def ::alternate_spirit_ids (s/coll-of pos-int?))
(s/def ::spirit
  (s/keys :req-un [:wine-cellar.specs.spirit/category]
          :opt-un [::subcategory ::spirit_id ::preferred_spirit_ids
                   ::alternate_spirit_ids]))
(s/def ::ingredient
  (s/keys :req-un [::name]
          :opt-un [::amount ::unit ::garnish ::inventory_item_ids ::spirit]))
(s/def ::ingredients (s/coll-of ::ingredient))
(s/def ::instructions (s/nilable string?))
(s/def ::action string?)
(s/def ::seconds pos-int?)
(s/def ::timer (s/keys :req-un [::action ::seconds]))
(s/def ::timers (s/nilable (s/coll-of ::timer)))
(s/def ::tags (s/nilable (s/coll-of string?)))
(s/def ::description (s/nilable string?))
(s/def ::caption (s/nilable string?))
(s/def ::message-text string?)

(def spirit-schema
  (s/keys :req-un [::name :wine-cellar.specs.spirit/category]
          :opt-un [::subcategory ::distillery ::country ::region ::age_statement
                   ::proof ::quantity ::price ::purchase_date ::location
                   ::notes]))

(def spirit-update-schema
  (s/keys :opt-un
          [::name :wine-cellar.specs.spirit/category ::subcategory ::distillery
           ::country ::region ::age_statement ::proof ::quantity ::price
           ::purchase_date ::location ::notes]))

(def bar-inventory-item-schema
  (s/keys :req-un [::name :wine-cellar.specs.bar-item/category]
          :opt-un [::have_it ::sort_order]))

;; Recipes are rated 1-10 (half stars, stored doubled), unlike wines' 1-100.
;; The key is still :rating, so the spec lives under its own namespace.
(s/def :wine-cellar.specs.recipe/rating
  (s/nilable (s/and int? (in-range common/recipe-rating-range))))

(def cocktail-recipe-schema
  (s/keys :req-un [::name ::ingredients]
          :opt-un [::caption ::description ::instructions ::timers ::notes
                   ::tags ::source :wine-cellar.specs.recipe/rating]))

(def cocktail-recipe-update-schema
  (s/keys :opt-un
          [::name ::ingredients ::caption ::description ::instructions ::timers
           ::notes ::tags ::source :wine-cellar.specs.recipe/rating]))

(def grape-variety-schema (s/keys :req-un [::variety_name]))

(def wine-fields
  "Every wine column a request may set. specs-test checks this against the
  table, so a new column has to be added here or listed as excluded there."
  [::producer ::name ::country ::region ::appellation ::appellation_tier
   ::classification ::vineyard ::designation ::vintage ::style ::location
   ::quantity ::original_quantity ::price ::purveyor ::purchase_date
   ::label_image ::label_thumbnail ::back_label_image ::drink_from_year
   ::drink_until_year ::alcohol_percentage ::disgorgement_year ::dosage
   ::tasting_window_commentary ::verified ::ai_summary ::closure_type
   ::bottle_format ::metadata])

(defmacro ^:private optional-keys
  "s/keys needs its keys at compile time; this spells out a var's vector."
  [fields-sym]
  `(s/keys :opt-un ~(deref (resolve fields-sym))))

(def wine-schema
  (s/merge (s/keys :req-un
                   [(or ::name ::producer) ::country ::region ::style
                    ::quantity])
           (optional-keys wine-fields)))

(def wine-update-schema
  "Any subset of the wine fields, but at least one of them."
  (s/and (optional-keys wine-fields)
         (fn [body]
           (some (set (map (comp keyword name) wine-fields)) (keys body)))))

(def image-update-schema
  (s/nilable (s/keys :opt-un
                     [::label_image ::label_thumbnail ::back_label_image])))

(def classification-schema
  (s/keys :req-un [::country ::region]
          :opt-un [::appellation ::appellation_tier ::classification]))

(def tasting-note-schema
  (s/keys :opt-un
          [::notes ::rating ::tasting_date ::is_external ::source ::wset_data]))
