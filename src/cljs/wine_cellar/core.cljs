(ns wine-cellar.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ["react-dom/client" :refer [createRoot]]
            [wine-cellar.views :as views]
            [wine-cellar.api :as api]))

;; State
(def initial-state {:wines []
                    :loading? true
                    :error nil
                    :new-wine {:name ""
                               :vintage 2020
                               :type ""
                               :location ""
                               :quantity 1
                               :price 0.0}})

(defonce app-state (r/atom initial-state))
(defonce root (atom nil))

;; Initialize app
(defn init []
  (js/console.log "Initializing app...")
  (api/fetch-wines app-state)
  (when-let [container (.getElementById js/document "app")]
    (when (nil? @root)
      (reset! root (createRoot container)))
    (.render @root (r/as-element [views/main-app app-state]))))

;; Start the app when loaded
(defn ^:export main []
  (init))
