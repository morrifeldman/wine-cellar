(ns wine-cellar.portal-debug
  (:require [portal.web :as p]
            [reagent.core :as r]))

;; State to track if debugging is active
(defonce debug-state (r/atom {:active? false :tap-fn nil}))

;; Start debugging with Portal
(defn- start-debugging!
  []
  (p/open {:launcher :web})
  (let [tap-fn (fn [value] (p/submit value))]
    (swap! debug-state assoc :active? true :tap-fn tap-fn)
    (add-tap tap-fn)))

;; Stop debugging
(defn- stop-debugging!
  []
  (when-let [tap-fn (:tap-fn @debug-state)] (remove-tap tap-fn))
  (p/close)
  (swap! debug-state assoc :active? false :tap-fn nil))

(defn toggle-debugging!
  []
  (if (:active? @debug-state) (stop-debugging!) (start-debugging!)))
