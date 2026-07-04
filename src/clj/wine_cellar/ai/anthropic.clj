(ns wine-cellar.ai.anthropic
  (:require [clojure.string :as str]
            [jsonista.core :as json]
            [mount.core :refer [defstate]]
            [org.httpkit.client :as http]
            [wine-cellar.common :as common]
            [wine-cellar.config-utils :as config-utils]
            [wine-cellar.ai.prompts :as prompts]))

(def api-url "https://api.anthropic.com/v1/messages")

(defstate
 model
 :start
 (config-utils/get-config "ANTHROPIC_MODEL" :fallback "claude-sonnet-4-6")) ; Default to current model

(defstate
 light-model
 :start
 (config-utils/get-config "ANTHROPIC_LIGHT_MODEL" :fallback "claude-haiku-4-5"))

(defstate api-key :start (config-utils/get-config "ANTHROPIC_API_KEY"))

(def drinking-window-tool-name "record_drinking_window")

(def drinking-window-tool
  (let
    [confidence-desc
     "Confidence level for this assessment; must be one of \"high\", \"medium\", or \"low\" per the drinking-window prompt."
     reasoning-desc
     "Brief justification focusing on the wine's peak-quality years and mentioning the broader enjoyable window."]
    {:name drinking-window-tool-name
     :description
     "Structured schema matching wine-cellar.ai.prompts/drinking-window-system-prompt."
     :input_schema
     {:type "object"
      :properties
      {:drink_from_year
       {:type "integer"
        :description
        "Year the optimal drinking window opens (the wine first reaches peak quality). May be a past year for already-mature wines; do not clamp to the current year."}
       :drink_until_year
       {:type "integer"
        :description
        "Last year the wine stays at peak quality (not merely drinkable). May be at or before the current year for wines already in decline."}
       :confidence {:type "string" :description confidence-desc}
       :reasoning {:type "string" :description reasoning-desc}}
      :required [:drink_from_year :drink_until_year :confidence :reasoning]
      :additionalProperties false}}))

(def label-analysis-tool-name "record_wine_label")

(def label-analysis-tool
  (let [style-options (str/join ", " (sort common/wine-styles))
        designation-options (str/join ", " (sort common/wine-designations))
        format-options (str/join ", " common/bottle-formats)
        null-note
        "Return null when the label does not provide this information."]
    {:name label-analysis-tool-name
     :description
     "Structured schema matching wine-cellar.ai.prompts/label-analysis-system-prompt."
     :input_schema
     {:type "object"
      :properties
      {:producer {:type ["string" "null"]
                  :description (str "Producer or winery name. " null-note)}
       :name {:type ["string" "null"]
              :description (str
                            "Specific wine name if distinct from the producer. "
                            null-note)}
       :vintage {:type ["integer" "null"]
                 :description
                 (str "Vintage year as an integer, or null for non-vintage. "
                      null-note)}
       :country {:type ["string" "null"]
                 :description (str "Country of origin printed on the label. "
                                   null-note)}
       :region {:type ["string" "null"]
                :description
                (str (:region common/field-descriptions) " " null-note)}
       :appellation {:type ["string" "null"]
                     :description (str (:appellation common/field-descriptions)
                                       " "
                                       null-note)}
       :appellation_tier
       {:type ["string" "null"]
        :enum (conj (vec (sort common/appellation-tiers)) nil)
        :description
        (str (:appellation_tier common/field-descriptions) " " null-note)}
       :vineyard {:type ["string" "null"]
                  :description
                  (str (:vineyard common/field-descriptions) " " null-note)}
       :classification
       {:type ["string" "null"]
        :description
        (str (:classification common/field-descriptions) " " null-note)}
       :style {:type ["string" "null"]
               :description (str "Wine style wording; align with: "
                                 style-options
                                 ". " null-note)}
       :designation {:type ["string" "null"]
                     :description (str (:designation common/field-descriptions)
                                       " Must be one of: " designation-options
                                       ". " null-note)}
       :bottle_format {:type ["string" "null"]
                       :description (str "Bottle format/size. Must be one of: "
                                         format-options
                                         ". " null-note)}
       :alcohol_percentage {:type ["number" "null"]
                            :description
                            (str "Alcohol percentage as a number (e.g. 12.5). "
                                 null-note)}}
      :required [:producer :name :vintage :country :region :appellation
                 :appellation_tier :vineyard :classification :style :designation
                 :bottle_format :alcohol_percentage]
      :additionalProperties false}}))

