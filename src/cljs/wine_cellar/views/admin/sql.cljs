(ns wine-cellar.views.admin.sql
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.alert :refer [alert]]
            [reagent-mui.material.table :refer [table]]
            [reagent-mui.material.table-body :refer [table-body]]
            [reagent-mui.material.table-cell :refer [table-cell]]
            [reagent-mui.material.table-container :refer [table-container]]
            [reagent-mui.material.table-head :refer [table-head]]
            [reagent-mui.material.table-row :refer [table-row]]
            [cljs.core.async :refer [<!]]
            [wine-cellar.api :as api])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- result-table
  [data]
  (when (seq data)
    (let [headers (keys (first data))]
      [paper {:sx {:width "100%" :mt 2 :overflow "hidden"}}
       [table-container {:sx {:maxHeight 600}}
        [table {:stickyHeader true :size "small"}
         [table-head
          [table-row
           (for [h headers]
             ^{:key h} [table-cell {:sx {:fontWeight "bold"}} (name h)])]]
         [table-body
          (for [[idx row] (map-indexed vector data)]
            ^{:key idx}
            [table-row
             (for [h headers] ^{:key h} [table-cell (str (get row h))])])]]]])))

(defn sql-page
  []
  (let [query (r/atom "")
        result (r/atom nil)
        error (r/atom nil)
        loading? (r/atom false)
        handle-run (fn []
                     (reset! loading? true)
                     (reset! error nil)
                     (reset! result nil)
                     (go (let [resp (<! (api/execute-sql @query))]
                           (reset! loading? false)
                           (if (:success resp)
                             (reset! result (:data resp))
                             (reset! error (:error resp))))))]
    (fn [] [box {:sx {:maxWidth "100%" :mx "auto" :my 3 :p 2}}
            [typography {:variant "h5" :sx {:mb 2}} "Run SQL Query"]
            [paper {:sx {:p 2 :mb 2}}
             [text-field
              {:label "SQL Query"
               :multiline true
               :rows 4
               :fullWidth true
               :value @query
               :on-change #(reset! query (.. % -target -value))
               :sx {:mb 2 :fontFamily "monospace"}}]
             [button
              {:variant "contained"
               :color "primary"
               :disabled (or @loading? (empty? @query))
               :on-click handle-run} (if @loading? "Running..." "Run Query")]]
            (when @error [alert {:severity "error" :sx {:mb 2}} (str @error)])
            (when @result
              [box
               [typography {:variant "subtitle2" :sx {:mb 1}}
                (str "Result (" (count @result) " rows)")]
               [result-table @result]])])))
