(ns wine-cellar.views.components.wine-chat
  (:require [reagent.core :as r]
            [reagent-mui.material.fab :refer [fab]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.chat :refer [chat]]
            [reagent-mui.icons.close :refer [close]]
            [reagent-mui.icons.clear-all :refer [clear-all]]
            [reagent-mui.icons.edit :refer [edit]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.filters :refer [filtered-sorted-wines]]))

;; Constants
(def ^:private edit-icon-size "0.8rem")
(def ^:private edit-button-spacing 4)

(defn message-bubble
  "Renders a single chat message bubble"
  [{:keys [text is-user timestamp id]} on-edit]
  [box
   {:sx {:display "flex"
         :justify-content (if is-user "flex-end" "flex-start")
         :mb 1}}
   [paper
    {:elevation 2
     :sx {:p 2
          :max-width "80%"
          :background-color (if is-user "primary.main" "background.paper")
          :color (if is-user "background.default" "text.primary")
          :word-wrap "break-word"
          :white-space "pre-wrap"
          :border-radius 2
          :position "relative"}}
    [typography
     {:variant "body2"
      :sx {:color "inherit"
           :white-space "pre-wrap"
           :word-wrap "break-word"
           :line-height 1.6}} text]
    (when timestamp
      [typography
       {:variant "caption"
        :sx {:display "block" :mt 0.5 :opacity 0.7 :font-size "0.7em"}}
       (.toLocaleTimeString (js/Date. timestamp))])
    ;; Edit button for user messages
    (when (and is-user on-edit)
      [icon-button
       {:size "small"
        :sx {:position "absolute"
             :top edit-button-spacing
             :right edit-button-spacing
             :color "inherit"
             :opacity 0.7
             :&:hover {:opacity 1}}
        :on-click #(on-edit id text)}
       [edit {:sx {:font-size edit-icon-size}}]])]])

(defn chat-input
  "Chat input field with send button and debounced sync"
  [message on-send disabled?]
  (r/with-let [local-value (r/atom @message) debounce-timeout (r/atom nil)
               ;; Watch for external changes to message (like from edit)
               _
               (add-watch message
                          :sync-external
                          (fn [_ _ _ new-value]
                            (when (not= @local-value new-value)
                              (reset! local-value new-value))))]
              [box {:sx {:display "flex" :gap 1 :mt 2}}
               [text-field
                {:value @local-value
                 :on-change
                 #(let [new-value (.. % -target -value)]
                    (reset! local-value new-value)
                    ;; Clear existing timeout
                    (when @debounce-timeout (js/clearTimeout @debounce-timeout))
                    ;; Set debounced sync (400ms for mobile)
                    (reset! debounce-timeout (js/setTimeout
                                              (fn [] (reset! message new-value))
                                              400)))
                 :placeholder "Ask me about your wines..."
                 :variant "outlined"
                 :size "small"
                 :disabled @disabled?
                 :sx {:flex-grow 1}
                 :multiline true
                 :max-rows 3
                 :on-blur #(do
                             ;; Clear timeout and sync immediately
                             (when @debounce-timeout
                               (js/clearTimeout @debounce-timeout)
                               (reset! debounce-timeout nil))
                             (reset! message @local-value))
                 :on-key-press #(when (and (= (.-key %) "Enter")
                                           (not (.-shiftKey %))
                                           (not @disabled?)
                                           (seq (str @local-value)))
                                  (.preventDefault %)
                                  ;; Clear any pending timeout since we're
                                  ;; sending
                                  (when @debounce-timeout
                                    (js/clearTimeout @debounce-timeout)
                                    (reset! debounce-timeout nil))
                                  (reset! message @local-value)
                                  (on-send)
                                  (reset! local-value ""))}]
               [button
                {:variant "contained"
                 :disabled (or @disabled? (empty? (str @local-value)))
                 :on-click #(do
                              ;; Clear any pending timeout since we're
                              ;; sending
                              (when @debounce-timeout
                                (js/clearTimeout @debounce-timeout)
                                (reset! debounce-timeout nil))
                              (reset! message @local-value)
                              (on-send)
                              (reset! local-value ""))} "Send"]]
              ;; Cleanup watcher on unmount
              (finally (remove-watch message :sync-external))))

(defn chat-messages
  "Scrollable container for chat messages"
  [messages on-edit]
  [box
   {:sx {:height "400px"
         :overflow-y "auto"
         :p 2
         :background-color "background.default"
         :border "1px solid"
         :border-color "divider"
         :border-radius 1}}
   (if (empty? @messages)
     [typography
      {:variant "body2"
       :sx {:text-align "center" :color "text.secondary" :mt 2}}
      "Start a conversation about your wine cellar..."]
     (for [message @messages]
       ^{:key (:id message)} [message-bubble message on-edit]))])

(defn send-chat-message
  "Send a message to the AI chat endpoint with conversation history"
  [message wines conversation-history callback]
  (api/send-chat-message message wines conversation-history callback))

