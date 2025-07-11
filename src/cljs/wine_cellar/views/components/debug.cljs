(ns wine-cellar.views.components.debug
  (:require [reagent.core :as r]
            [wine-cellar.api :as api]
            [cljs.reader :as reader]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.text-field :as mui-text-field]))

;; Helper functions for managing saved states
(defn get-saved-states
  "Returns a map of all saved states from localStorage"
  []
  (try (js->clj (js/JSON.parse
                 (or (js/localStorage.getItem "wine-cellar-saved-states") "{}"))
                :keywordize-keys
                true)
       (catch js/Error _ {})))

(defn save-state
  "Saves a state with the given name to localStorage"
  [name state-str]
  (let [saved-states (get-saved-states)
        updated-states (assoc saved-states
                              (keyword name)
                              {:timestamp (.toISOString (js/Date.))
                               :state state-str})]
    (js/localStorage.setItem "wine-cellar-saved-states"
                             (js/JSON.stringify (clj->js updated-states)))
    updated-states))

(defn delete-saved-state
  "Deletes a saved state by name"
  [name]
  (let [saved-states (get-saved-states)
        updated-states (dissoc saved-states (keyword name))]
    (js/localStorage.setItem "wine-cellar-saved-states"
                             (js/JSON.stringify (clj->js updated-states)))
    updated-states))

(defn apply-debug-state!
  "Applies a new state to the app, saves a backup of the current state,
   and enables headless mode. Returns true if successful."
  [app-state new-state]
  (try
    ;; Either use the existing backup state (if we're already in headless
    ;; mode)
    ;; since the backup state should come from the non-headless mode
    ;; or create a new backup from the current state
    (let [backup-state (or (:_headless_backup_state @app-state) @app-state)]
      (reset! app-state (assoc new-state :_headless_backup_state backup-state)))
    (api/enable-headless-mode!)
    (js/console.log "Debug state applied successfully")
    true
    (catch js/Error e
      (js/console.error "Error applying debug state:" e)
      (js/alert (str "Error parsing state: " e))
      false)))

(defn exit-debug-mode!
  "Exits debug mode, restores the backup state while preserving view context,
   and refreshes data from the server. Returns true if successful."
  [app-state]
  (try
    ;; Disable headless mode
    (api/disable-headless-mode!)
    ;; If we have a backup state, restore it
    (when-let [backup-state (:_headless_backup_state @app-state)]
      (reset! app-state backup-state))
    (js/console.log "Exited debug mode successfully")
    true
    (catch js/Error e (js/console.error "Error exiting debug mode:" e) false)))

(defn headless-mode-toggle
  "Toggle button for headless mode"
  [app-state]
  [:div
   [:button
    {:on-click #(if @api/headless-mode?
                  (exit-debug-mode! app-state)
                  (apply-debug-state! app-state @app-state))
     :style {:background-color (if @api/headless-mode? "#ffcccc" "#ccffcc")
             :padding "5px 10px"
             :margin "5px 0"
             :border "1px solid #ccc"
             :border-radius "3px"
             :cursor "pointer"}}
    (if @api/headless-mode? "Disable Headless Mode" "Enable Headless Mode")]
   ;; Status indicator
   [:div
    {:style {:font-size "0.8em"
             :color (if @api/headless-mode? "red" "green")
             :margin "5px 0"}}
    (if @api/headless-mode?
      "API calls are being intercepted to preserve state"
      "API calls are being processed normally")]])

(defn state-editor
  "Editor for app state with buttons to apply and capture state"
  [app-state]
  [:div {:style {:margin-top "20px"}}
   [:h4 {:style {:margin-bottom "8px"}} "App State Editor"]
   [:textarea
    {:rows 10
     :cols 50
     :placeholder "Paste app state EDN here..."
     :id "test-state-input"
     :style {:width "100%"
             :font-family "monospace"
             :font-size "12px"
             :padding "8px"
             :border "1px solid #ccc"
             :border-radius "3px"
             :resize "vertical"}}]
   [:div {:style {:margin-top "10px" :display "flex" :gap "10px"}}
    [:button
     {:on-click
      #(try (let [input-value (.-value (.getElementById js/document
                                                        "test-state-input"))
                  parsed-state (reader/read-string input-value)]
              (apply-debug-state! app-state parsed-state))
            (catch js/Error e (js/alert (str "Error parsing state: " e))))
      :style {:padding "5px 10px"
              :background-color "#4CAF50"
              :color "white"
              :border "none"
              :border-radius "3px"
              :cursor "pointer"}} "Apply This State"]
    ;; Capture current state button
    [:button
     {:on-click #(let [current-state (pr-str @app-state)]
                   (set! (.-value (.getElementById js/document
                                                   "test-state-input"))
                         current-state)
                   (js/console.log "Current state captured to textarea"))
      :style {:padding "5px 10px"
              :background-color "#2196F3"
              :color "white"
              :border "none"
              :border-radius "3px"
              :cursor "pointer"}} "Capture Current State"]]])

