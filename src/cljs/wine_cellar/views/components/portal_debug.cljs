(ns wine-cellar.views.components.portal-debug
  (:require [wine-cellar.portal-debug :as pd]))

;; Component for debug button
(defn debug-button
  []
  [:button.debug-toggle
   {:on-click pd/toggle-debugging!
    :style {:position "fixed"
            :bottom "10px"
            :right "10px"
            :z-index 9999
            :padding "8px 12px"
            :background (if (:active? @pd/debug-state) "#ff4136" "#2ecc40")
            :color "white"
            :border "none"
            :border-radius "4px"
            :cursor "pointer"}}
   (if (:active? @pd/debug-state) "Stop Debugging" "Start Debugging")])
