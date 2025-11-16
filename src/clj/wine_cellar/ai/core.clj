(ns wine-cellar.ai.core
  "Provider dispatch layer for AI functionality."
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [wine-cellar.ai.anthropic :as anthropic]
            [wine-cellar.ai.openai :as openai]
            [wine-cellar.ai.prompts :as prompts]
            [wine-cellar.common :as common]
            [wine-cellar.config-utils :as config-utils]))

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
  {:pre [(map? context) (contains? context :summary)]}
  (let [{:keys [summary selected-wines]} context
        prompt
        {:system-text (prompts/wine-system-instructions)
         :context-text (prompts/wine-collection-context
                        {:summary summary :selected-wines selected-wines})
         :messages (prompts/conversation-messages conversation-history image)}]
    (case provider
      :openai (openai/chat-about-wines prompt)
      :anthropic (anthropic/chat-about-wines prompt))))

(defn suggest-drinking-window
  [provider wine]
  (let [prompt-data {:system (prompts/drinking-window-system-prompt)
                     :user (prompts/drinking-window-user-message wine)}]
    (case provider
      :openai (openai/suggest-drinking-window prompt-data)
      :anthropic (anthropic/suggest-drinking-window prompt-data))))

(defn generate-wine-summary
  [provider wine]
  (let [prompt-data {:system (prompts/wine-summary-system-prompt)
                     :user (prompts/wine-summary-user-message wine)}]
    (case provider
      :openai (openai/generate-wine-summary prompt-data)
      :anthropic (anthropic/generate-wine-summary prompt-data))))

(defn analyze-wine-label
  [provider front-image back-image]
  (let [prompt (prompts/label-analysis-prompt front-image back-image)]
    (case provider
      :openai (openai/analyze-wine-label prompt)
      :anthropic (anthropic/analyze-wine-label prompt))))

(defn generate-conversation-title
  [provider first-message]
  (let [prompt (prompts/conversation-title-prompt first-message)
        raw (case provider
              :openai (openai/generate-conversation-title prompt)
              :anthropic (anthropic/generate-conversation-title prompt)
              nil)
        title (some-> raw
                      str
                      str/trim
                      (str/replace #"\s+" " "))]
    (when-not (str/blank? title) title)))

(defn get-model-info
  "Returns current model configuration for each provider and the default provider"
  []
  {:models {:anthropic anthropic/model :openai openai/model}
   :default-provider default-provider})

;; TODO: add provider-aware wrappers for any remaining Anthropics-only helpers
;; as we
;; extend OpenAI support.
