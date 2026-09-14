(ns wine-cellar.nav
  (:require [clojure.string :as str]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]))

(def routes
  [["/" {:name ::wines}] ["/wine/:id" {:name ::wine-detail}]
   ["/add-wine" {:name ::add-wine}] ["/insights" {:name ::insights}]
   ["/grape-varieties" {:name ::grape-varieties}]
   ["/classifications" {:name ::classifications}] ["/sensors" {:name ::sensors}]
   ["/devices" {:name ::devices}] ["/blind-tastings" {:name ::blind-tastings}]
   ["/admin/sql" {:name ::admin-sql}] ["/bar" {:name ::bar}]])

(defn go-wines! [] (rfe/push-state ::wines))
(defn go-wine-detail! [id] (rfe/push-state ::wine-detail {:id id}))
(defn go-add-wine! [] (rfe/push-state ::add-wine))
(defn go-insights! [] (rfe/push-state ::insights))
(defn go-grape-varieties! [] (rfe/push-state ::grape-varieties))
(defn go-classifications! [] (rfe/push-state ::classifications))
(defn go-sensors! [] (rfe/push-state ::sensors))
(defn go-devices! [] (rfe/push-state ::devices))
(defn go-blind-tastings! [] (rfe/push-state ::blind-tastings))
(defn go-admin-sql! [] (rfe/push-state ::admin-sql))
(defn go-bar! [] (rfe/push-state ::bar))
(defn replace-wines! [] (rfe/replace-state ::wines))

(defn go-selected-wines!
  "Show the wine list narrowed to the given ids."
  [ids]
  (rfe/push-state ::wines nil {:selected (str/join "," ids)}))

(defn open-modal!
  "Open a modal by naming it in the current route's query string, so Back closes
   just that modal and a reload opens it again."
  ([param] (open-modal! param 1))
  ([param value] (rfe/set-query #(assoc % param (str value)))))

(defn back! [] (.back js/history))

(defn modal-in-url?
  "Whether the current URL names this modal, i.e. whether Back is what closes it."
  [param]
  (.has (js/URLSearchParams. (.-search js/location)) (name param)))

(defn- current-location
  []
  (str (.-pathname js/location) (.-search js/location)))

(defn forget-modal!
  "Drop a modal's query param without telling the router — the app already left
   the state that param describes."
  [param]
  (.replaceState js/history
                 nil
                 ""
                 (rf/set-query-params (current-location) #(dissoc % param))))

(defonce ^:private settled-location (atom nil))

(defn remember-location! [] (reset! settled-location (current-location)))

(defn undo-back!
  "Put back the entry a Back press popped, for when the app refuses to leave the
   screen (an unsaved form). Without it the browser stack would sit one entry
   shallower than what's on screen and swallow the next Back."
  []
  (when-let [location @settled-location]
    (.pushState js/history nil "" location)))
