(ns wine-cellar.portal-debug
  (:require [portal.web :as p]
            [reagent.core :as r]))

;; State to track if debugging is active
(defonce debug-state (r/atom {:active? false :tap-fn nil}))

;; Start debugging with Portal
(defn- start-debugging!
  [app-state]
  (p/open)
  (let [tap-fn (fn [value] (p/submit value))]
    (swap! debug-state assoc :active? true :tap-fn tap-fn)
    (add-tap tap-fn)
    ;; Add app-state watch for debugging
    (add-watch app-state
               :debug-tap
               (fn [_ _ _ new-state] (tap> ["app-state-changed" new-state])))))

;; Stop debugging
(defn- stop-debugging!
  [app-state]
  (when-let [tap-fn (:tap-fn @debug-state)] (remove-tap tap-fn))
  ;; Remove app-state watch
  (remove-watch app-state :debug-tap)
  (p/close)
  (swap! debug-state assoc :active? false :tap-fn nil))

(defn toggle-debugging!
  [app-state]
  (if (:active? @debug-state)
    (stop-debugging! app-state)
    (start-debugging! app-state)))
