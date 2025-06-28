(ns wine-cellar.portal-debug
  (:require [portal.web :as p]
            [reagent.core :as r]))

;; State to track if debugging is active
(defonce debug-state (r/atom {:active? false :tap-fn nil}))

;; Start debugging with Portal
(defn- start-debugging!
  []
  (let [tap-fn (fn [value] (p/submit value))]
    (p/open)
    (add-tap tap-fn)
    (swap! debug-state assoc :active? true :tap-fn tap-fn)
    (tap> {:hello "from clojure frontend"})))

(defn unwatch-app-state [app-state] (remove-watch app-state :debug-tap))

;; Stop debugging
(defn- stop-debugging!
  [app-state]
  (js/console.log "Stopping portal debugging")
  (when-let [tap-fn (:tap-fn @debug-state)]
    (js/console.log "Removing tap function")
    (remove-tap tap-fn))
  (unwatch-app-state app-state)
  (p/close)
  (swap! debug-state assoc :active? false :tap-fn nil))

(defn debugging? [] (boolean (:active? @debug-state)))

(defn toggle-debugging!
  [app-state]
  (if (debugging?) (stop-debugging! app-state) (start-debugging!)))

(defn reconnect-if-needed [] (when (debugging?) (p/open)))

(defn watch-app-state
  [app-state]
  (add-watch app-state
             :debug-tap
             (fn [_ _ _ new-state] (tap> ["app-state-changed" new-state]))))
