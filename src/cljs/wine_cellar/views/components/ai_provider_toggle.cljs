(ns wine-cellar.views.components.ai-provider-toggle
  (:require [wine-cellar.common :as common]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.tooltip :refer [tooltip]]))

(defn toggle-provider!
  [app-state]
  (swap! app-state update-in
    [:ai :provider]
    (fn [current]
      (let [providers (vec common/ai-providers)
            current-idx (.indexOf providers current)
            next-idx (mod (inc current-idx) (count providers))]
        (get providers next-idx)))))

(defn- mobile?
  []
  (boolean (and (exists? js/navigator)
                (pos? (or (.-maxTouchPoints js/navigator) 0)))))

;; Reusable toggle to flip between Anthropic and OpenAI without duplicating
;; logic.
(defn provider-toggle-button
  ([app-state] (provider-toggle-button app-state {}))
  ([app-state
    {:keys [size variant sx label label-fn title mobile-min-width]
     :or {size "small" variant "outlined" title "Toggle AI provider"}}]
   (let [provider (get-in @app-state [:ai :provider])
         provider-name (common/provider-label provider)
         display-label (cond (some? label) label
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
     [tooltip {:title title}
      [button
       {:variant variant
        :size size
        :on-click #(toggle-provider! app-state)
        :sx (merge base-sx sx)} display-label]])))
