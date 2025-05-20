(ns wine-cellar.debug
  (:require [portal.web :as p]
            [reagent.core :as r]))

;; State to track if debugging is active
(defonce debug-state (r/atom {:active? false :tap-fn nil}))

;; Start debugging with Portal
(defn start-debugging!
  []
  (when-not (:active? @debug-state)
    ;; Open portal - no need to store the reference
    (p/open {:launcher :web})
    ;; Create a tap function that correctly calls submit
    (let [tap-fn (fn [value] (p/submit value))]
      ;; Store tap function
      (swap! debug-state assoc :active? true :tap-fn tap-fn)
      ;; Add tap handler
      (add-tap tap-fn)
      ;; Return true to indicate success
      true)))

;; Stop debugging
(defn stop-debugging!
  []
  (when (:active? @debug-state)
    ;; Remove our tap handler
    (when-let [tap-fn (:tap-fn @debug-state)] (remove-tap tap-fn))
    ;; Close portal - no arguments needed
    (p/close)
    ;; Reset state
    (swap! debug-state assoc :active? false :tap-fn nil)
    ;; Return true to indicate success
    true))

;; Toggle debugging state
(defn toggle-debugging!
  []
  (if (:active? @debug-state) (stop-debugging!) (start-debugging!)))

;; Component for debug button
(defn debug-button
  []
  [:button.debug-toggle
   {:on-click toggle-debugging!
    :style {:position "fixed"
            :bottom "10px"
            :right "10px"
            :z-index 9999
            :padding "8px 12px"
            :background (if (:active? @debug-state) "#ff4136" "#2ecc40")
            :color "white"
            :border "none"
            :border-radius "4px"
            :cursor "pointer"}}
   (if (:active? @debug-state) "Stop Debugging" "Start Debugging")])
