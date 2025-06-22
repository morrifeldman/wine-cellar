(ns wine-cellar.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as dom-client]
            [wine-cellar.views.main :as views]
            [wine-cellar.api :as api]
            [wine-cellar.state :refer [initial-app-state]]
            [wine-cellar.theme :refer [wine-theme]]
            [reagent-mui.styles :refer [theme-provider]]))

(defonce app-state (r/atom initial-app-state))

(defn update-url-from-state
  [state]
  (let [url (cond (:selected-wine-id state) (str "/wine/"
                                                 (:selected-wine-id state))
                  (:show-wine-form? state) "/add-wine"
                  (= (:view state) :grape-varieties) "/grape-varieties"
                  (= (:view state) :classifications) "/classifications"
                  :else "/")]
    (when (not= url (.-pathname js/location))
      (.pushState js/history nil "" url))))

(defn parse-url
  []
  (let [path (.-pathname js/location)]
    (cond (= path "/") {:view nil :selected-wine-id nil :show-wine-form? false}
          (= path "/add-wine")
          {:view nil :selected-wine-id nil :show-wine-form? true}
          (= path "/grape-varieties")
          {:view :grape-varieties :selected-wine-id nil :show-wine-form? false}
          (= path "/classifications")
          {:view :classifications :selected-wine-id nil :show-wine-form? false}
          (.startsWith path "/wine/")
          {:view nil :selected-wine-id (subs path 6) :show-wine-form? false}
          :else {:view nil :selected-wine-id nil :show-wine-form? false})))

(defn sync-state-with-url
  []
  (let [url-state (parse-url)] (swap! app-state merge url-state)))

(defn handle-popstate [_] (sync-state-with-url))


(add-watch
 app-state
 :url-sync
 (fn [_ _ old-state new-state]
   (when (or (not= (:view old-state) (:view new-state))
             (not= (:selected-wine-id old-state) (:selected-wine-id new-state))
             (not= (:show-wine-form? old-state) (:show-wine-form? new-state)))
     (update-url-from-state new-state))))

(defonce root (atom nil))

(defn init
  []
  (js/console.log "Initializing app...")
  ;; Set up browser history handling
  (.addEventListener js/window "popstate" handle-popstate)
  ;; Sync initial state with URL
  (sync-state-with-url)
  ;; Only fetch data if we don't already have it and we're not in headless
  ;; mode
  (when (and (empty? (:wines @app-state)) (not @api/headless-mode?))
    (api/fetch-wines app-state))
  (when (and (empty? (:classifications @app-state)) (not @api/headless-mode?))
    (api/fetch-classifications app-state))
  (when (and (empty? (:grape-varieties @app-state)) (not @api/headless-mode?))
    (api/fetch-grape-varieties app-state))
  (when-not @root
    (reset! root (dom-client/create-root (js/document.getElementById "app"))))
  (dom-client/render @root
                     [theme-provider wine-theme [views/main-app app-state]]))

;; Start the app when loaded
(defn ^:export main [] (init))

(defn ^:dev/after-load on-reload
  []
  (js/console.log "Code updated, re-rendering app...")
  (init))
