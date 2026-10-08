(ns wine-cellar.ai.schemas
  "Each structured AI output described once, then rendered in the dialect each
  provider takes: JSON Schema for Anthropic and OpenAI, Gemini's OpenAPI
  subset for Gemini. Before, every provider kept its own copy and they drifted
  (missing descriptions, missing categories, no nullability on Gemini)."
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]))

(def ^:private null-note
  "Return null when the label does not provide this information.")

(defn- label-field
  "A label field the model may leave null when the label doesn't say."
  [type description & {:as more}]
  (merge
   {:type type :nullable? true :description (str description " " null-note)}
   more))

(def drinking-window
  {:name "DrinkingWindow"
   :fields
   [[:drink_from_year
     {:type :integer
      :description
      "Year the optimal drinking window opens (the wine first reaches peak quality). May be a past year for already-mature wines; do not clamp to the current year."}]
    [:drink_until_year
     {:type :integer
      :description
      "Last year the wine stays at peak quality (not merely drinkable). May be at or before the current year for wines already in decline."}]
    [:confidence
     {:type :string
      :enum ["high" "medium" "low"]
      :description "Confidence level for this assessment."}]
    [:reasoning
     {:type :string
      :description
      "Brief justification focusing on the wine's peak-quality years and mentioning the broader enjoyable window."}]]})

(def wine-label
  {:name "WineLabelAnalysis"
   :fields
   [[:producer (label-field :string "Producer or winery name.")]
    [:name
     (label-field :string "Specific wine name if distinct from the producer.")]
    [:vintage
     (label-field :integer
                  "Vintage year as an integer, or null for non-vintage.")]
    [:country (label-field :string "Country of origin printed on the label.")]
    [:region (label-field :string (:region common/field-descriptions))]
    [:appellation
     (label-field :string (:appellation common/field-descriptions))]
    [:appellation_tier
     (label-field :string (:appellation_tier common/field-descriptions)
                  :enum (vec (sort common/appellation-tiers)))]
    [:vineyard (label-field :string (:vineyard common/field-descriptions))]
    [:classification
     (label-field :string (:classification common/field-descriptions))]
    [:style
     (label-field :string "Wine style." :enum (vec (sort common/wine-styles)))]
    [:designation
     (label-field :string (:designation common/field-descriptions)
                  :enum (vec (sort common/wine-designations)))]
    [:bottle_format
     (label-field :string "Bottle format/size."
                  :enum (vec common/bottle-formats))]
    [:alcohol_percentage
     (label-field :number "Alcohol percentage as a number (e.g. 12.5).")]]})

(def spirit-label
  {:name "SpiritLabelAnalysis"
   :fields
   [[:name (label-field :string "Full spirit name (brand + expression).")]
    [:category
     (label-field :string "Spirit type." :enum common/spirit-categories)]
    [:subcategory
     (label-field
      :string
      "More specific type (e.g. \"bourbon\", \"rye\", \"single malt\", \"reposado\", \"amaro\").")]
    [:distillery (label-field :string "Producer or distillery name.")]
    [:country (label-field :string "Country of origin.")]
    [:region
     (label-field :string "Region of production (e.g. Speyside, Jalisco).")]
    [:age_statement
     (label-field :string "Age statement text if present (e.g. \"12 Year\").")]
    [:proof (label-field :integer "Proof value as an integer (e.g. 80).")]]})

(defn- json-field
  [{:keys [type nullable? enum description]}]
  (let [t (name type)]
    (cond-> {:type (if nullable? [t "null"] t)}
      enum (assoc :enum (cond-> (vec enum) nullable? (conj nil)))
      description (assoc :description description))))

(defn ->json-schema
  "Strict JSON Schema, as Anthropic's output_config and OpenAI's json_schema
  format take it: every field required, nullable ones typed [t \"null\"]."
  [{:keys [fields]}]
  {:type "object"
   :properties (into {} (map (fn [[k f]] [k (json-field f)])) fields)
   :required (mapv first fields)
   :additionalProperties false})

(defn- gemini-field
  [{:keys [type nullable? enum description]}]
  (cond-> {:type (str/upper-case (name type))}
    nullable? (assoc :nullable true)
    enum (assoc :enum (vec enum))
    description (assoc :description description)))

(defn ->gemini-schema
  "Gemini's OpenAPI subset: upper-case types, nullable as a flag."
  [{:keys [fields]}]
  {:type "OBJECT"
   :properties (into {} (map (fn [[k f]] [k (gemini-field f)])) fields)
   :required (mapv (comp name first) fields)})