(defn- handle-send-message
  "Handle sending a message to the AI assistant"
  [app-state message-or-atom messages is-sending?]
  (let [message-text (if (satisfies? IDeref message-or-atom)
                       @message-or-atom
                       message-or-atom)]
    (when (and (not @is-sending?) (seq message-text))
      (reset! is-sending? true)
      (let [user-message {:id (random-uuid)
                          :text message-text
                          :is-user true
                          :timestamp (.getTime (js/Date.))}
            wines (if-let [current-wine-id (:selected-wine-id @app-state)]
                    ;; In detail view - pass only current wine
                    (filter #(= (:id %) current-wine-id) (:wines @app-state))
                    ;; In list view - pass all filtered wines
                    (filtered-sorted-wines app-state))]
        (swap! messages conj user-message)
        (swap! app-state assoc-in [:chat :messages] @messages)
        ;; Only reset if it's an atom (normal mode)
        (when (satisfies? IDeref message-or-atom) (reset! message-or-atom ""))
        (send-chat-message
         message-text
         wines
         @messages
         (fn [response]
           (let [ai-message {:id (random-uuid)
                             :text response
                             :is-user false
                             :timestamp (.getTime (js/Date.))}]
             (swap! messages conj ai-message)
             (swap! app-state assoc-in [:chat :messages] @messages)
             (reset! is-sending? false))))))))

(defn- use-edit-state
  "Hook for managing edit state"
  []
  (let [editing-message-id (r/atom nil)]
    {:editing-message-id editing-message-id
     :handle-edit (fn [message-id message-text current-message]
                    (reset! editing-message-id message-id)
                    (reset! current-message message-text))
     :handle-cancel (fn [current-message]
                      (reset! editing-message-id nil)
                      (reset! current-message ""))
     :is-editing? #(some? @editing-message-id)}))

(defn- handle-edit-send
  "Handle sending an edited message"
  [app-state editing-message-id current-message messages is-sending?]
  (if-let [message-idx (->> @messages
                            (keep-indexed
                             #(when (= (:id %2) @editing-message-id) %1))
                            first)]
    (let [updated-message
          (assoc (nth @messages message-idx) :text @current-message)]
      ;; Remove all messages after the edited one
      (reset! messages (conj (vec (take message-idx @messages))
                             updated-message))
      (reset! editing-message-id nil)
      (reset! current-message "")
      ;; Regenerate AI response with just the text
      (handle-send-message app-state
                           (:text updated-message)
                           messages
                           is-sending?))
    ;; If message not found, just clear edit state
    (do (reset! editing-message-id nil) (reset! current-message ""))))

(defn chat-dialog
  "Main chat dialog component"
  [app-state]
  (let [chat-state (:chat @app-state)
        messages (r/atom (:messages chat-state []))
        current-message (r/atom "")
        is-sending? (r/atom false)
        edit-state (use-edit-state)
        {:keys [editing-message-id handle-edit handle-cancel is-editing?]}
        edit-state
        handle-send (fn []
                      (if (is-editing?)
                        ;; Edit mode - replace message and regenerate
                        ;; response
                        (handle-edit-send app-state
                                          @editing-message-id
                                          current-message
                                          messages
                                          is-sending?)
                        ;; Normal mode - send new message
                        (handle-send-message app-state
                                             current-message
                                             messages
                                             is-sending?)))]
    (fn [app-state]
      (let [chat-state (:chat @app-state)
            is-open (:open? chat-state false)]
        [dialog
         {:open is-open
          :on-close #(swap! app-state assoc-in [:chat :open?] false)
          :max-width "md"
          :full-width true}
         [dialog-title
          [box
           {:sx {:display "flex"
                 :justify-content "space-between"
                 :align-items "center"}}
           [typography {:variant "h6"} "Wine Cellar Assistant"]
           [box {:sx {:display "flex" :gap 1}}
            [icon-button
             {:on-click #(do (reset! messages [])
                             (swap! app-state assoc-in [:chat :messages] []))
              :title "Clear chat history"} [clear-all]]
            [icon-button
             {:on-click #(swap! app-state assoc-in [:chat :open?] false)
              :title "Close chat"} [close]]]]]
         [dialog-content
          [chat-messages messages #(handle-edit %1 %2 current-message)]
          ;; Show editing indicator
          (when (is-editing?)
            [typography
             {:variant "caption" :sx {:color "warning.main" :px 2 :py 0.5}}
             "Editing message - all responses after this will be regenerated"])
          [chat-input current-message handle-send is-sending?]
          ;; Cancel edit button
          (when (is-editing?)
            [button
             {:variant "text"
              :size "small"
              :sx {:mt 1}
              :on-click #(handle-cancel current-message)} "Cancel Edit"])]]))))

(defn wine-chat-fab
  "Floating action button for wine chat"
  [app-state]
  [fab
   {:color "primary"
    :sx {:position "fixed" :bottom 16 :right 16 :z-index 1000}
    :on-click #(swap! app-state assoc-in [:chat :open?] true)} [chat]])

(defn wine-chat
  "Main wine chat component with FAB and dialog"
  [app-state]
  [:div [wine-chat-fab app-state] [chat-dialog app-state]])
