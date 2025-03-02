(ns wine-cellar.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ["react-dom/client" :refer [createRoot]]
            [wine-cellar.views :as views]
            [wine-cellar.api :as api]))

(defonce app-state
  (r/atom {:wines []
           :classifications []
           :new-wine {}
           :error nil
           :loading? false
           :selected-wine-id nil
           :tasting-notes []
           :new-tasting-note {}
           :sort {:field nil :direction :asc}
           :filters {:search "" :country nil :region nil :styles nil}}))

#_(prn @app-state)

(defonce root (atom nil))

;; Initialize app
(defn init []
  (js/console.log "Initializing app...")
  (api/fetch-wines app-state)
  (api/fetch-classifications app-state)  ;; Load classifications at startup
  (when-let [container (.getElementById js/document "app")]
    (when (nil? @root)
      (reset! root (createRoot container)))
    (.render @root (r/as-element [views/main-app app-state]))))

;; Start the app when loaded
(defn ^:export main []
  (init))
