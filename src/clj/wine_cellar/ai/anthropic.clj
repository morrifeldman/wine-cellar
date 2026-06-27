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

;; Mirrors wine-cellar.views.bar.spirits/spirit-categories so spirit_tags hold
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
    {:recipes {:type "array"
               :items
               {:type "object"
                :required ["name" "ingredients"]
                :properties
                {:name {:type "string"}
                 :description {:type "string"}
                 :ingredients {:type "array"
                               :items {:type "object"
                                       :required ["name"]
                                       :properties {:name {:type "string"}
                                                    :amount {:type "string"}
                                                    :unit {:type "string"}}}}
                 :instructions {:type "string"}
                 :spirit_tags
                 {:type "array"
                  :items {:type "object"
                          :required ["category"]
                          :properties {:category {:type "string"
                                                  :enum spirit-categories}
                                       :subcategory {:type "string"}}}
                  :description
                  (str "One entry per spirit/modifier ingredient — the base "
                       "spirit, any spirituous modifiers, and any rinse/wash "
                       "(e.g. absinthe rinse). category MUST be one of the "
                       "listed values. Include subcategory only when it "
                       "matches a subcategory shown in the bar inventory "
                       "(e.g. {category: liqueur, subcategory: bitter} for "
                       "Campari, {category: other, subcategory: Absinthe}). "
                       "Reuse the inventory's exact subcategory strings, "
                       "matching their capitalization.")}
                 :tags {:type "array"
                        :maxItems 4
                        :items {:type "string"}
                        :description
                        (str "1-3 short, lowercase tags for filtering. "
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
   existing-tags are supplied, nudges the model to reuse that vocabulary. When
   bar (a map of :spirits/:inventory-items) is supplied, feeds the user's real
   inventory so the model can assign accurate spirit_tags and normalize
   ingredient/garnish names to the existing vocabulary."
  ([text] (extract-cocktail-recipe text nil nil))
  ([text existing-tags] (extract-cocktail-recipe text existing-tags nil))
  ([text existing-tags bar]
   {:pre [(string? text)]}
   (let [tag-hint
         (when (seq existing-tags)
           (str "\n\nTags already in use (reuse these exact strings when one "
                "applies; only add a new tag if none fit): "
                (str/join ", " existing-tags)))
         bar-text (prompts/bar-context-text bar)
         bar-hint
         (when bar-text
           (str "\n\nThe user's bar inventory is below. Use it to (1) assign "
                "spirit_tags by reusing the exact categories and subcategory "
                "strings shown (match the subcategory capitalization), and "
                "(2) normalize ingredient and garnish "
                "names to match the Mixers & Garnishes names when an item "
                "clearly corresponds (e.g. prefer 'lime juice', 'Angostura "
                "bitters'); otherwise keep the recipe's own wording.\n\n"
                "=== Bar Inventory ===\n" bar-text))
         request {:messages [{:role "user"
                              :content (str "Extract all cocktail recipes from"
                                            " this text:\n\n"
                                            text
                                            tag-hint
                                            bar-hint)}]
                  :tools [extract-recipe-tool]
                  :tool_choice {:type "tool" :name extract-recipe-tool-name}
                  :max_tokens 2000}]
     (call-anthropic-api request true))))
