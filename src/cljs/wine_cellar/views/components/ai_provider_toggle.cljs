(ns wine-cellar.views.components.ai-provider-toggle
  (:require [clojure.string :as string]
            [reagent-mui.material.button :refer [button]]))

(defn normalize-provider
  [value]
  (cond
    (keyword? value) value
    (string? value) (-> value string/lower-case keyword)
    (nil? value) :anthropic
    :else :anthropic))

(defn provider-label
  [provider]
  (case (normalize-provider provider)
    :openai "ChatGPT"
    :anthropic "Claude"
    (-> provider name string/capitalize)))

(defn toggle-provider!
  [app-state]
  (swap! app-state update-in [:chat :provider]
         (fn [current]
           (let [provider (normalize-provider current)]
             (case provider
               :anthropic :openai
               :openai :anthropic
               :anthropic)))))

(defn- mobile?
  []
  (boolean (and (exists? js/navigator)
                (pos? (or (.-maxTouchPoints js/navigator) 0)))))

;; Reusable toggle to flip between Anthropic and OpenAI without duplicating logic.
(defn provider-toggle-button
  ([app-state]
   (provider-toggle-button app-state {}))
  ([app-state {:keys [size variant sx label label-fn title mobile-min-width]
               :or {size "small"
                    variant "outlined"
                    title "Toggle AI provider"}}]
   (let [provider (normalize-provider (get-in @app-state [:chat :provider]))
         provider-name (provider-label provider)
         display-label (cond
                         (some? label) label
                         (fn? label-fn) (label-fn provider provider-name)
                         :else (str "AI: " provider-name))
         base-sx {:textTransform "none"
                  :fontSize "0.75rem"
                  :px 1.5
                  :py 0.25
                  :borderColor "divider"
                  :color "text.primary"
                  :minWidth (or mobile-min-width (if (mobile?) "96px" "120px"))
                  :height "28px"
                  :lineHeight 1.2}]
     [button
      {:variant variant
       :size size
       :on-click #(toggle-provider! app-state)
       :sx (merge base-sx sx)
       :title title}
      display-label])))
