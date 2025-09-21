(ns wine-cellar.ai.core
  "Provider dispatch layer for AI functionality."
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [wine-cellar.ai.anthropic :as anthropic]
            [wine-cellar.ai.openai :as openai]
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
   (let [provider-key (normalize-provider provider)]
     (case provider-key
       :openai (openai/chat-about-wines wines conversation-history image)
       :anthropic (anthropic/chat-about-wines wines conversation-history image)
       (throw (ex-info "Unsupported AI provider" {:provider provider-key}))))))

;; TODO: add provider-aware wrappers for the non-chat Anthropic helpers as we extend
;; OpenAI support. For now other call sites continue to require anthropic directly.
