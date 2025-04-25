(ns wine-cellar.core
  (:require [reagent.core :as r]
            ["react-dom/client" :refer [createRoot]]
            [wine-cellar.views.main :as views]
            [wine-cellar.api :as api]
            [wine-cellar.theme :refer [wine-theme]]
            [reagent-mui.styles :refer [theme-provider]]))

(defonce app-state
  (r/atom {:wines []
           :classifications []
           :new-wine {}
           :error nil
           :success nil
           :loading? false
           :selected-wine-id nil
           :show-out-of-stock? false
           :show-wine-form? false
           :show-stats? false
           :show-filters? false
           :tasting-notes []
           :new-tasting-note {}
           :sort {:field nil :direction :asc}
           :filters {:search "" :country nil :region nil :style nil}
           :view nil}))

(add-watch app-state :tap (fn [_ _ _ new-state] (tap> new-state)))

(defonce root (atom nil))

;; Initialize app
(defn init
  []
  (js/console.log "Initializing app...")
  (api/fetch-wines app-state)
  (api/fetch-classifications app-state) ;; Load classifications at startup
  (when-let [container (.getElementById js/document "app")]
    (when (nil? @root) (reset! root (createRoot container)))
    (.render @root
             (r/as-element [theme-provider wine-theme
                            [views/main-app app-state]]))))

;; Start the app when loaded
(defn ^:export main [] (init))

