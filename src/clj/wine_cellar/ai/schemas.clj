(ns wine-cellar.ai.schemas
  "Each structured AI output described once, then rendered in the dialect each
  provider takes: JSON Schema for Anthropic and OpenAI, Gemini's OpenAPI
  subset for Gemini. Before, every provider kept its own copy and they drifted
  (missing descriptions, missing categories, no nullability on Gemini)."
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]))

(defn- label-field
  "A field the model may leave null; the schema's :null-note says when."
  [type description & {:as more}]
  (merge {:type type :nullable? true :description description} more))

(def drinking-window
  {:name "DrinkingWindow"
   :fields
   [[:drink_from_year
     {:type :integer
      :description
      "The year the optimal drinking window opens, when the wine first reaches peak quality. May be a past year for already-mature wines; do not clamp to the current year."}]
    [:drink_until_year
     {:type :integer
      :description
      "The last year the wine stays at peak quality (not merely drinkable). May be at or before the current year for wines already in decline."}]
    [:confidence
     {:type :string
      :enum ["high" "medium" "low"]
      :description "How confident you are in this assessment."}]
    [:reasoning
     {:type :string
      :description
      "A brief explanation focusing on peak-quality timing that also mentions the broader window when the wine remains enjoyable."}]]})

(def wine-label
  {:name "WineLabelAnalysis"
   :null-note "Return null when the label does not provide this information."
   :fields
   [[:producer (label-field :string "Producer or winery name.")]
    [:name
     (label-field :string "Specific wine name if distinct from the producer.")]
    [:vintage (label-field :integer "Vintage year, or null for non-vintage.")]
    [:country (label-field :string "Country of origin.")]
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
     (label-field :number
                  "Alcohol percentage as a number (e.g. 12.5), if visible.")]]})

(def spirit-label
  {:name "SpiritLabelAnalysis"
   :null-note
   "Use your knowledge of the product when the label doesn't show this; return null only when it is truly unknown."
   :fields
   [[:name
     (label-field
      :string
      "The expression or product name (e.g. \"Black Label 12 Year\", \"Small Batch\"), NOT the brand or distillery.")]
    [:category
     (label-field :string "Spirit type." :enum common/spirit-categories)]
    [:subcategory
     (label-field
      :string
      "More specific type (e.g. \"bourbon\", \"rye\", \"single malt\", \"reposado\", \"amaro\").")]
    [:distillery
     (label-field
      :string
      "Producer, brand, or distillery name (e.g. \"Johnnie Walker\", \"Buffalo Trace\").")]
    [:country (label-field :string "Country of origin.")]
    [:region
     (label-field :string
                  "Region of production (e.g. Speyside, Jalisco, Kentucky).")]
    [:age_statement
     (label-field :string "Age statement text if present (e.g. \"12 Year\").")]
    [:proof
     (label-field
      :integer
      "Proof as an integer (e.g. 80). If only ABV% is shown, convert it (ABV × 2).")]]})

(defn prompt-fields
  "The fields as the bullet list a system prompt shows the model, so the
  prompt and the enforced schema can't disagree about what a field means."
  [{:keys [fields null-note]}]
  (str (str/join "\n"
                 (for [[k {:keys [description enum]}] fields]
                   (str "- "
                        (name k)
                        ": "
                        description
                        (when enum
                          (str " Must be one of: " (str/join ", " enum) ".")))))
       (when null-note (str "\n\n" null-note))))

(defn- described
  "A nullable field's description carries the schema's null-note into the
  API schema, where the model sees it next to the field."
  [{:keys [nullable?] :as field} null-note]
  (cond-> field
    (and nullable? null-note) (update :description str " " null-note)))

(defn- json-field
  [{:keys [type nullable? enum description]} typed-enums?]
  (let [t (name type)]
    (cond-> {}
      (or typed-enums? (not enum)) (assoc :type (if nullable? [t "null"] t))
      enum (assoc :enum (cond-> (vec enum) nullable? (conj nil)))
      description (assoc :description description))))

(defn ->json-schema
  "Strict JSON Schema, as Anthropic's output_config and OpenAI's json_schema
  format take it: every field required, nullable ones typed [t \"null\"].
  Anthropic rejects a type list next to an enum, so for Anthropic pass
  :typed-enums? false and the enum alone (null included) types the field."
  ([schema] (->json-schema schema {:typed-enums? true}))
  ([{:keys [fields null-note]} {:keys [typed-enums?]}]
   {:type "object"
    :properties (into {}
                      (map (fn [[k f]] [k
                                        (json-field (described f null-note)
                                                    typed-enums?)]))
                      fields)
    :required (mapv first fields)
    :additionalProperties false}))

(defn- gemini-field
  [{:keys [type nullable? enum description]}]
  (cond-> {:type (str/upper-case (name type))}
    nullable? (assoc :nullable true)
    enum (assoc :enum (vec enum))
    description (assoc :description description)))

(defn ->gemini-schema
  "Gemini's OpenAPI subset: upper-case types, nullable as a flag."
  [{:keys [fields null-note]}]
  {:type "OBJECT"
   :properties (into {}
                     (map (fn [[k f]] [k
                                       (gemini-field (described f null-note))]))
                     fields)
   :required (mapv (comp name first) fields)})
