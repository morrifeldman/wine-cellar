(ns wine-cellar.portal-debug
  (:require [portal.web :as p]
            [reagent.core :as r]))

;; State to track if debugging is active
(defonce debug-state (r/atom {:active? false :tap-fn nil}))

#_(p/clear)
;; Start debugging with Portal
(defn- start-debugging!
  [app-state]
  (let [tap-fn (fn [value] (p/submit value))]
    (p/open)
    (add-tap tap-fn)
    (swap! debug-state assoc :active? true :tap-fn tap-fn)
    (tap> {:hello "from clojure frontend"})
    (tap> app-state) ;; Can watch it from the portal UI
  ))

;; Stop debugging
(defn- stop-debugging!
  [app-state]
  (js/console.log "Stopping portal debugging")
  (when-let [tap-fn (:tap-fn @debug-state)]
    (js/console.log "Removing tap function")
    (remove-tap tap-fn))
  ;; Remove app-state watch
  (remove-watch app-state :debug-tap)
  (p/close)
  (swap! debug-state assoc :active? false :tap-fn nil))

(defn toggle-debugging!
  [app-state]
  (if (:active? @debug-state)
    (stop-debugging! app-state)
    (start-debugging! app-state)))

(defn open-portal [] (p/open))