(defn- temperature-supported?
  "Opus 4.8+ deprecates the temperature parameter and 400s when it is sent."
  [model-name]
  (not (and model-name (str/includes? model-name "opus-4-8"))))

(defn- build-request-body
  [{:keys [system messages tools tool_choice max_tokens temperature metadata
           stop_sequences]} model-override]
  (-> {:model model-override :max_tokens (or max_tokens 1000)}
      (cond-> system (assoc :system system))
      (assoc :messages messages)
      (cond-> (seq tools) (assoc :tools tools))
      (cond-> tool_choice (assoc :tool_choice tool_choice))
      (cond-> (and temperature (temperature-supported? model-override))
              (assoc :temperature temperature))
      (cond-> metadata (assoc :metadata metadata))
      (cond-> (seq stop_sequences) (assoc :stop_sequences stop_sequences))))

(defn- extract-text-content
  [content]
  (->> content
       (keep #(when (= "text" (:type %)) (:text %)))
       (remove str/blank?)
       (str/join "\n\n")))

(defn- parse-json-content
  [content]
  (if-let [tool-use (some #(when (= "tool_use" (:type %)) %) content)]
    (:input tool-use)
    (when-let [text (extract-text-content content)]
      (json/read-value text json/keyword-keys-object-mapper))))

(defn call-anthropic-api
  "Makes a request to the Anthropic API with messages array and optional JSON parsing"
  ([request] (call-anthropic-api request false))
  ([request parse-json?] (call-anthropic-api request parse-json? model))
  ([request parse-json? model-override]
   (let [request-body (build-request-body request model-override)]
     (tap> ["anthropic-request-body" request-body])
     (let [{:keys [status body error] :as response}
           (deref (http/post api-url
                             {:body (json/write-value-as-string request-body)
                              :headers {"x-api-key" api-key
                                        "anthropic-version" "2023-06-01"
                                        "content-type" "application/json"}
                              :as :text
                              :keepalive 60000
                              :timeout 60000}))
           parsed (when body
                    (json/read-value body json/keyword-keys-object-mapper))
           response-with-parsed (assoc response :parsed parsed)
           content (:content parsed)]
       (when error
         (tap> ["anthropic-request-error" error])
         (throw
          (ex-info "Anthropic API request failed" response-with-parsed error)))
       (when (not= 200 status)
         (tap> ["anthropic-request-non-200" {:status status :body parsed}])
         (throw (ex-info "Anthropic API request failed" response-with-parsed)))
       (if parse-json?
         (try (if-let [parsed-response (parse-json-content content)]
                (do (tap> ["anthropic-parsed-response" parsed-response])
                    parsed-response)
                (throw (ex-info "Anthropic response missing JSON payload"
                                response-with-parsed)))
              (catch Exception e
                (throw (ex-info "Failed to parse AI response as JSON"
                                response-with-parsed
                                e))))
         (let [text-content (extract-text-content content)]
           (if (seq (str text-content))
             text-content
             (throw (ex-info "Anthropic response missing assistant text"
                             response-with-parsed)))))))))

(defn suggest-drinking-window
  "Suggests an optimal drinking window for a wine using Anthropic's Claude API.
   Expects a map with :system and :user prompt strings."
  [{:keys [system user]}]
  (assert (string? system) "Drinking-window prompt requires :system text")
  (assert (string? user) "Drinking-window prompt requires :user text")
  (let [request {:system system
                 :messages [{:role "user" :content [{:type "text" :text user}]}]
                 :tools [drinking-window-tool]
                 :tool_choice {:type "tool" :name drinking-window-tool-name}
                 :max_tokens 600}]
    (call-anthropic-api request true)))

(defn analyze-wine-label
  "Analyzes wine label images using Anthropic's Claude API.
   Expects {:system string :user-content vector-of-content}."
  [{:keys [system user-content]}]
  (assert (string? system) "Label analysis prompt requires :system text")
  (assert (vector? user-content)
          "Label analysis prompt requires :user-content vector")
  (let [request {:system system
                 :messages [{:role "user" :content (vec user-content)}]
                 :tools [label-analysis-tool]
                 :tool_choice {:type "tool" :name label-analysis-tool-name}
                 :max_tokens 900}]
    (call-anthropic-api request true)))

(def spirit-label-analysis-tool-name "record_spirit_label")

(def spirit-label-analysis-tool
  (let [categories ["whiskey" "gin" "rum" "vodka" "tequila" "mezcal" "brandy"
                    "liqueur" "other"]
        null-note
        "Return null when the label does not provide this information."]
    {:name spirit-label-analysis-tool-name
     :description "Structured schema for spirit label extraction."
     :input_schema
     {:type "object"
      :properties
      {:name {:type ["string" "null"]
              :description (str "Full spirit name (brand + expression). "
                                null-note)}
       :category {:type ["string" "null"]
                  :enum (conj (vec categories) nil)
                  :description (str "Spirit type. Must be one of: "
                                    (str/join ", " categories)
                                    ". " null-note)}
       :subcategory {:type ["string" "null"]
                     :description
                     (str "More specific type (e.g. \"bourbon\", \"rye\", "
                          "\"single malt\", \"reposado\", \"amaro\"). "
                          null-note)}
       :distillery {:type ["string" "null"]
                    :description (str "Producer or distillery name. "
                                      null-note)}
       :country {:type ["string" "null"]
                 :description (str "Country of origin. " null-note)}
       :region {:type ["string" "null"]
                :description (str
                              "Region of production (e.g. Speyside, Jalisco). "
                              null-note)}
       :age_statement {:type ["string" "null"]
                       :description (str "Age statement text if present "
                                         "(e.g. \"12 Year\"). "
                                         null-note)}
       :proof {:type ["integer" "null"]
               :description (str "Proof value as an integer (e.g. 80). "
                                 null-note)}}
      :required [:name :category :subcategory :distillery :country :region
                 :age_statement :proof]
      :additionalProperties false}}))

(defn analyze-spirit-label
  "Analyzes spirit label images using Anthropic's Claude API.
   Expects {:system string :user-content vector-of-content}."
  [{:keys [system user-content]}]
  (assert (string? system) "Spirit label analysis prompt requires :system text")
  (assert (vector? user-content)
          "Spirit label analysis prompt requires :user-content vector")
  (let [request {:system system
                 :messages [{:role "user" :content (vec user-content)}]
                 :tools [spirit-label-analysis-tool]
                 :tool_choice {:type "tool"
                               :name spirit-label-analysis-tool-name}
                 :max_tokens 600}]
    (call-anthropic-api request true)))

(defn chat-about-wines
  "Chat with AI about wine collection and wine-related topics with conversation history.
   Expects {:system-text ... :context-text ... :messages [...]} prepared by ai.core."
  [{:keys [system-text context-text messages]}]
  (assert (string? system-text) "Chat prompt requires :system-text string")
  (assert (string? context-text) "Chat prompt requires :context-text string")
  (assert (vector? messages) "Chat prompt requires :messages vector")
  (let [request
        {:system
         [{:type "text" :text system-text :cache_control {:type "ephemeral"}}
          {:type "text" :text context-text :cache_control {:type "ephemeral"}}]
         :messages messages}]
    (call-anthropic-api request false)))

(defn generate-wine-summary
  "Generates a comprehensive wine summary including taste profile and food pairings using Anthropic's Claude API.
   Expects a map with :system and :user prompt strings."
  [{:keys [system user]}]
  (assert (string? system) "Wine-summary prompt requires :system text")
  (assert (string? user) "Wine-summary prompt requires :user text")
  (let [request {:system system :messages [{:role "user" :content user}]}]
    (call-anthropic-api request false)))

(defn generate-conversation-title
  "Create a concise conversation title using Anthropic's lightweight model."
  [{:keys [system user]}]
  {:pre [(string? system) (string? user)]}
  (let [request {:system system
                 :messages [{:role "user" :content [{:type "text" :text user}]}]
                 :max_tokens 40
                 :temperature 0.2}]
    (call-anthropic-api request false light-model)))

(defn generate-report-commentary
  "Generates a report commentary using Anthropic's Claude API."
  [{:keys [system user]}]
  (assert (string? system) "Report prompt requires :system text")
  (assert (string? user) "Report prompt requires :user text")
  (let [request {:system system
                 :messages [{:role "user" :content [{:type "text" :text user}]}]
                 :max_tokens 1000
                 :temperature 0.7}]
    (call-anthropic-api request false)))

(def extract-recipe-tool-name "save_cocktail_recipe")

;; Mirrors wine-cellar.views.bar.spirits/spirit-categories so spirit specs hold
;; even when the user has no spirits in inventory yet.
(def spirit-categories
  ["whiskey" "gin" "rum" "vodka" "tequila" "mezcal" "brandy" "liqueur"
   "vermouth" "other"])

(def extract-recipe-tool
  {:name extract-recipe-tool-name
   :description "Extracts structured cocktail recipe data from text."
   :input_schema
   {:type "object"
    :required ["recipes"]
    :properties
    {:recipes
     {:type "array"
      :items
      {:type "object"
       :required ["name" "ingredients"]
       :properties
       {:name {:type "string"}
        :description {:type "string"}
        :ingredients
        {:type "array"
         :items {:type "object"
                 :required ["name"]
                 :properties {:name {:type "string"}
                              :amount {:type "string"}
                              :unit {:type "string"}
                              :garnish {:type "boolean"}}}
         :description
         (str
          "One entry per ingredient. For name, prefer the generic spirit/"
          "ingredient term over a specific brand when it still makes sense in "
          "context (e.g. 'bourbon' rather than 'Buffalo Trace', 'London dry "
          "gin' rather than 'Tanqueray'); keep a brand only when the recipe "
          "truly depends on that specific bottle. "
          "Set garnish to true when the "
          "ingredient is used only as a garnish (a twist, peel, wheel, "
          "wedge, sprig, cherry, etc. — anything in a \"Garnish:\" line or "
          "marked \"to garnish\"); a missing garnish never blocks making the "
          "drink, so do not flag ingredients that are juiced, muddled, or "
          "otherwise mixed in.")}
        :instructions {:type "string"}
        :tags {:type "array"
               :maxItems 4
               :items {:type "string"}
               :description (str
                             "1-3 short, lowercase tags for filtering. "
                             "Choose only high-signal, reusable tags from "
                             "these categories: drink family (e.g. sour, "
                             "old-fashioned, negroni, martini, highball, "
                             "spritz, tiki, flip) and technique (shaken, "
                             "stirred, built). Do NOT include the base "
                             "spirit (gin, bourbon, rum, etc.) — spirits "
                             "are derived separately from the ingredients. "
                             "Do NOT include subjective descriptors "
                             "(elegant, refreshing, classic, simple), "
                             "ratios (2:1), individual ingredients (aperol, "
                             "egg white, simple syrup), or 'variation'/"
                             "'-style' qualifiers. Prefer the canonical "
                             "family name over a variant (use 'negroni', "
                             "not 'negroni variation').")}}}}}}})

(defn extract-cocktail-recipe
  "Extracts structured cocktail recipe data from a plain-text message. When
   existing-tags are supplied, nudges the model to reuse that vocabulary.
   Pure extraction — linking to the user's bar happens in a separate
   resolve-recipe-links phase."
  ([text] (extract-cocktail-recipe text nil))
  ([text existing-tags]
   {:pre [(string? text)]}
   (let [tag-hint
         (when (seq existing-tags)
           (str "\n\nTags already in use (reuse these exact strings when one "
                "applies; only add a new tag if none fit): "
                (str/join ", " existing-tags)))
         request {:messages [{:role "user"
                              :content (str "Extract all cocktail recipes from"
                                            " this text:\n\n"
                                            text
                                            tag-hint)}]
                  :tools [extract-recipe-tool]
                  :tool_choice {:type "tool" :name extract-recipe-tool-name}
                  :max_tokens 2000}]
     (call-anthropic-api request true))))

(def resolve-links-tool-name "resolve_recipe_links")

(def resolve-links-tool
  {:name resolve-links-tool-name
   :description
   "Resolve an existing cocktail recipe's ingredient and spirit links to the user's bar inventory by #id, keyed to the indices shown."
   :input_schema
   {:type "object"
    :properties
    {:ingredient_links
     {:type "array"
      :description
      (str
       "Exactly one entry per ingredient index shown. inventory_item_ids = "
       "the #ids of EVERY Mixers & Garnishes item that genuinely satisfies "
       "that ingredient — link all equivalents, not just one (e.g. both a "
       "demerara and a white sugar for \"sugar\", both lemon and lime for a "
       "\"citrus garnish\"). Use an empty array when nothing matches. Do not "
       "link the base spirit or spirituous modifiers here — those get "
       "spirit_links entries. Set garnish to true when the ingredient is used "
       "only as a "
       "garnish (a twist, peel, wheel, wedge, sprig, cherry, etc.), false "
       "when it is juiced, muddled, or otherwise mixed into the drink.")
      :items {:type "object"
              :required ["index" "inventory_item_ids" "garnish"]
              :properties {:index {:type "integer"}
                           :garnish {:type "boolean"}
                           :inventory_item_ids {:type "array"
                                                :items {:type "integer"}}}}}
     :spirit_links
     {:type "array"
      :description
      (str
       "One entry per ingredient line that is a spirit, spirituous modifier, "
       "or rinse/wash (the base spirit, vermouths, liqueurs, absinthe rinse, "
       "etc.) — keyed by that line's ingredient index. Do NOT include entries "
       "for non-spirituous lines (juices, syrups, bitters dashes, garnishes). "
       "spirit_id = set ONLY when the ingredient line itself names a "
       "specific brand or product (e.g. \"Beefeater\", \"Four Roses Small "
       "Batch\", \"Campari\") that matches a bottle the user owns. For a "
       "GENERIC spirit or style name — \"gin\", \"London Dry gin\", "
       "\"bourbon\", \"rye\", \"blanco tequila\", \"sweet vermouth\" — leave "
       "spirit_id null and rely on the category/subcategory match; do NOT "
       "pin it to whatever bottle the user happens to own in that style, and "
       "brands mentioned in the recipe text merely as options or "
       "suggestions do not count. null is the default. category/subcategory "
       "= the style the recipe calls for, judged from the ingredient name "
       "and recipe text. Set subcategory ONLY when the recipe actually "
       "specifies a style (\"London Dry gin\", \"blanco tequila\", "
       "\"rye\"); when interchangeable brands or styles are offered, there "
       "is no style requirement. The bar inventory below supplies only the "
       "exact spelling/capitalization of a subcategory, never a reason to "
       "add one. Use null for subcategory when the recipe doesn't call for "
       "a specific style.")
      :items {:type "object"
              :required ["ingredient_index" "spirit_id" "category"
                         "subcategory"]
              :properties {:ingredient_index {:type "integer"}
                           :spirit_id {:type ["integer" "null"]}
                           :category {:type "string" :enum spirit-categories}
                           :subcategory {:type ["string" "null"]}}}}}
    :required ["ingredient_links" "spirit_links"]
    :additionalProperties false}})

(defn resolve-recipe-links
  "Resolves a recipe's ingredient and spirit links to the user's bar by #id,
   keyed by ingredient index. Takes the recipe (:name :description
   :instructions :notes :ingredients — the ingredient NAME is the sole style
   signal; the rest is shown as context only) and bar
   (:spirits/:inventory-items). Returns a map of
   {:ingredient_links [{:index :inventory_item_ids :garnish}]
    :spirit_links [{:ingredient_index :spirit_id :category :subcategory}]}.
   Unlike re-extraction this never paraphrases the recipe — the model only
   reports ids against the indices we send, so the join back is exact."
  [{:keys [name description instructions notes ingredients]} bar]
  (let [ing-lines (str/join "\n"
                            (map-indexed (fn [i {:keys [name]}]
                                           (str i ". " name))
                                         ingredients))
        recipe-context
        (->> [(when (seq name) (str "Name: " name))
              (when (seq description) (str "Description: " description))
              (when (seq instructions) (str "Instructions: " instructions))
              (when (seq notes) (str "Notes: " notes))]
             (remove nil?)
             (str/join "\n"))
        bar-text (prompts/bar-context-text
                  (select-keys bar [:spirits :inventory-items]))
        content (str
                 "Resolve this cocktail recipe's links to the user's bar by "
                 "#id. Return one ingredient_links entry per ingredient index "
                 "shown, and one spirit_links entry per spirituous line.\n\n"
                 "Ingredients (index. name):\n"
                 ing-lines
                 (when (seq recipe-context)
                   (str "\n\nRecipe (context only — use it to judge what "
                        "style each ingredient calls for and which lines are "
                        "garnishes; brands mentioned here as options or "
                        "suggestions justify neither a spirit_id nor a "
                        "subcategory):\n" recipe-context))
                 "\n\n=== Bar Inventory ===\n"
                 bar-text)
        request {:messages [{:role "user" :content content}]
                 :tools [resolve-links-tool]
                 :tool_choice {:type "tool" :name resolve-links-tool-name}
                 :max_tokens 1500}]
    (call-anthropic-api request true)))
