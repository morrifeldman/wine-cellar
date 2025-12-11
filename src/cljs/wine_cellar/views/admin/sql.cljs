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

(defn- table-schema-item
  [{:keys [table expanded? on-toggle on-select-all]}]
  [box {:sx {:mb 0.5}}
   [box
    {:sx {:cursor "pointer"
          :display "flex"
          :alignItems "center"
          :py 0.5
          :px 1
          :borderRadius 1
          :&:hover {:bgcolor "action.hover"}}
     :on-click #(on-toggle (:table_name table))}
    [typography {:variant "caption" :sx {:mr 1 :width 12}}
     (if expanded? "▼" "▶")]
    [typography
     {:variant "body2"
      :sx {:fontWeight (if expanded? "bold" "normal")
           :color
           (if (= (:table_type table) "VIEW") "primary.main" "text.primary")}}
     (str (:table_name table))]]
   ;; Expanded Column List
   (when expanded?
     [box {:sx {:pl 4 :py 0.5}}
      ;; Quick Query Link
      [box
       {:sx {:cursor "pointer"
             :mb 1
             :color "primary.main"
             :fontSize "0.75rem"
             :&:hover {:textDecoration "underline"}}
        :on-click
        (fn [e] (.stopPropagation e) (on-select-all (:table_name table)))}
       "Select All (Limit 100)"]
      ;; Columns
      (for [col (:columns table)]
        ^{:key (:column_name col)}
        [box {:sx {:display "flex" :justifyContent "space-between" :mb 0.5}}
         [typography {:variant "caption" :sx {:color "text.primary"}}
          (:column_name col)]
         [typography {:variant "caption" :sx {:color "text.secondary" :ml 1}}
          (:data_type col)]])])])

(defn sql-page
  []
  (let [query (r/atom "")
        result (r/atom nil)
        error (r/atom nil)
        loading? (r/atom false)
        schema (r/atom nil)
        expanded-tables (r/atom #{})
        load-schema (fn []
                      (go (let [resp (<! (api/fetch-db-schema))]
                            (when (:success resp)
                              (reset! schema (:data resp))))))
        toggle-table (fn [table-name]
                       (swap! expanded-tables (fn [s]
                                                (if (contains? s table-name)
                                                  (disj s table-name)
                                                  (conj s table-name)))))
        handle-run (fn []
                     (reset! loading? true)
                     (reset! error nil)
                     (reset! result nil)
                     (go (let [resp (<! (api/execute-sql @query))]
                           (reset! loading? false)
                           (if (:success resp)
                             (reset! result (:data resp))
                             (reset! error (:error resp))))))]
    (load-schema)
    (fn [] [box
            {:sx {:display "flex" :gap 2 :p 2 :height "calc(100vh - 100px)"}}
            ;; Schema Sidebar
            [paper
             {:sx {:width 280
                   :p 2
                   :flexShrink 0
                   :overflow "auto"
                   :display "flex"
                   :flexDirection "column"}}
             [typography {:variant "h6" :sx {:mb 2}} "Schema"]
             (if @schema
               [box {:sx {:flexGrow 1 :overflow "auto"}}
                (doall
                 (for [table @schema]
                   ^{:key (:table_name table)}
                   [table-schema-item
                    {:table table
                     :expanded? (contains? @expanded-tables (:table_name table))
                     :on-toggle toggle-table
                     :on-select-all
                     #(reset! query (str "SELECT * FROM " % " LIMIT 100;"))}]))]
               [typography {:variant "body2" :color "text.secondary"}
                "Loading schema..."])]
            ;; Main Query Area
            [box
             {:sx {:flexGrow 1
                   :display "flex"
                   :flexDirection "column"
                   :overflow "hidden"}}
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
                {:sx {:flexGrow 1
                      :overflow "hidden"
                      :display "flex"
                      :flexDirection "column"}}
                [typography {:variant "subtitle2" :sx {:mb 1}}
                 (str "Result (" (count @result) " rows)")]
                [result-table @result]])]])))
