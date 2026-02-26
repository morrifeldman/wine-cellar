(ns wine-cellar.nav
  (:require [reitit.frontend.easy :as rfe]))

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