(defn state-save-form
  "Form for saving the current state with a name"
  [saved-states state-name]
  [:div {:style {:margin-top "15px"}} [:h4 "Save Editor State"]
   [:div {:style {:display "flex" :gap "10px" :margin-top "5px"}}
    [:input
     {:type "text"
      :placeholder "Enter a name for this state"
      :value @state-name
      :on-change #(reset! state-name (.. % -target -value))
      :style {:flex 1
              :padding "5px 8px"
              :border "1px solid #ccc"
              :border-radius "3px"}}]
    [:button
     {:on-click #(let [name @state-name
                       state-str (.-value (.getElementById js/document
                                                           "test-state-input"))]
                   (if (empty? name)
                     (js/alert "Please enter a name for the state")
                     (do (reset! saved-states (save-state name state-str))
                         (reset! state-name "")
                         (js/console.log (str "State saved as '" name "'")))))
      :style {:padding "5px 10px"
              :background-color "#ff9800"
              :color "white"
              :border "none"
              :border-radius "3px"
              :cursor "pointer"}} "Save to Storage"]]])

(defn saved-state-item
  "A single saved state item with actions"
  [key {:keys [timestamp state]} app-state saved-states]
  ^{:key key}
  [:div
   {:style {:padding "8px"
            :border-bottom "1px solid #eee"
            :display "flex"
            :justify-content "space-between"
            :align-items "center"}}
   [:div [:div {:style {:font-weight "bold"}} (name key)]
    [:div {:style {:font-size "0.8em" :color "#666"}}
     (.toLocaleString (js/Date. timestamp))]]
   [:div {:style {:display "flex" :gap "5px"}}
    [:button
     {:on-click #(do (set! (.-value (.getElementById js/document
                                                     "test-state-input"))
                           state)
                     (js/console.log (str "Recalled state '" (name key) "'")))
      :style {:padding "3px 6px"
              :background-color "#2196F3"
              :color "white"
              :border "none"
              :border-radius "3px"
              :font-size "0.8em"
              :cursor "pointer"}} "Recall"]
    [:button
     {:on-click #(try (let [parsed-state (reader/read-string state)]
                        (apply-debug-state! app-state parsed-state)
                        (js/console.log (str "Applied state '" (name key) "'")))
                      (catch js/Error e
                        (js/alert (str "Error parsing state: " e))))
      :style {:padding "3px 6px"
              :background-color "#4CAF50"
              :color "white"
              :border "none"
              :border-radius "3px"
              :font-size "0.8em"
              :cursor "pointer"}} "Apply"]
    [:button
     {:on-click #(when (js/confirm (str "Delete saved state '" (name key) "'?"))
                   (reset! saved-states (delete-saved-state (name key)))
                   (js/console.log (str "Deleted state '" (name key) "'")))
      :style {:padding "3px 6px"
              :background-color "#f44336"
              :color "white"
              :border "none"
              :border-radius "3px"
              :font-size "0.8em"
              :cursor "pointer"}} "Delete"]]])

(defn saved-states-list
  "List of all saved states"
  [saved-states app-state]
  [:div {:style {:margin-top "20px"}} [:h4 "Saved States"]
   (if (empty? @saved-states)
     [:p {:style {:font-style "italic" :color "#666"}} "No saved states yet"]
     [:div
      {:style {:max-height "200px"
               :overflow-y "auto"
               :border "1px solid #ddd"
               :border-radius "3px"
               :margin-top "5px"}}
      (for [[key state-data] @saved-states]
        ^{:key key}
        [saved-state-item key state-data app-state saved-states])])])

