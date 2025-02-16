(ns wine-cellar.core
  (:require [reagent.dom.client :as rdc]
            [wine-cellar.views :as views]))

(defn ^:export init []
  (let [root (rdc/create-root (js/document.getElementById "app"))]
    (rdc/render root [views/app])))