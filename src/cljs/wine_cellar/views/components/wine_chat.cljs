(ns wine-cellar.views.components.wine-chat
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [reagent-mui.material.fab :refer [fab]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.chat :refer [chat]]
            [reagent-mui.icons.close :refer [close]]
            [reagent-mui.icons.clear-all :refer [clear-all]]
            [reagent-mui.icons.edit :refer [edit]]
            [reagent-mui.icons.star :refer [star]]
            [reagent-mui.icons.star-border :refer [star-border]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.send :refer [send]]
            [reagent-mui.icons.camera-alt :refer [camera-alt]]
            [reagent-mui.icons.photo-library :refer [photo-library]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [wine-cellar.views.components.image-upload :refer [camera-capture]]
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

(declare persist-conversation-message!)

(defn- open-conversation!
  ([app-state messages conversation]
   (open-conversation! app-state messages conversation true))
  ([app-state messages {:keys [id] :as conversation} close-sidebar?]
   (when id
     (when close-sidebar?
       (swap! app-state assoc-in [:chat :sidebar-open?] false))
     (swap! app-state assoc-in [:chat :active-conversation-id] id)
     (swap! app-state assoc-in [:chat :active-conversation] conversation)
     (reset! messages [])
     (swap! app-state assoc-in [:chat :messages] [])
     (api/fetch-conversation-messages! app-state id))))

(defn- ensure-conversation!
  "Ensure an active conversation exists before persisting messages."
  [app-state wines callback]
  (let [conversation-id (get-in @app-state [:chat :active-conversation-id])
        creating? (get-in @app-state [:chat :creating-conversation?])]
    (cond
      conversation-id (do
                        (swap! app-state assoc-in [:chat :active-conversation-id] conversation-id)
                        (callback conversation-id))
      creating? (js/setTimeout #(ensure-conversation! app-state wines callback) 100)
      :else
      (let [wine-ids (->> wines (map :id) (remove nil?) vec)
            payload (cond-> {}
                       (seq wine-ids) (assoc :wine_ids wine-ids))]
        (swap! app-state assoc-in [:chat :creating-conversation?] true)
        (api/create-conversation!
         app-state
         payload
         (fn [{:keys [success conversation error]}]
           (swap! app-state assoc-in [:chat :creating-conversation?] false)
           (if success
             (callback (:id conversation))
             (tap> ["ensure-conversation-failed" error]))))))))

(defn- persist-conversation-message!
  "Persist a chat message (user or AI) to the backend conversation store."
  ([app-state wines message-map]
   (persist-conversation-message! app-state wines message-map nil))
  ([app-state wines message-map after-save]
   (ensure-conversation!
    app-state
    wines
    (fn [conversation-id]
      (api/append-conversation-message!
       app-state
       conversation-id
       message-map
       (fn [{:keys [success error]}]
         (if success
           (when after-save (after-save conversation-id))
           (tap> ["conversation-message-persist-failed" error]))))))))

(defn- context-wines
  "Derive the set of wines currently in chat context."
  [app-state]
  (let [state @app-state
        selected-id (:selected-wine-id state)
        wines (or (:wines state) [])]
    (cond
      selected-id (into [] (filter #(= (:id %) selected-id) wines))
      :else (vec (or (filtered-sorted-wines app-state) [])))))

(defn- context-label-element
  [count]
  (cond
    (zero? count) "No wines in context"
    (= count 1) [:<> [:span {:style {:fontWeight 700}} "1"] " wine in context"]
    :else [:<> [:span {:style {:fontWeight 700}} (str count)] " wines in context"]))

(defn- context-indicator-style
  [count]
  (let [base-label (context-label-element count)]
    (cond
      (zero? count) {:color "text.secondary" :label base-label}
      (<= count 15) {:color "success.main" :label base-label}
      (<= count 50) {:color "warning.main" :label base-label}
      :else {:color "error.main" :label base-label})))

(defn- mobile?
  []
  (boolean
   (and (exists? js/navigator)
        (pos? (or (.-maxTouchPoints js/navigator) 0)))))

(defn- normalize-provider
  [value]
  (cond
    (keyword? value) value
    (string? value) (-> value string/lower-case keyword)
    (nil? value) :anthropic
    :else :anthropic))

(defn- provider-label
  [provider]
  (case (normalize-provider provider)
    :openai "ChatGPT"
    :anthropic "Claude"
    (-> provider name string/capitalize)))

(defn- toggle-provider!
  [app-state]
  (swap! app-state
         update-in
         [:chat :provider]
         (fn [current]
           (let [provider (normalize-provider current)]
             (case provider
               :anthropic :openai
               :openai :anthropic
               :anthropic)))))

(defn- provider-toggle
  [app-state]
  (let [provider (normalize-provider (get-in @app-state [:chat :provider]))
        is-mobile? (mobile?)]
    [button
     {:variant "outlined"
      :size "small"
      :on-click #(toggle-provider! app-state)
      :sx {:textTransform "none"
            :fontSize "0.75rem"
            :px 1.5
            :py 0.25
            :borderColor "divider"
            :color "text.primary"
           :minWidth (if is-mobile? "96px" "120px")
           :height "28px"
           :lineHeight 1.2}
      :title "Toggle AI provider"}
     (str "AI: " (provider-label provider))]))

(defn- conversation-label
  [{:keys [title id last_message_at]}]
  (or title
      (when last_message_at (.toLocaleString (js/Date. last_message_at)))
      (when id (str "Conversation " id))
      "Conversation"))

(defn- conversation-action-buttons
  [{:keys [pinned] :as conversation}
   {:keys [on-pin on-rename on-delete pinning? renaming? deleting?]}]
  [box {:sx {:display "flex" :align-items "center" :gap 0.25}}
   (when on-pin
     [icon-button
      {:size "small"
       :on-click (fn [event]
                   (.stopPropagation event)
                   (on-pin conversation))
       :disabled pinning?
       :sx {:color (if (true? pinned) "warning.main" "text.secondary")}}
      (cond
        pinning? [circular-progress {:size 16}]
        (true? pinned) [star {:fontSize "small"}]
        :else [star-border {:fontSize "small"}])])
   (when on-rename
     [icon-button
      {:size "small"
       :on-click (fn [event]
                   (.stopPropagation event)
                   (on-rename conversation))
       :disabled renaming?
       :sx {:color "text.secondary"}}
      (if renaming?
        [circular-progress {:size 16}]
        [edit])])
   (when on-delete
     [icon-button
      {:size "small"
       :on-click (fn [event]
                   (.stopPropagation event)
                   (on-delete conversation))
       :disabled deleting?
       :sx {:color "text.secondary"}}
      (if deleting?
        [circular-progress {:size 16}]
        [delete])])])

(defn- conversation-row
  [app-state messages
   {:keys [active-id deleting-id renaming-id pinning-id on-delete on-rename on-pin]
    :as opts}
   {:keys [id] :as conversation}]
  (let [active? (= id active-id)
        deleting? (= id deleting-id)
        renaming? (= id renaming-id)
        pinning? (= id pinning-id)]
    [box
     {:on-click #(open-conversation! app-state messages conversation true)
      :sx {:px 2 :py 1.5 :cursor "pointer"
           :background-color (if active? "action.hover" "transparent")
           :border-bottom "1px solid"
           :border-color "divider"}}
     [box
      {:sx {:display "flex"
            :align-items "center"
            :justify-content "space-between"
            :gap 1}}
      [typography {:variant "body2"
                   :sx {:fontWeight (if active? "600" "500")}}
       (conversation-label conversation)]
      (conversation-action-buttons
       conversation
       {:on-pin on-pin
        :on-rename on-rename
        :on-delete on-delete
        :pinning? pinning?
        :renaming? renaming?
        :deleting? deleting?})]]))

(defn- conversations->items
  [app-state messages opts conversations]
  (into [] (map #(conversation-row app-state messages opts %) conversations)))

(defn- conversation-content
  [status items]
  (case status
    :loading [box {:sx {:display "flex" :justify-content "center" :py 3}}
              [circular-progress {:size 24}]]
    :empty [typography {:variant "body2"
                        :sx {:px 2 :py 2 :color "text.secondary"}}
            "No conversations yet"]
    (into [:<>] items)))

(defn- conversation-sidebar
  [app-state messages {:keys [open? conversations loading? active-id deleting-id renaming-id pinning-id scroll-ref scroll-requested? on-delete on-rename on-pin]}]
  (if open?
    (let [items (conversations->items
                 app-state
                 messages
                 {:active-id active-id
                  :deleting-id deleting-id
                  :renaming-id renaming-id
                  :pinning-id pinning-id
                  :on-delete on-delete
                  :on-rename on-rename
                  :on-pin on-pin}
                 conversations)
          status (cond
                   loading? :loading
                   (empty? conversations) :empty
                   :else :items)
          content (conversation-content status items)]
      [grid {:item true :xs 12 :md 4}
       [box
        {:sx {:border "1px solid"
              :border-color "divider"
              :border-radius 1
              :overflow "hidden"
              :background-color "background.default"}}
        [typography {:variant "subtitle2"
                     :sx {:px 2 :py 1 :border-bottom "1px solid"
                          :border-color "divider"}}
         "Conversations"]
        [box {:sx {:max-height "320px" :overflow "auto"}
               :ref (fn [el]
                      (when scroll-ref (reset! scroll-ref el))
                      (when (and scroll-requested?
                                 @scroll-requested?
                                 el)
                        (.scrollTo el 0 0)
                        (reset! scroll-requested? false)))}
         content]]])
    (do
      (when scroll-requested? (reset! scroll-requested? false))
      (when scroll-ref (reset! scroll-ref nil))
      nil)))

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
    (when has-image?
      (js/setTimeout
       #(when @container-ref
          (.scrollIntoView @container-ref #js {:behavior "smooth" :block "nearest"}))
       100))
    [box {:sx {:display "flex" :flex-direction "column" :gap 1 :mt 2}
          :ref #(reset! container-ref %)}
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
       :on-paste #(handle-paste-event % attached-image)}]
     [box {:sx {:display "flex"
                :justify-content "flex-end"
                :align-items "center"
                :gap 1
                :flex-wrap "wrap"}}
      (let [is-mobile? (and js/navigator.maxTouchPoints (> js/navigator.maxTouchPoints 0))
            trigger-upload #(when-let [input (js/document.getElementById "photo-picker-input")]
                              (.click input))]
        [:<>
         [:input {:type "file"
                  :accept "image/*"
                  :style {:display "none"}
                  :id "photo-picker-input"
                  :on-change #(when-let [file (-> % .-target .-files (aget 0))]
                                (handle-clipboard-image file attached-image))}]
         (when is-mobile?
           [button
            {:variant "outlined"
             :size "small"
             :disabled @disabled?
             :startIcon (r/as-element [camera-alt {:size 14}])
             :on-click #(on-image-capture nil)
             :sx {:minWidth "90px"}}
            "Camera"])
         [button
          {:variant "outlined"
           :size "small"
           :disabled @disabled?
           :startIcon (r/as-element [photo-library {:size 14}])
           :on-click trigger-upload
           :sx {:minWidth (if is-mobile? "90px" "100px")}}
          (if is-mobile? "Photos" "Upload")]
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
                  (swap! app-state update :chat dissoc :draft-message))))}
          [box
           {:sx {:color (if @disabled? "text.disabled" "inherit")
                 :fontWeight (if @disabled? "600" "normal")
                 :fontSize (if @disabled? "0.8rem" "0.9rem")}}
           (if @disabled? "Sending..." "Send")]]])]]))

(defn chat-messages
  "Scrollable container for chat messages"
  [messages on-edit]
  (let [scroll-ref (r/atom nil)
        last-ai-message-ref (r/atom nil)]
    (r/create-class
     {:component-did-update (fn [_]
                              (when @last-ai-message-ref
                                (.scrollIntoView @last-ai-message-ref
                                                 #js {:behavior "smooth"
                                                      :block "start"})))
      :reagent-render
      (fn [messages on-edit]
        [box
         {:ref #(reset! scroll-ref %)
          :sx {:height "360px"
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
                      (let [is-last-message? (= message (last current-messages))]
                        ^{:key (:id message)}
                        [message-bubble message on-edit
                         (when is-last-message?
                           #(reset! last-ai-message-ref %))])))))])})))

(defn send-chat-message
  "Send a message to the AI chat endpoint with conversation history, provider, and optional image"
  ([message wines conversation-history provider callback]
   (send-chat-message message wines conversation-history provider nil callback))
  ([message wines conversation-history provider image callback]
   (tap> ["send-chat-message" message conversation-history {:provider provider}])
   (api/send-chat-message message wines conversation-history provider image callback)))

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
                  (filter #(= (:id %) current-wine-id) (:wines @app-state))
                  (filtered-sorted-wines app-state))
          provider (get-in @app-state [:chat :provider] :anthropic)]
      (swap! messages conj user-message)
      (swap! app-state assoc-in [:chat :messages] @messages)
      (persist-conversation-message!
       app-state
       wines
       (cond-> {:is_user true
                :content (or message-text "")}
         image (assoc :image_data image)))
      (send-chat-message
       message-text
       wines
       @messages
       provider
       image
       (fn [response]
         (let [ai-message {:id (random-uuid)
                           :text response
                           :is-user false
                           :timestamp (.getTime (js/Date.))}]
           (swap! messages conj ai-message)
           (swap! app-state assoc-in [:chat :messages] @messages)
           (persist-conversation-message!
            app-state
            wines
            {:is_user false :content response}
            (fn [_]
              (api/load-conversations! app-state {:force? true})))
           (reset! is-sending? false)))))))

(defn- use-edit-state
  []
  (let [editing-message-id (r/atom nil)]
    {:editing-message-id editing-message-id
     :handle-edit (fn [message-id message-text message-ref]
                    (reset! editing-message-id message-id)
                    (when @message-ref
                      (set! (.-value @message-ref) message-text)
                      (let [input-event (js/Event. "input" #js {:bubbles true})]
                        (.dispatchEvent @message-ref input-event))
                      (.focus @message-ref)))
     :handle-cancel (fn [message-ref]
                      (reset! editing-message-id nil)
                      (when @message-ref (set! (.-value @message-ref) "")))
     :is-editing? #(some? @editing-message-id)}))

(defn- handle-edit-send
  [app-state editing-message-id message-ref messages is-sending?]
  (when @message-ref
    (let [message-text (.-value @message-ref)]
      (if-let [message-idx (->> @messages
                                (keep-indexed
                                 #(when (= (:id %2) @editing-message-id) %1))
                                first)]
        (let [updated-message
              (assoc (nth @messages message-idx) :text message-text)]
          (reset! messages (conj (vec (take message-idx @messages))
                                 updated-message))
          (reset! editing-message-id nil)
          (set! (.-value @message-ref) "")
          (reset! is-sending? true)
          (let [wines (if-let [current-wine-id (:selected-wine-id @app-state)]
                        (filter #(= (:id %) current-wine-id)
                                (:wines @app-state))
                        (filtered-sorted-wines app-state))
                provider (get-in @app-state [:chat :provider] :anthropic)]
            (persist-conversation-message!
             app-state
             wines
             {:is_user true
              :content (or (:text updated-message) "")})
            (send-chat-message
             (:text updated-message)
             wines
             @messages
             provider
             (fn [response]
               (let [ai-message {:id (random-uuid)
                                 :text response
                                 :is-user false
                                 :timestamp (.getTime (js/Date.))}]
                 (swap! messages conj ai-message)
                 (swap! app-state assoc-in [:chat :messages] @messages)
                 (persist-conversation-message!
                  app-state
                  wines
                  {:is_user false :content response}
                  (fn [_]
                    (api/load-conversations! app-state {:force? true})))
                 (reset! is-sending? false))))))
        (do (reset! editing-message-id nil)
            (set! (.-value @message-ref) ""))))))

(defn clear-chat!
  [app-state messages]
  (reset! messages [])
  (swap! app-state
         (fn [state]
           (-> state
               (assoc-in [:chat :messages] [])
               (assoc-in [:chat :active-conversation-id] nil)
               (assoc-in [:chat :active-conversation] nil)
               (assoc-in [:chat :messages-loading?] false)
               (assoc-in [:chat :creating-conversation?] false)
               (assoc-in [:chat :draft-message] nil)))))

(defn close-chat!
  [app-state message-ref]
  (when-let [node @message-ref]
    (swap! app-state assoc-in [:chat :draft-message] (.-value node)))
  (swap! app-state assoc-in [:chat :open?] false))

(defn delete-conversation-with-confirm!
  [app-state {:keys [id] :as conversation}]
  (when (and id
             (js/confirm (str "Delete conversation \""
                              (conversation-label conversation)
                              "\"? This cannot be undone.")))
    (api/delete-conversation! app-state id)))

(defn rename-conversation-with-prompt!
  [app-state {:keys [id title]}]
  (when id
    (when-let [new-title (js/prompt "Rename conversation" (or title ""))]
      (let [trimmed (string/trim new-title)]
        (when (and (not (string/blank? trimmed))
                   (not= trimmed title))
          (api/rename-conversation! app-state id trimmed))))))

(defn toggle-pin!
  [app-state {:keys [id pinned]}]
  (when id
    (api/set-conversation-pinned! app-state id (not (true? pinned)))))

(defn chat-dialog-header
  [{:keys [app-state messages message-ref conversation-loading? sidebar-open?
           on-toggle-sidebar context-label context-color]}]
  (let [is-mobile? (mobile?)
        conversation-toggle
        (if is-mobile?
          [icon-button
           {:on-click on-toggle-sidebar
            :title (if sidebar-open? "Hide conversations" "Show conversations")
            :sx {:color "secondary.main"}}
           (if conversation-loading?
             [circular-progress {:size 18}]
             [chat {:fontSize "small"}])]
          [button
           {:variant "outlined"
            :size "small"
            :disabled conversation-loading?
            :on-click on-toggle-sidebar
            :sx {:textTransform "none"
                 :display "flex"
                 :alignItems "center"
                 :gap 1}}
           (if conversation-loading?
             [circular-progress {:size 16 :sx {:color "primary.main"}}]
             (if sidebar-open? "Hide Conversations" "Show Conversations"))])]
    [dialog-title
     [box
      {:sx {:display "flex"
            :justify-content "space-between"
            :align-items "center"
            :gap (if is-mobile? 0.5 1)
            :flex-wrap "nowrap"}}
      [box {:sx {:display "flex"
                 :align-items "center"
                 :gap 0.75
                 :minWidth 0}}
       [box {:sx {:display "flex"
                  :flex-direction "column"
                  :align-items "flex-start"
                  :gap 0.25}}
        [provider-toggle app-state]
       (when context-label
         [typography
          {:variant "caption"
           :sx {:color context-color
                :fontWeight 400
                :fontSize "0.7rem"
                :lineHeight 1.2}}
           context-label])]]
      [box {:sx {:display "flex"
                 :align-items "center"
                 :gap (if is-mobile? 0.5 1)}}
       conversation-toggle
       [icon-button
        {:on-click #(clear-chat! app-state messages)
         :title "Clear chat history"
         :sx {:color "secondary.main"}}
        [clear-all]]
       [icon-button
        {:on-click #(close-chat! app-state message-ref)
         :title "Close chat"
         :sx {:color "secondary.main"}}
        [close]]]]]))

(defn chat-main-column
  [{:keys [sidebar-open?
           show-camera?
           handle-camera-capture
           handle-camera-cancel
           messages
           message-edit-handler
           handle-send
           is-sending?
           app-state
           handle-image-capture
           pending-image
           handle-image-remove
           message-ref
           is-editing?
           handle-cancel]}]
  (let [components (concat
                    (when @show-camera?
                      [[camera-capture handle-camera-capture handle-camera-cancel]])
                    [[chat-messages messages message-edit-handler]]
                    (when (is-editing?)
                      [[typography
                        {:variant "caption"
                         :sx {:color "warning.main" :px 2 :py 0.5}}
                        "Editing message - all responses after this will be regenerated"]])
                    [[chat-input message-ref
                                 handle-send
                                 is-sending?
                                 "chat-input"
                                 app-state
                                 handle-image-capture
                                 pending-image
                                 handle-image-remove]]
                    (when (is-editing?)
                      [[button
                        {:variant "text"
                         :size "small"
                         :sx {:mt 1}
                         :on-click #(handle-cancel message-ref)}
                        "Cancel Edit"]]))]
    (into [grid {:item true :xs 12 :md (if sidebar-open? 8 12)}]
          components)))

(defn chat-dialog-content
  [{:keys [dialog-content-ref sidebar main-column]}]
  [dialog-content
   {:ref #(reset! dialog-content-ref %)}
   (into [grid {:container true :spacing 2}]
         (cond-> []
           sidebar (conj sidebar)
           true (conj main-column)))])

(defn chat-dialog-shell
  [{:keys [app-state is-open header-props content-props]}]
  [dialog
   {:open is-open
    :on-close #(swap! app-state assoc-in [:chat :open?] false)
    :max-width "md"
    :full-width true}
   (chat-dialog-header header-props)
   (chat-dialog-content content-props)])

(defn chat-dialog
  "Main chat dialog component"
  [app-state]
  (let [chat-state (:chat @app-state)
        messages (r/atom (vec (or (:messages chat-state) [])))
        message-ref (r/atom nil)
        is-sending? (r/atom false)
        show-camera? (r/atom false)
        pending-image (r/atom nil)
        dialog-content-ref (r/atom nil)
        dialog-opened (r/atom false)
        sidebar-scroll-ref (r/atom nil)
        sidebar-scroll-requested? (r/atom false)
        edit-state (use-edit-state)
        {:keys [editing-message-id handle-edit handle-cancel is-editing?]} edit-state
        handle-send (fn [message-text]
                      (if (is-editing?)
                        (handle-edit-send app-state
                                          editing-message-id
                                          message-ref
                                          messages
                                          is-sending?)
                        (do (handle-send-message app-state
                                                 message-text
                                                 messages
                                                 is-sending?
                                                 @pending-image)
                            (reset! pending-image nil))))
        handle-image-capture (fn [] (reset! show-camera? true))
        handle-camera-capture (fn [image-data]
                                (reset! show-camera? false)
                                (reset! pending-image (:label_image image-data)))
        handle-camera-cancel (fn [] (reset! show-camera? false) (reset! pending-image nil))
        handle-image-remove (fn [] (reset! pending-image nil))
        message-edit-handler (fn [id text]
                               (handle-edit id text message-ref))]
    (fn [app-state]
      (let [chat-state (:chat @app-state)
            is-open (:open? chat-state false)
            sidebar-open? (:sidebar-open? chat-state)
            conversation-loading? (:conversation-loading? chat-state)
            messages-loading? (:messages-loading? chat-state)
            conversations (:conversations chat-state)
            active-id (:active-conversation-id chat-state)
            deleting-id (:deleting-conversation-id chat-state)
            pinning-id (:pinning-conversation-id chat-state)
            renaming-id (:renaming-conversation-id chat-state)
            conversation-messages (vec (or (:messages chat-state) []))
            context-count (count (context-wines app-state))
            {:keys [color label]} (context-indicator-style context-count)
            toggle-sidebar! (fn []
                              (let [opening? (not sidebar-open?)]
                                (if opening?
                                  (do
                                    (reset! sidebar-scroll-requested? true)
                                    (js/setTimeout
                                     #(when-let [el @dialog-content-ref]
                                        (.scrollTo el 0 0))
                                     50))
                                  (reset! sidebar-scroll-requested? false))
                                (reset! sidebar-scroll-ref nil)
                                (swap! app-state update-in [:chat :sidebar-open?] not)))
            sidebar (conversation-sidebar app-state
                                          messages
                                          {:open? sidebar-open?
                                           :conversations conversations
                                           :loading? conversation-loading?
                                           :active-id active-id
                                           :deleting-id deleting-id
                                           :renaming-id renaming-id
                                           :pinning-id pinning-id
                                           :scroll-ref sidebar-scroll-ref
                                           :scroll-requested? sidebar-scroll-requested?
                                           :on-delete #(delete-conversation-with-confirm! app-state %)
                                           :on-rename #(rename-conversation-with-prompt! app-state %)
                                           :on-pin #(toggle-pin! app-state % )})
            main-column (chat-main-column {:sidebar-open? sidebar-open?
                                           :show-camera? show-camera?
                                           :handle-camera-capture handle-camera-capture
                                           :handle-camera-cancel handle-camera-cancel
                                           :messages messages
                                           :message-edit-handler message-edit-handler
                                           :handle-send handle-send
                                           :is-sending? is-sending?
                                           :app-state app-state
                                           :handle-image-capture handle-image-capture
                                           :pending-image pending-image
                                           :handle-image-remove handle-image-remove
                                           :message-ref message-ref
                                           :is-editing? is-editing?
                                           :handle-cancel handle-cancel})
            header-props {:app-state app-state
                          :messages messages
                          :message-ref message-ref
                          :conversation-loading? conversation-loading?
                          :sidebar-open? sidebar-open?
                          :on-toggle-sidebar toggle-sidebar!
                          :context-label label
                          :context-color color}
            content-props {:dialog-content-ref dialog-content-ref
                           :sidebar sidebar
                           :main-column main-column}]
        (when (not= @messages conversation-messages)
          (reset! messages conversation-messages))
        (when (and is-open active-id (not messages-loading?) (empty? conversation-messages))
          (api/fetch-conversation-messages! app-state active-id))
        (when (and is-open (not @dialog-opened) @dialog-content-ref)
          (reset! dialog-opened true)
          (js/setTimeout
            #(when @dialog-content-ref
               (.scrollTo @dialog-content-ref 0 (.-scrollHeight @dialog-content-ref)))
            200))
        (when (not is-open)
          (reset! dialog-opened false))
        (chat-dialog-shell {:app-state app-state
                            :is-open is-open
                            :header-props header-props
                            :content-props content-props})))))

(defn wine-chat-fab
  "Floating action button for wine chat"
  [app-state]
  [fab
   {:color "primary"
    :sx {:position "fixed" :bottom 16 :right 16 :z-index 1000}
    :on-click #(do (swap! app-state assoc-in [:chat :open?] true)
                   (api/load-conversations! app-state {:force? true}))} [chat]])

(defn wine-chat
  "Main wine chat component with FAB and dialog"
  [app-state]
  [:div [wine-chat-fab app-state] [chat-dialog app-state]])
