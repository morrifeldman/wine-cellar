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
          (let [provider (config-utils/ai-default-provider)]
            (when-not (common/ai-providers provider)
              (throw (ex-info (str "Invalid AI_DEFAULT_PROVIDER: " provider
                                   ". Must be one of: " common/ai-providers)
                              {:provider provider
                               :valid-providers common/ai-providers})))
            provider))

;; What each provider namespace offers, by task. Vars rather than values so
;; a redefined provider function (in a test) is the one that runs.
(def ^:private tasks
  {:anthropic {:chat #'anthropic/chat-about-wines
               :drinking-window #'anthropic/suggest-drinking-window
               :wine-summary #'anthropic/generate-wine-summary
               :wine-label #'anthropic/analyze-wine-label
               :spirit-label #'anthropic/analyze-spirit-label
               :conversation-title #'anthropic/generate-conversation-title
               :report-commentary #'anthropic/generate-report-commentary}
   :openai {:chat #'openai/chat-about-wines
            :drinking-window #'openai/suggest-drinking-window
            :wine-summary #'openai/generate-wine-summary
            :wine-label #'openai/analyze-wine-label
            :spirit-label #'openai/analyze-spirit-label
            :conversation-title #'openai/generate-conversation-title
            :report-commentary #'openai/generate-report-commentary}
   :gemini {:chat #'gemini/chat-about-wines
            :drinking-window #'gemini/suggest-drinking-window
            :wine-summary #'gemini/generate-wine-summary
            :wine-label #'gemini/analyze-wine-label
            :spirit-label #'gemini/analyze-spirit-label
            :conversation-title #'gemini/generate-conversation-title
            :report-commentary #'gemini/generate-report-commentary}})

(defn- run-task
  "Runs task on provider (the default when nil). An unknown provider is the
  caller's mistake, so a 400 rather than a 500."
  [provider task prompt]
  (let [provider (or provider default-provider)
        f (get-in tasks [provider task])]
    (when-not f
      (throw (ex-info (str "Unknown AI provider: " provider)
                      {:status 400
                       :error (str "Unknown AI provider: " provider)})))
    (f prompt)))

(defn wines-context-text
  "The details of these wines as chat context, rendered from the database."
  [wine-ids]
  (some-> (seq wine-ids)
          db-api/get-enriched-wines-by-ids
          prompts/selected-wines-context))

(defn chat-about-wines
  [provider context conversation-history image]
  {:pre [(map? context)]}
  (let [{:keys [summary web-content bar chat-mode effort]} context
        bar-mode? (= :bar chat-mode)
        prompt
        {:system-text (if bar-mode?
                        (prompts/bar-system-instructions)
                        (prompts/wine-system-instructions))
         :context-text (if bar-mode?
                         (prompts/bar-chat-context context)
                         (prompts/wine-collection-context
                          {:summary summary :web-content web-content :bar bar}))
         :messages (prompts/conversation-messages conversation-history image)
         :effort effort}]
    (run-task provider :chat prompt)))

(defn suggest-drinking-window
  [provider wine]
  (let [prompt-data {:system (prompts/drinking-window-system-prompt)
                     :user (prompts/drinking-window-user-message wine)}]
    (run-task provider :drinking-window prompt-data)))

(defn generate-wine-summary
  [provider wine]
  (let [prompt-data {:system (prompts/wine-summary-system-prompt)
                     :user (prompts/wine-summary-user-message wine)}]
    (run-task provider :wine-summary prompt-data)))

(defn analyze-wine-label
  [provider front-image back-image & [classifications]]
  (let [system-prompt (prompts/label-analysis-system-prompt classifications)
        user-content (prompts/label-analysis-user-content front-image
                                                          back-image)
        prompt {:system system-prompt :user-content user-content}]
    (run-task provider :wine-label prompt)))

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
    (run-task provider :spirit-label prompt)))

(defn generate-conversation-title
  [provider first-message]
  (let [prompt (prompts/conversation-title-prompt first-message)
        raw (run-task provider :conversation-title prompt)
        title (some-> raw
                      str
                      str/trim
                      (str/replace #"\s+" " "))]
    (when-not (str/blank? title) title)))

(defn generate-report-commentary
  [provider report-data]
  (let [prompt {:system (prompts/report-system-prompt)
                :user (prompts/report-user-message report-data)}]
    (run-task provider :report-commentary prompt)))

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

(defn extract-recipe-timers
  "Returns the timed steps in a recipe's instructions, or nil on failure.
   Always uses Anthropic."
  [instructions]
  (try (anthropic/extract-recipe-timers instructions)
       (catch Exception e (tap> ["❌ extract-recipe-timers failed" e]) nil)))

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
   :small-models {:anthropic anthropic/small-model
                  :openai openai/small-model
                  :gemini gemini/small-model}
   :default-provider default-provider
   :default-effort anthropic/default-effort})

