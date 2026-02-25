(ns wine-cellar.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as dom-client]
            [wine-cellar.views.main :as views]
            [wine-cellar.api :as api]
            [wine-cellar.nav :as nav]
            [wine-cellar.state :refer [initial-app-state]]
            [wine-cellar.theme :refer [wine-theme]]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [reagent-mui.styles :refer [theme-provider]]
            [goog.object :as gobj]))

(defonce app-state (r/atom initial-app-state))

(defn- match->nav-state
  [match]
  (let [name (-> match
                 :data
                 :name)
        params (:path-params match)]
    (case name
      ::nav/wines {:view nil
                   :selected-wine-id nil
                   :show-wine-form? false
                   :show-report? false}
      ::nav/wine-detail {:view nil
                         :selected-wine-id (js/parseInt (:id params) 10)
                         :show-wine-form? false
                         :show-report? false}
      ::nav/add-wine {:view nil
                      :selected-wine-id nil
                      :show-wine-form? true
                      :show-report? false}
      ::nav/insights {:view nil
                      :selected-wine-id nil
                      :show-wine-form? false
                      :show-report? true}
      ::nav/grape-varieties {:view :grape-varieties
                             :selected-wine-id nil
                             :show-wine-form? false
                             :show-report? false}
      ::nav/classifications {:view :classifications
                             :selected-wine-id nil
                             :show-wine-form? false
                             :show-report? false}
      ::nav/sensors {:view :sensor-readings
                     :selected-wine-id nil
                     :show-wine-form? false
                     :show-report? false}
      ::nav/devices {:view :devices
                     :selected-wine-id nil
                     :show-wine-form? false
                     :show-report? false}
      ::nav/blind-tastings {:view :blind-tastings
                            :selected-wine-id nil
                            :show-wine-form? false
                            :show-report? false}
      ::nav/admin-sql {:view :sql
                       :selected-wine-id nil
                       :show-wine-form? false
                       :show-report? false}
      {:view nil
       :selected-wine-id nil
       :show-wine-form? false
       :show-report? false})))

(defn on-navigate
  [match _history]
  (let [nav-state (match->nav-state match)
        old-wine-id (:selected-wine-id @app-state)
        new-wine-id (:selected-wine-id nav-state)]
    (when (and old-wine-id (not= old-wine-id new-wine-id))
      (api/exit-wine-detail-page app-state))
    (swap! app-state (fn [s]
                       (-> s
                           (dissoc :zoomed-image)
                           (merge nav-state))))
    (when new-wine-id (api/load-wine-detail-page app-state new-wine-id))
    (when (:show-report? nav-state)
      (api/fetch-latest-report app-state
                               {:provider (get-in @app-state [:ai :provider])}))
    (when (= :devices (:view nav-state)) (api/fetch-devices app-state))
    (when (= :sensor-readings (:view nav-state))
      (api/fetch-latest-sensor-readings app-state {}))))

(defonce root (atom nil))

(defonce service-worker-state (atom {:registered? false :poll-interval nil}))

(defn- notify-update-available!
  "Record that a newer application bundle is ready so the UI can prompt the user."
  [version]
  (swap! app-state (fn [state]
                     (let [current (get-in state [:update-available :version])]
                       (if (= current version)
                         state
                         (assoc state
                                :update-available
                                {:version version
                                 :notified-at (js/Date.now)}))))))

(defn- handle-sw-message
  [event]
  (when-let [data (.-data event)]
    (when (= "version-update" (gobj/get data "type"))
      (let [version (gobj/get data "version")]
        (js/console.info "New app version detected; prompting user to refresh."
                         (when version (str "(version " version ")")))
        (notify-update-available! version)))))

(defn- trigger-version-check
  []
  (-> (js/fetch "/version.json" #js {:cache "no-store"})
      (.catch (fn [err] (js/console.warn "Version check failed" err)))))

(defn register-service-worker!
  []
  (when-let [container (some-> js/navigator
                               (.-serviceWorker))]
    (when-not (:registered? @service-worker-state)
      (-> (.register container "/service-worker.js")
          (.then (fn [registration]
                   (js/console.info "Service worker registered" registration)
                   (.addEventListener container "message" handle-sw-message)
                   (trigger-version-check)
                   (let [interval (js/setInterval trigger-version-check
                                                  (* 5 60 1000))]
                     (swap! service-worker-state assoc
                       :registered? true
                       :poll-interval interval))))
          (.catch (fn [err]
                    (js/console.error "Service worker registration failed"
                                      err)))))))

(defn init
  []
  (js/console.log "Initializing app...")
  (rfe/start! (rf/router nav/routes) on-navigate {:use-fragment false})
  (when-not @api/headless-mode?
    (api/fetch-model-info app-state)
    (api/fetch-verbose-logging-state app-state))
  (when (and (empty? (:wines @app-state)) (not @api/headless-mode?))
    (api/fetch-wines app-state))
  (when (and (empty? (:classifications @app-state)) (not @api/headless-mode?))
    (api/fetch-classifications app-state))
  (when (and (empty? (:grape-varieties @app-state)) (not @api/headless-mode?))
    (api/fetch-grape-varieties app-state))
  (when-not @root
    (reset! root (dom-client/create-root (js/document.getElementById "app"))))
  (dom-client/render @root
                     [theme-provider wine-theme [views/main-app app-state]])
  (register-service-worker!))

;; Start the app when loaded
(defn ^:export main [] (init))

(defn ^:dev/after-load on-reload
  []
  (js/console.log "Code updated, re-rendering app...")
  (init))
