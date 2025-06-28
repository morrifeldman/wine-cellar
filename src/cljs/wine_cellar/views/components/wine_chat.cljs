(ns wine-cellar.views.components.wine-chat
  (:require [reagent.core :as r]
            [reagent-mui.material.fab :refer [fab]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.text-field :refer [text-field]]
            [wine-cellar.views.components.form :refer
             [uncontrolled-text-area-field]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.chat :refer [chat]]
            [reagent-mui.icons.close :refer [close]]
            [reagent-mui.icons.clear-all :refer [clear-all]]
            [reagent-mui.icons.edit :refer [edit]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
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
             :bottom edit-button-spacing
             :right edit-button-spacing
             :color "inherit"
             :opacity 0.7
             :&:hover {:opacity 1}}
        :on-click #(on-edit id text)}
       [edit {:sx {:font-size edit-icon-size}}]])]])

(defn chat-input
  "Chat input field with send button - uncontrolled for performance"
  [message-ref on-send disabled? reset-key]
  [box {:sx {:display "flex" :gap 1 :mt 2}}
   [uncontrolled-text-area-field
    {:label "Message"
     :initial-value ""
     :reset-key reset-key
     :input-ref message-ref
     :rows 3
     :helper-text "Ask me about your wines..."
     :sx {:flex-grow 1
          :& {:backgroundColor "background.paper"}
          "& .MuiOutlinedInput-root"
          {:backgroundColor "background.paper"
           :border "2px solid"
           :borderColor "primary.main"
           :borderRadius 2
           :&:hover {:borderColor "primary.dark"}
           :&.Mui-focused {:borderColor "primary.main"
                           :boxShadow "0 0 0 3px rgba(25, 118, 210, 0.1)"}}
          "& .MuiInputLabel-root" {:&.MuiInputLabel-shrink
                                   {:transform
                                    "translate(14px, -9px) scale(0.75)"}}}
     :placeholder "Type your message here..."
     :disabled @disabled?
     :InputLabelProps {:shrink true} ;; Always keep label shrunk to avoid
                                     ;; overlap
     :on-blur nil}]
   [button
    {:variant "contained"
     :disabled @disabled?
     :sx {:minWidth "80px"}
     :startIcon (when @disabled?
                  (r/as-element [circular-progress
                                 {:size 16 :sx {:color "secondary.light"}}]))
     :on-click #(when @message-ref
                  (let [message-text (.-value @message-ref)]
                    (when (seq (str message-text))
                      (on-send message-text)
                      (set! (.-value @message-ref) ""))))}
    [box
     {:sx {:color (if @disabled? "secondary.light" "inherit")
           :fontWeight (if @disabled? "600" "normal")
           :fontSize (if @disabled? "0.85rem" "1rem")}}
     (if @disabled? "Sending..." "Send")]]])

(defn chat-messages
  "Scrollable container for chat messages"
  [messages on-edit]
  (let [scroll-ref (r/atom nil)]
    (r/create-class
     {:component-did-update (fn [this]
                              (when @scroll-ref
                                (set! (.-scrollTop @scroll-ref)
                                      (.-scrollHeight @scroll-ref))))
      :reagent-render
      (fn [messages on-edit]
        [box
         {:ref #(reset! scroll-ref %)
          :sx {:height "400px"
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
             ^{:key (:id message)} [message-bubble message on-edit]))])})))

(defn send-chat-message
  "Send a message to the AI chat endpoint with conversation history"
  [message wines conversation-history callback]
  (tap> ["send-chat-message" message conversation-history])
  (api/send-chat-message message wines conversation-history callback))

(defn- handle-send-message
  "Handle sending a message to the AI assistant"
  [app-state message-text messages is-sending?]
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
           (reset! is-sending? false)))))))

(defn- use-edit-state
  "Hook for managing edit state"
  []
  (let [editing-message-id (r/atom nil)]
    {:editing-message-id editing-message-id
     :handle-edit (fn [message-id message-text message-ref]
                    (reset! editing-message-id message-id)
                    (when @message-ref
                      (set! (.-value @message-ref) message-text)
                      ;; Trigger input event to make Material-UI recognize
                      ;; the text
                      (let [input-event (js/Event. "input" #js {:bubbles true})]
                        (.dispatchEvent @message-ref input-event))
                      (.focus @message-ref)))
     :handle-cancel (fn [message-ref]
                      (reset! editing-message-id nil)
                      (when @message-ref (set! (.-value @message-ref) "")))
     :is-editing? #(some? @editing-message-id)}))

(defn- handle-edit-send
  "Handle sending an edited message"
  [app-state editing-message-id message-ref messages is-sending?]
  (when @message-ref
    (let [message-text (.-value @message-ref)]
      (if-let [message-idx (->> @messages
                                (keep-indexed
                                 #(when (= (:id %2) @editing-message-id) %1))
                                first)]
        (let [updated-message
              (assoc (nth @messages message-idx) :text message-text)]
          ;; Replace the edited message and remove messages after it
          (reset! messages (conj (vec (take message-idx @messages))
                                 updated-message))
          (reset! editing-message-id nil)
          (set! (.-value @message-ref) "")
          ;; Generate new AI response (without adding duplicate user
          ;; message)
          (reset! is-sending? true)
          (let [wines (if-let [current-wine-id (:selected-wine-id @app-state)]
                        (filter #(= (:id %) current-wine-id)
                                (:wines @app-state))
                        (filtered-sorted-wines app-state))]
            (send-chat-message
             (:text updated-message)
             wines
             @messages
             (fn [response]
               (let [ai-message {:id (random-uuid)
                                 :text response
                                 :is-user false
                                 :timestamp (.getTime (js/Date.))}]
                 (swap! messages conj ai-message)
                 (swap! app-state assoc-in [:chat :messages] @messages)
                 (reset! is-sending? false))))))
        ;; If message not found, just clear edit state
        (do (reset! editing-message-id nil)
            (set! (.-value @message-ref) ""))))))

(defn chat-dialog
  "Main chat dialog component"
  [app-state]
  (let [chat-state (:chat @app-state)
        messages (r/atom (:messages chat-state []))
        message-ref (r/atom nil)
        is-sending? (r/atom false)
        edit-state (use-edit-state)
        {:keys [editing-message-id handle-edit handle-cancel is-editing?]}
        edit-state
        handle-send
        (fn [message-text]
          (if (is-editing?)
            ;; Edit mode - replace message and regenerate response
            (handle-edit-send app-state
                              editing-message-id
                              message-ref
                              messages
                              is-sending?)
            ;; Normal mode - send new message
            (handle-send-message app-state message-text messages is-sending?)))]
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
          [chat-messages messages #(handle-edit %1 %2 message-ref)]
          ;; Show editing indicator
          (when (is-editing?)
            [typography
             {:variant "caption" :sx {:color "warning.main" :px 2 :py 0.5}}
             "Editing message - all responses after this will be regenerated"])
          [chat-input message-ref handle-send is-sending? "chat-input"]
          ;; Cancel edit button
          (when (is-editing?)
            [button
             {:variant "text"
              :size "small"
              :sx {:mt 1}
              :on-click #(handle-cancel message-ref)} "Cancel Edit"])]]))))

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