(defn uncontrolled-textarea-demo
  "Demo of uncontrolled textarea for performance testing"
  []
  (r/with-let
   [textarea-ref (r/atom nil) demo-mode (r/atom "new") demo-text (r/atom "")]
   [:div
    {:style
     {:margin-top "20px" :border-top "1px solid #ddd" :padding-top "10px"}}
    [:h4 "Uncontrolled Textarea Demo"]
    [:p {:style {:font-size "0.9em" :color "#666"}}
     "Test typing performance - should be smooth on mobile!"]
    ;; Mode switcher
    [:div {:style {:margin-bottom "10px"}}
     [:button
      {:on-click #(do (reset! demo-mode "new") (reset! demo-text ""))
       :style {:padding "5px 10px"
               :margin-right "5px"
               :background-color (if (= @demo-mode "new") "#4CAF50" "#ddd")}}
      "New Note Mode"]
     [:button
      {:on-click #(do (reset! demo-mode "edit")
                      (reset! demo-text
                        "This is existing text that you can edit..."))
       :style {:padding "5px 10px"
               :background-color (if (= @demo-mode "edit") "#4CAF50" "#ddd")}}
      "Edit Mode"]]
    ;; The uncontrolled textarea
    [mui-text-field/text-field
     {:key (str "demo-" @demo-mode) ; forces re-render on mode switch
      :label "Demo Notes Field"
      :multiline true
      :rows 4
      :fullWidth true
      :defaultValue @demo-text
      :inputRef #(reset! textarea-ref %)
      :variant "outlined"
      :placeholder "Type something long here to test performance..."
      :sx {:margin-bottom "10px"
           :color "#000"
           "& .MuiInputBase-input" {:color "#000"}
           "& textarea" {:color "#000"}}}]
    ;; Test buttons
    [:div {:style {:display "flex" :gap "10px"}}
     [:button
      {:on-click #(when @textarea-ref
                    (js/alert (str "Current value: " (.-value @textarea-ref))))
       :style {:padding "5px 10px" :background-color "#2196F3" :color "white"}}
      "Read Value"]
     [:button
      {:on-click #(when @textarea-ref
                    (set! (.-value @textarea-ref) "")
                    (js/alert "Cleared!"))
       :style {:padding "5px 10px" :background-color "#ff9800" :color "white"}}
      "Clear Field"]
     [:button
      {:on-click #(when @textarea-ref
                    (set! (.-value @textarea-ref) "Programmatically set text!")
                    (js/alert "Text set!"))
       :style {:padding "5px 10px" :background-color "#9c27b0" :color "white"}}
      "Set Text"]]]))

(defn debug-info
  "Display debug information"
  [app-state saved-states]
  [:div
   {:style
    {:margin-top "20px" :border-top "1px solid #ddd" :padding-top "10px"}}
   [:h4 "Debug Information"]
   [:div
    [:p {:style {:font-size "0.9em"}} "Current mode: "
     [:strong (if @api/headless-mode? "Headless" "Normal")]]
    [:p {:style {:font-size "0.9em"}} "App state size: "
     [:strong (count (pr-str @app-state)) " characters"]]
    [:p {:style {:font-size "0.9em"}} "Saved states: "
     [:strong (count @saved-states)]]
    [:p {:style {:font-size "0.8em" :color "#666"}}
     "Note: When in headless mode, API calls are intercepted and won't affect the backend."]]])

(defn debug-panel
  "A panel with controls for debugging app state"
  [app-state]
  (let [saved-states (r/atom (get-saved-states))
        state-name (r/atom "")]
    (fn [] [:div.debug-panel-content
            [:h3
             {:style {:margin-top 0
                      :border-bottom "1px solid #ddd"
                      :padding-bottom "8px"}} "Debug Controls"]
            [headless-mode-toggle app-state] [uncontrolled-textarea-demo]
            [state-editor app-state] [state-save-form saved-states state-name]
            [saved-states-list saved-states app-state]
            [debug-info app-state saved-states]])))

(defn debug-sidebar
  "A sidebar containing debug controls.
   Takes the app-state atom as a parameter."
  [app-state]
  [:div.debug-sidebar
   {:style {:position "fixed"
            :top 0
            :right 0
            :width "350px"
            :height "100vh"
            :background-color "#f8f8f8"
            :border-left "1px solid #ccc"
            :box-shadow "-2px 0 5px rgba(0,0,0,0.1)"
            :overflow-y "auto"
            :z-index 999
            :padding "15px"}}
   ;; Use the debug panel inside the sidebar
   [debug-panel app-state]])
