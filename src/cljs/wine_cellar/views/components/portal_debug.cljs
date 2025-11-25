(ns wine-cellar.views.components.portal-debug
  (:require [wine-cellar.portal-debug :as pd]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]))

;; Replace your debug-button function:
(defn debug-button
  [app-state]
  [box {:sx {:position "fixed" :bottom "10px" :right "100px" :zIndex 1000}}
   [button
    {:variant "contained"
     :color (if (:active? @pd/debug-state) "error" "primary")
     :size "small"
     :onClick #(pd/toggle-debugging! app-state)
     :sx {:textTransform "none" :boxShadow 3}}
    (if-not (:active? @pd/debug-state) "Start Debugging" "Stop Debugger")]])
