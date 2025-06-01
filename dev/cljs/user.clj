(ns user
  (:require [portal.api :as p]))

(defonce portal (p/open))
(add-tap #'p/submit)

;; Helper functions for portal

;; Helper functions for portal
(defn clear-portal [] (p/clear))

(defn close-portal [] (p/close))

(comment
  ;; Example usage:
  (tap> {:hello "world"})
  (tap> (range 10))
  ;; Clear the portal display
  (clear-portal)
  ;; Close the portal window
  (close-portal)
  ;; Reopen if needed
  (def portal (p/open))
  (add-tap #'p/submit)
  ;; For ClojureScript usage, you'll need to connect to the CLJS runtime
  ;; This happens automatically when using shadow-cljs with portal
)
