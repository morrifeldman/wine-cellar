(ns wine-cellar.views.components.wine-chat
  (:require [reagent.core :as r]
            [reagent-mui.material.fab :refer [fab]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.chat :refer [chat]]
            [reagent-mui.icons.close :refer [close]]
            [reagent-mui.icons.clear-all :refer [clear-all]]
            [reagent-mui.icons.edit :refer [edit]]
            [reagent-mui.icons.send :refer [send]]
            [reagent-mui.icons.camera-alt :refer [camera-alt]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [wine-cellar.views.components.image-upload :refer [camera-capture create-thumbnail]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.filters :refer [filtered-sorted-wines]]))

;; Constants
(def ^:private edit-icon-size "0.8rem")
(def ^:private edit-button-spacing 4)

(defn- handle-clipboard-image
  "Process an image file/blob from clipboard and set it as attached image"
  [file-or-blob attached-image]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (let [data-url (-> e .-target .-result)
                  img (js/Image.)]
              (set! (.-onload img)
                    (fn []
                      ;; Convert to JPEG format
                      (let [canvas (js/document.createElement "canvas")
                            ctx (.getContext canvas "2d")]
                        (set! (.-width canvas) (.-width img))
                        (set! (.-height canvas) (.-height img))
                        (.drawImage ctx img 0 0)
                        (let [jpeg-data-url (.toDataURL canvas "image/jpeg" 0.85)]
                          (reset! attached-image jpeg-data-url)))))
              (set! (.-src img) data-url))))
    (.readAsDataURL reader file-or-blob)))

(defn- handle-paste-event
  "Handle paste events to detect and process clipboard images"
  [event attached-image]
  (when-let [items (.-items (.-clipboardData event))]
    (dotimes [i (.-length items)]
      (let [item (aget items i)]
        (when (and (.-type item) 
                   (.startsWith (.-type item) "image/"))
          (when-let [file (.getAsFile item)]
            (handle-clipboard-image file attached-image)))))))

