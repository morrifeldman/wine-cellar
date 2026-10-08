(ns wine-cellar.views.components.ai-provider-toggle
  (:require [reagent.core :as r]
            [reagent-mui.icons.auto-awesome :refer [auto-awesome]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.tooltip :refer [tooltip]]
            [wine-cellar.common :as common]))

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

;; Cycles through the AI providers.
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
                  :fontSize "0.875rem"
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

(defn- next-effort
  [current]
  (let [levels common/ai-effort-levels
        idx (.indexOf levels current)]
    (get levels (mod (inc idx) (count levels)))))

(defn effort-toggle-button
  "Only Claude takes an effort setting, so the button hides for other providers."
  [app-state]
  (let [{:keys [provider effort]} (:ai @app-state)]
    (when (and (common/provider-supports? provider :effort) effort)
      [tooltip {:title "How hard Claude thinks before answering"}
       [button
        {:variant "outlined"
         :size "small"
         :on-click #(swap! app-state update-in [:ai :effort] next-effort)
         :sx {:textTransform "none"
              :fontSize "0.875rem"
              :px 1.5
              :py 0.25
              :borderColor "divider"
              :color "text.primary"
              :height "28px"
              :lineHeight 1.2}} (str "Effort: " effort)]])))

(defn ai-button
  "A button that asks the AI for something, followed by the provider toggle
   so the provider can be changed first. While `busy?` it shows a spinner
   and `busy-label`."
  [app-state
   {:keys [label busy-label busy? disabled? on-click variant]
    :or {variant "outlined"}}]
  [:<>
   [button
    {:variant variant
     :color "secondary"
     :size "small"
     :disabled (boolean (or busy? disabled?))
     :startIcon (when-not busy? (r/as-element [auto-awesome]))
     :onClick on-click}
    (if busy?
      [box {:sx {:display "flex" :alignItems "center"}}
       [circular-progress {:size 18 :sx {:mr 1}}] busy-label]
      label)]
   [provider-toggle-button app-state
    {:mobile-min-width "auto" :sx {:minWidth "auto" :px 1 :py 0.25}}]])
