(ns wine-cellar.ai.core
  "Provider dispatch layer for AI functionality."
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [wine-cellar.ai.anthropic :as anthropic]
            [wine-cellar.ai.gemini :as gemini]
            [wine-cellar.ai.openai :as openai]
            [wine-cellar.ai.prompts :as prompts]
            [wine-cellar.common :as common]
            [wine-cellar.config-utils :as config-utils]
            [wine-cellar.db.api :as db-api]))

(defstate default-provider
          :start
          (let [provider (keyword (config-utils/get-config "AI_DEFAULT_PROVIDER"
                                                           :fallback
                                                           "anthropic"))]
            (when-not (common/ai-providers provider)
              (throw (ex-info (str "Invalid AI_DEFAULT_PROVIDER: " provider
                                   ". Must be one of: " common/ai-providers)
                              {:provider provider
                               :valid-providers common/ai-providers})))
            provider))

(defn chat-about-wines
  [provider context conversation-history image]
  {:pre [(map? context)]}
  (let [{:keys [summary selected-wines web-content bar chat-mode]} context
        bar-mode? (= :bar chat-mode)
        prompt {:system-text (if bar-mode?
                               (prompts/bar-system-instructions)
                               (prompts/wine-system-instructions))
                :context-text (if bar-mode?
                                (prompts/bar-chat-context context)
                                (prompts/wine-collection-context
                                 {:summary summary
                                  :selected-wines selected-wines
                                  :web-content web-content
                                  :bar bar}))
                :messages (prompts/conversation-messages conversation-history
                                                         image)}]
    (case provider
      :openai (openai/chat-about-wines prompt)
      :anthropic (anthropic/chat-about-wines prompt)
      :gemini (gemini/chat-about-wines prompt))))

(defn suggest-drinking-window
  [provider wine]
  (let [prompt-data {:system (prompts/drinking-window-system-prompt)
                     :user (prompts/drinking-window-user-message wine)}]
    (case provider
      :openai (openai/suggest-drinking-window prompt-data)
      :anthropic (anthropic/suggest-drinking-window prompt-data)
      :gemini (gemini/suggest-drinking-window prompt-data))))

(defn generate-wine-summary
  [provider wine]
  (let [prompt-data {:system (prompts/wine-summary-system-prompt)
                     :user (prompts/wine-summary-user-message wine)}]
    (case provider
      :openai (openai/generate-wine-summary prompt-data)
      :anthropic (anthropic/generate-wine-summary prompt-data)
      :gemini (gemini/generate-wine-summary prompt-data))))

(defn analyze-wine-label
  [provider front-image back-image & [classifications]]
  (let [system-prompt (prompts/label-analysis-system-prompt classifications)
        user-content (prompts/label-analysis-user-content front-image
                                                          back-image)
        prompt {:system system-prompt :user-content user-content}]
    (case provider
      :openai (openai/analyze-wine-label prompt)
      :anthropic (anthropic/analyze-wine-label prompt)
      :gemini (gemini/analyze-wine-label prompt))))

(defn analyze-spirit-label
  [provider front-image]
  (let [subcategory-tree (->> (db-api/get-spirits)
                              (filter :subcategory)
                              (group-by :category)
                              (map (fn [[category spirits]] [category
                                                             (distinct
                                                              (map :subcategory
                                                                   spirits))]))
                              (into {}))
        system-prompt (prompts/spirit-label-analysis-system-prompt
                       subcategory-tree)
        user-content (prompts/label-analysis-user-content front-image nil)
        prompt {:system system-prompt :user-content user-content}]
    (case provider
      :openai (openai/analyze-spirit-label prompt)
      :anthropic (anthropic/analyze-spirit-label prompt)
      :gemini (gemini/analyze-spirit-label prompt))))

(defn generate-conversation-title
  [provider first-message]
  (let [prompt (prompts/conversation-title-prompt first-message)
        raw (case provider
              :openai (openai/generate-conversation-title prompt)
              :anthropic (anthropic/generate-conversation-title prompt)
              :gemini (gemini/generate-conversation-title prompt)
              nil)
        title (some-> raw
                      str
                      str/trim
                      (str/replace #"\s+" " "))]
    (when-not (str/blank? title) title)))

(defn generate-report-commentary
  [provider report-data]
  (let [prompt {:system (prompts/report-system-prompt)
                :user (prompts/report-user-message report-data)}]
    (case provider
      :openai (openai/generate-report-commentary prompt)
      :anthropic (anthropic/generate-report-commentary prompt)
      :gemini (gemini/generate-report-commentary prompt))))

(defn extract-cocktail-recipe
  "Extracts structured cocktail recipe data from text and/or an image (base64
   data URL). Always uses Anthropic. existing-tags nudges the model to reuse
   the current tag vocabulary. Pure extraction — bar linking happens
   separately via resolve-recipe-links."
  ([text] (extract-cocktail-recipe text nil))
  ([text existing-tags] (extract-cocktail-recipe text existing-tags nil))
  ([text existing-tags image]
   (try (anthropic/extract-cocktail-recipe text existing-tags image)
        (catch Exception e (tap> ["❌ extract-cocktail-recipe failed" e]) nil))))

(defn resolve-recipe-links
  "Resolves a recipe's ingredient/spirit links to the bar by #id, keyed by
   index. Always uses Anthropic. Returns {:ingredient_links [...]
   :spirit_links [...]} or nil on failure."
  [recipe bar]
  (try (anthropic/resolve-recipe-links recipe bar)
       (catch Exception e (tap> ["❌ resolve-recipe-links failed" e]) nil)))

(defn get-model-info
  "Returns current model configuration for each provider and the default provider"
  []
  {:models
   {:anthropic anthropic/model :openai openai/model :gemini gemini/model}
   :light-models {:anthropic anthropic/light-model
                  :openai openai/light-model
                  :gemini gemini/light-model}
   :default-provider default-provider})

;; TODO: add provider-aware wrappers for any remaining Anthropics-only helpers
;; as we
;; extend OpenAI support.
