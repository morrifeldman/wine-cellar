(ns wine-cellar.core
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent.dom.client :as dom-client]
            [wine-cellar.views.main :as views]
            [wine-cellar.api :as api]
            [wine-cellar.dom :as dom]
            [wine-cellar.nav :as nav]
            [wine-cellar.state :refer
             [initial-app-state default-recipe-filters]]
            [wine-cellar.theme :refer [wine-theme]]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [reagent-mui.styles :refer [theme-provider]]
            [goog.object :as gobj]))

(defonce app-state (r/atom initial-app-state))

(defn- parse-id
  [s]
  (when s (let [id (js/parseInt s 10)] (when-not (js/isNaN id) id))))

(defn- highlight-ids
  "The Mixers items the URL marks, in the order it names them — which is the
   order the ingredient listed its bottles in, so the first one is the one to
   scroll to."
  [match]
  (when-let [highlight (:highlight (:query-params match))]
    (into [] (keep parse-id) (str/split highlight #","))))

(defn- match->bar-state
  "The bar's own sub-state, as the URL states it. Every key here is owned by the
   URL, so a nil is an answer and not a gap: it closes whatever a previous URL
   had open."
  [match]
  (let [name (-> match
                 :data
                 :name)
        id (parse-id (:id (:path-params match)))
        {:keys [category subcategory]} (:query-params match)]
    {:active-tab (case name
                   (::nav/bar-spirits ::nav/bar-spirit) :spirits
                   ::nav/bar-inventory :inventory
                   :recipes)
     :viewing-recipe-id (when (= name ::nav/bar-recipe) id)
     ;; The edit form is a way of showing the recipe the URL names, so
     ;; leaving that URL ends the edit — otherwise the form would follow
     ;; you onto the list a Back lands on.
     :editing-recipe-id nil
     :editing-spirit-id (when (= name ::nav/bar-spirit) id)
     :spirits-initial-filter (when (seq category)
                               {:categories #{category}
                                :subcategories
                                (if (seq subcategory) #{subcategory} #{})})
     :highlight-item-ids (not-empty (set (highlight-ids match)))}))

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
      (::nav/bar ::nav/bar-recipes
                 ::nav/bar-recipe ::nav/bar-spirits
                 ::nav/bar-spirit ::nav/bar-inventory)
      {:view :bar
       :selected-wine-id nil
       :show-wine-form? false
       :show-report? false
       :bar (match->bar-state match)}
      {:view nil
       :selected-wine-id nil
       :show-wine-form? false
       :show-report? false})))

(defn- match->modal-state
  "Every modal that can be backed out of names itself in the query string, so
   the URL alone says which ones are open."
  [match]
  (let [{:keys [stats note zoom selected only]} (:query-params match)
        selected-ids
        (into #{} (keep parse-id) (str/split (or selected "") #","))]
    {:show-collection-stats? (= "1" stats)
     :show-tasting-note-form? (= "new" note)
     :editing-note-id (when (and note (not= "new" note))
                        (let [id (js/parseInt note 10)]
                          (when-not (js/isNaN id) id)))
     :zoomed-image zoom
     :selected-wine-ids selected-ids
     :show-selected-wines? (boolean (and (= "selected" only)
                                         (seq selected-ids)))}))

(defn- note-form-open?
  [state]
  (or (:show-tasting-note-form? state) (boolean (:editing-note-id state))))

(defn- note-touched?
  "Whether the open tasting-note form holds anything worth warning about. What
   counts is the difference from the snapshot taken when the form opened, not
   whether it has content: a note opened for editing arrives full of text, and a
   new one arrives dated today, so neither is empty to begin with. A saved note
   drops its snapshot, which is what stops a save from asking you to discard the
   work it just wrote."
  [state]
  (when-let [{:keys [note notes]} (:tasting-note-baseline state)]
    (let [typed (dom/notes-field-text)]
      (or (not= (dissoc (or (:new-tasting-note state) {}) :wine-id)
                (dissoc note :wine-id))
          (and (some? typed) (not= typed notes))))))

(defn on-navigate
  [match _history]
  (let [nav-state (match->nav-state match)
        modal-state (match->modal-state match)
        old-wine-id (:selected-wine-id @app-state)
        new-wine-id (:selected-wine-id nav-state)
        old-view (:view @app-state)
        new-view (:view nav-state)
        chat-open? (= "1" (:chat (:query-params match)))
        chat-was-open? (get-in @app-state [:chat :open?])
        submitting-note? (:submitting-note? @app-state)
        note-dirty? (and (note-form-open? @app-state)
                         (not (note-form-open? modal-state))
                         (not submitting-note?)
                         (note-touched? @app-state))]
    (when (and old-wine-id (not= old-wine-id new-wine-id))
      (api/exit-wine-detail-page app-state))
    (if (and note-dirty?
             (not (js/confirm "Discard your in-progress tasting note?")))
      (nav/undo-back!)
      (do
        (swap! app-state (fn [s]
                           (-> s
                               (cond-> (not (note-form-open? modal-state))
                                       (dissoc :new-tasting-note
                                        :tasting-note-baseline))
                               (merge (dissoc nav-state :bar) modal-state)
                               ;; The bar's URL-owned keys share :bar
                               ;; with plenty the URL says nothing
                               ;; about (filters, half-filled forms),
                               ;; so merge the URL's answer into the
                               ;; sub-map instead of over it.
                               (cond-> (:bar nav-state)
                                       (update :bar merge (:bar nav-state))))))
        (cond
          ;; The URL owns the chat, so arriving at one that names it is
          ;; what opens it, a reload and a Back onto the FAB's entry
          ;; included.
          chat-open? (when-not chat-was-open?
                       (swap! app-state assoc-in [:chat :open?] true)
                       (api/load-conversations! app-state {:force? true}))
          ;; The conversation belongs to the page it was started on
          (not= old-view new-view)
          (swap! app-state (fn [s]
                             (-> s
                                 (assoc-in [:chat :open?] false)
                                 (assoc-in [:chat :conversations-loaded?] false)
                                 (assoc-in [:chat :active-conversation-id] nil)
                                 (assoc-in [:chat :messages] []))))
          ;; Closed on the same page, so it survives a reopen
          chat-was-open? (swap! app-state assoc-in [:chat :open?] false))
        ;; Whatever the bar URL points at, put it under the reader's eyes
        ;; rather than trusting the browser's own scroll restoration.
        ;; Driven from here so a Back onto a detail scrolls to it too,
        ;; not just the click that opened it.
        (when-let [recipe-id (get-in nav-state [:bar :viewing-recipe-id])]
          (dom/scroll-recipe-into-view! recipe-id))
        (when-let [spirit-id (get-in nav-state [:bar :editing-spirit-id])]
          (dom/scroll-spirit-into-view! spirit-id))
        (when (get-in nav-state [:bar :highlight-item-ids])
          (dom/scroll-bar-item-into-view! (first (highlight-ids match))))
        ;; Only on arrival: opening a modal on a wine page navigates too,
        ;; and reloading the page under it would scroll it away and
        ;; discard whatever the modal is editing.
        (when (and new-wine-id (not= old-wine-id new-wine-id))
          (api/load-wine-detail-page app-state new-wine-id))
        (when (and (:show-report? nav-state) (not (:report @app-state)))
          (api/fetch-latest-report app-state
                                   {:provider (get-in @app-state
                                                      [:ai :provider])}))
        (when (= :devices (:view nav-state)) (api/fetch-devices app-state))
        (when (= :sensor-readings (:view nav-state))
          (api/fetch-latest-sensor-readings app-state {}))
        (when (and (= :bar new-view) (not= :bar old-view))
          ;; Arriving at the bar from elsewhere, rather than moving
          ;; around inside it — start with the whole recipe collection
          ;; showing.
          (swap! app-state assoc-in
            [:bar :recipe-filters]
            default-recipe-filters)
          (api/fetch-bar-data app-state))
        (nav/remember-location!)))))

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
