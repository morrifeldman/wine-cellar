(ns wine-cellar.utils.stats
  (:require [wine-cellar.summary :as summary]))

(defn collection-stats
  ([wines]
   (summary/collection-stats wines))
  ([wines options]
   (summary/collection-stats wines options)))