(defn message-bubble
  "Renders a single chat message bubble"
  [{:keys [text is-user timestamp id]} on-edit & [ref-callback]]
  [box
   {:ref ref-callback
    :sx {:display "flex"
         :justify-content (if is-user "flex-end" "flex-start")
         :mb 1}}
   [paper
    {:elevation 2
     :sx {:p 2
          :max-width "80%"
          :background-color (if is-user "primary.main" "container.main")
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
  "Chat input field with send button and camera button - uncontrolled for performance"
  [message-ref on-send disabled? reset-key app-state on-image-capture
   attached-image on-image-remove]
  (let [has-image? @attached-image
        container-ref (r/atom nil)]
    ;; Auto-scroll when image is added - just bring into view, don't force to bottom
    (when has-image?
      (js/setTimeout 
        #(when @container-ref
           (.scrollIntoView @container-ref #js {:behavior "smooth" :block "nearest"}))
        100))
    [box {:sx {:display "flex" :flex-direction "column" :gap 1 :mt 2}
          :ref #(reset! container-ref %)}
     ;; Image preview if attached
     (when has-image?
       [box
        {:sx {:mb 1
              :p 1
              :border "1px solid"
              :border-color "divider"
              :border-radius 1}}
        [box
         {:sx {:display "flex"
               :justify-content "space-between"
               :align-items "center"
               :mb 1}}
         [typography {:variant "caption" :color "text.secondary"}
          "Attached image:"]
         [icon-button
          {:size "small"
           :on-click on-image-remove
           :sx {:color "secondary.main"}} [close {:sx {:font-size "0.8rem"}}]]]
        [box
         {:component "img"
          :src has-image?
          :sx {:max-width "100%"
               :max-height "150px"
               :object-fit "contain"
               :border-radius 1}}]])
     [mui-text-field/text-field
      {:multiline true
       :rows 5
       :fullWidth true
       :variant "outlined"
       :size "small"
       :margin "dense"
       :inputRef #(when %
                    (reset! message-ref %)
                    ;; Restore draft message when component mounts
                    (when-let [draft (get-in @app-state [:chat :draft-message])]
                      (set! (.-value %) draft)))
       :sx {"& .MuiOutlinedInput-root" {:backgroundColor "background.default"
                                        :border "none"
                                        :borderRadius 2}}
       :placeholder (if has-image?
                      "Add a message to go with your image..."
                      (let [is-mobile? (and js/navigator.maxTouchPoints (> js/navigator.maxTouchPoints 0))]
                        (if is-mobile?
                          "Type your message here..."
                          "Type your message here... (or paste a screenshot)")))
       :disabled @disabled?
       :on-blur nil
       :on-paste #(handle-paste-event % attached-image)}]
     [box {:sx {:display "flex" :justify-content "flex-end" :gap 1}}
      ;; Detect if mobile device
      (let [is-mobile? (and js/navigator.maxTouchPoints (> js/navigator.maxTouchPoints 0))]
        [:<>
         ;; Hidden file input
         [:input {:type "file"
                  :accept "image/*"
                  :style {:display "none"}
                  :id "photo-picker-input"
                  :on-change #(when-let [file (-> % .-target .-files (aget 0))]
                                (handle-clipboard-image file attached-image))}]
         
         ;; Mobile: Camera + Photos buttons
         (when is-mobile?
           [:<>
            [button
             {:variant "outlined"
              :disabled @disabled?
              :sx {:minWidth "60px" :px 1}
              :startIcon (r/as-element [camera-alt {:size 14}])
              :on-click on-image-capture} "Camera"]
            [button
             {:variant "outlined" 
              :disabled @disabled?
              :sx {:minWidth "60px" :px 1}
              :on-click #(when-let [input (js/document.getElementById "photo-picker-input")]
                           (.click input))} "Photos"]])
         
         ;; Desktop: Camera + File picker with better labels  
         (when (not is-mobile?)
           [:<>
            [button
             {:variant "outlined"
              :disabled @disabled?
              :sx {:minWidth "60px" :px 1}
              :startIcon (r/as-element [camera-alt {:size 14}])
              :on-click on-image-capture} "Camera"]
            [button
             {:variant "outlined"
              :disabled @disabled?
              :sx {:minWidth "60px" :px 1}
              :on-click #(when-let [input (js/document.getElementById "photo-picker-input")]
                           (.click input))} "Upload"]])])
      [button
       {:variant "contained"
        :disabled @disabled?
        :sx {:minWidth "60px" :px 1}
        :startIcon (if @disabled?
                     (r/as-element [circular-progress
                                    {:size 14 :sx {:color "secondary.light"}}])
                     (r/as-element [send {:size 14}]))
        :on-click
        #(when @message-ref
           (let [message-text (.-value @message-ref)]
             (when (or (seq (str message-text)) has-image?)
               (on-send message-text)
               (set! (.-value @message-ref) "")
               ;; Clear draft message after sending
               (swap! app-state update :chat dissoc :draft-message))))}
       [box
        {:sx {:color (if @disabled? "text.disabled" "inherit")
              :fontWeight (if @disabled? "600" "normal")
              :fontSize (if @disabled? "0.8rem" "0.9rem")}}
        (if @disabled? "Sending..." "Send")]]]]))

(defn chat-messages
  "Scrollable container for chat messages"
  [messages on-edit]
  (let [scroll-ref (r/atom nil)
        last-ai-message-ref (r/atom nil)]
    (r/create-class
     {:component-did-update (fn [this]
                              (when @last-ai-message-ref
                                (.scrollIntoView @last-ai-message-ref
                                                 #js {:behavior "smooth"
                                                      :block "start"})))
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
         (let [current-messages @messages]
           (if (empty? current-messages)
             [typography
              {:variant "body2"
               :sx {:text-align "center" :color "text.secondary" :mt 2}}
              "Start a conversation about your wine cellar..."]
             (doall (for [message current-messages]
                      (let [is-last-message? (= message
                                                (last current-messages))]
                        ^{:key (:id message)}
                        [message-bubble message on-edit
                         (when is-last-message?
                           #(reset! last-ai-message-ref %))])))))])})))

(defn send-chat-message
  "Send a message to the AI chat endpoint with conversation history and optional image"
  ([message wines conversation-history callback]
   (send-chat-message message wines conversation-history nil callback))
  ([message wines conversation-history image callback]
   (tap> ["send-chat-message" message conversation-history])
   (api/send-chat-message message wines conversation-history image callback)))

(defn- handle-send-message
  "Handle sending a message to the AI assistant with optional image"
  [app-state message-text messages is-sending? & [image]]
  (when (and (not @is-sending?) (or (seq message-text) image))
    (reset! is-sending? true)
    (let [user-message {:id (random-uuid)
                        :text (or message-text "")
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
       image
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
        show-camera? (r/atom false)
        pending-image (r/atom nil)
        dialog-content-ref (r/atom nil)
        dialog-opened (r/atom false)
        edit-state (use-edit-state)
        {:keys [editing-message-id handle-edit handle-cancel is-editing?]}
        edit-state
        handle-send (fn [message-text]
                      (if (is-editing?)
                        ;; Edit mode - replace message and regenerate
                        ;; response
                        (handle-edit-send app-state
                                          editing-message-id
                                          message-ref
                                          messages
                                          is-sending?)
                        ;; Normal mode - send new message with attached
                        ;; image
                        (do (handle-send-message app-state
                                                 message-text
                                                 messages
                                                 is-sending?
                                                 @pending-image)
                            (reset! pending-image nil))))
        handle-image-capture (fn [] (reset! show-camera? true))
        handle-camera-capture (fn [image-data]
                                (reset! show-camera? false)
                                (reset! pending-image (:label_image
                                                       image-data)))
        handle-camera-cancel
        (fn [] (reset! show-camera? false) (reset! pending-image nil))
        handle-image-remove (fn [] (reset! pending-image nil))]
    (fn [app-state]
      (let [chat-state (:chat @app-state)
            is-open (:open? chat-state false)]
        ;; Auto-scroll to bottom only when dialog first opens
        (when (and is-open (not @dialog-opened) @dialog-content-ref)
          (reset! dialog-opened true)
          (js/setTimeout 
            #(when @dialog-content-ref
               (.scrollTo @dialog-content-ref 0 (.-scrollHeight @dialog-content-ref)))
            200))
        ;; Reset opened flag when dialog closes
        (when (not is-open)
          (reset! dialog-opened false))
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
              :title "Clear chat history"
              :sx {:color "secondary.main"}} [clear-all]]
            [icon-button
             {:on-click #(do
                           ;; Save draft message before closing
                           (when @message-ref
                             (let [draft-text (.-value @message-ref)]
                               (swap! app-state assoc-in
                                 [:chat :draft-message]
                                 draft-text)))
                           (swap! app-state assoc-in [:chat :open?] false))
              :title "Close chat"
              :sx {:color "secondary.main"}} [close]]]]]
         [dialog-content
          {:ref #(reset! dialog-content-ref %)}
          ;; Camera modal
          (when @show-camera?
            [camera-capture handle-camera-capture handle-camera-cancel])
          [chat-messages messages #(handle-edit %1 %2 message-ref)]
          ;; Show editing indicator
          (when (is-editing?)
            [typography
             {:variant "caption" :sx {:color "warning.main" :px 2 :py 0.5}}
             "Editing message - all responses after this will be regenerated"])
          [chat-input message-ref handle-send is-sending? "chat-input" app-state
           handle-image-capture pending-image handle-image-remove]
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
