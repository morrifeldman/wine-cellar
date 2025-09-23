(ns wine-cellar.ai.core
  "Provider dispatch layer for AI functionality."
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [wine-cellar.ai.anthropic :as anthropic]
            [wine-cellar.ai.openai :as openai]
            [wine-cellar.ai.prompts :as prompts]
            [wine-cellar.config-utils :as config-utils]))

(def supported-providers #{:anthropic :openai})

(defstate default-provider
  :start
  (let [configured (some-> (config-utils/get-config
                             "AI_DEFAULT_PROVIDER"
                             :fallback "anthropic")
                           str/lower-case
                           keyword)]
    (if (supported-providers configured)
      configured
      :anthropic)))

(defn normalize-provider
  [provider]
  (cond
    (keyword? provider) provider
    (string? provider) (-> provider str/lower-case keyword)
    (nil? provider) default-provider
    :else (throw (ex-info "Unknown AI provider type" {:provider provider}))))

(defn chat-about-wines
  ([wines conversation-history]
   (chat-about-wines nil wines conversation-history nil))
  ([provider wines conversation-history]
   (chat-about-wines provider wines conversation-history nil))
  ([provider wines conversation-history image]
   (tap> ["conversation-history" conversation-history])
   (let [provider-key (normalize-provider provider)
         prompt {:system-text (prompts/wine-system-instructions)
                 :context-text (prompts/wine-collection-context wines)
                 :messages (prompts/conversation-messages conversation-history image)}]
     (tap> ["prompt" prompt])
     (case provider-key
       :openai (openai/chat-about-wines prompt)
       :anthropic (anthropic/chat-about-wines prompt)
       (throw (ex-info "Unsupported AI provider" {:provider provider-key}))))))

(defn suggest-drinking-window
  ([wine]
   (suggest-drinking-window nil wine))
  ([provider wine]
   (let [provider-key (normalize-provider provider)
         prompt-data {:system (prompts/drinking-window-system-prompt)
                      :user (prompts/drinking-window-user-message wine)}]
     (case provider-key
       :openai (openai/suggest-drinking-window prompt-data)
       :anthropic (anthropic/suggest-drinking-window prompt-data)
       (throw (ex-info "Unsupported AI provider" {:provider provider-key}))))))

(defn generate-wine-summary
  ([wine]
   (generate-wine-summary nil wine))
  ([provider wine]
   (let [provider-key (normalize-provider provider)
         prompt-data {:system (prompts/wine-summary-system-prompt)
                      :user (prompts/wine-summary-user-message wine)}]
     (case provider-key
       :openai (openai/generate-wine-summary prompt-data)
       :anthropic (anthropic/generate-wine-summary prompt-data)
       (throw (ex-info "Unsupported AI provider" {:provider provider-key}))))))

(defn analyze-wine-label
  ([front-image back-image]
   (analyze-wine-label nil front-image back-image))
  ([provider front-image back-image]
   (let [provider-key (normalize-provider provider)
         prompt (prompts/label-analysis-prompt front-image back-image)]
     (case provider-key
       :openai (openai/analyze-wine-label prompt)
       :anthropic (anthropic/analyze-wine-label prompt)
       (throw (ex-info "Unsupported AI provider" {:provider provider-key}))))))

;; TODO: add provider-aware wrappers for any remaining Anthropics-only helpers as we
;; extend OpenAI support.
