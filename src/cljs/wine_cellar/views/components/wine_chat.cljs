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
            [wine-cellar.views.components.ai-provider-toggle :as ai-toggle]
            [wine-cellar.views.wines.filters :as wine-filters]
            [wine-cellar.api :as api]
            [wine-cellar.state :as state-core]
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
            (let [data-url (-> e
                               .-target
                               .-result)
                  img (js/Image.)]
              (set! (.-onload img)
                    (fn []
                      ;; Convert to JPEG format
                      (let [canvas (js/document.createElement "canvas")
                            ctx (.getContext canvas "2d")]
                        (set! (.-width canvas) (.-width img))
                        (set! (.-height canvas) (.-height img))
                        (.drawImage ctx img 0 0)
                        (let [jpeg-data-url
                              (.toDataURL canvas "image/jpeg" 0.85)]
                          (reset! attached-image jpeg-data-url)))))
              (set! (.-src img) data-url))))
    (.readAsDataURL reader file-or-blob)))

(defn- handle-paste-event
  "Handle paste events to detect and process clipboard images"
  [event attached-image]
  (when-let [items (.-items (.-clipboardData event))]
    (dotimes [i (.-length items)]
      (let [item (aget items i)]
        (when (and (.-type item) (.startsWith (.-type item) "image/"))
          (when-let [file (.getAsFile item)]
            (handle-clipboard-image file attached-image)))))))

(declare persist-conversation-message!
         apply-wine-search-state!
         build-wine-search-state
         sync-conversation-context!
         context-wines)

(defn- open-conversation!
  ([app-state messages conversation]
   (open-conversation! app-state messages conversation false))
  ([app-state messages {:keys [id] :as conversation} close-sidebar?]
   (when id
     (when close-sidebar?
       (swap! app-state assoc-in [:chat :sidebar-open?] false))
     (swap! app-state assoc-in [:chat :active-conversation-id] id)
     (swap! app-state assoc-in [:chat :active-conversation] conversation)
     (when-let [provider (:provider conversation)]
       (swap! app-state assoc-in [:ai :provider] (keyword provider)))
     (reset! messages [])
     (swap! app-state assoc-in [:chat :messages] [])
     (when-let [search-state (:wine_search_state conversation)]
       (apply-wine-search-state! app-state search-state))
     (api/fetch-conversation-messages! app-state id))))

(defn- ensure-conversation!
  "Ensure an active conversation exists before persisting messages."
  [app-state wines callback]
  (let [conversation-id (get-in @app-state [:chat :active-conversation-id])
        creating? (get-in @app-state [:chat :creating-conversation?])]
    (cond conversation-id (do (swap! app-state assoc-in
                                [:chat :active-conversation-id]
                                conversation-id)
                              (callback conversation-id))
          creating?
          (js/setTimeout #(ensure-conversation! app-state wines callback) 100)
          :else
          (let [state @app-state
                context-mode (state-core/context-mode state)
                wine-ids (->> wines
                              (map :id)
                              (remove nil?)
                              vec)
                payload (cond-> {:wine_search_state (build-wine-search-state
                                                     app-state
                                                     context-mode)
                                 :provider (get-in @app-state [:ai :provider])}
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
   (ensure-conversation! app-state
                         wines
                         (fn [conversation-id]
                           (api/append-conversation-message!
                            app-state
                            conversation-id
                            message-map
                            (fn [{:keys [success error]}]
                              (if success
                                (when after-save (after-save conversation-id))
                                (tap> ["conversation-message-persist-failed"
                                       error]))))))))

(defn- combine-wine-lists
  "Merge two wine collections, keeping the order of the first and
   removing duplicates by id."
  [primary secondary]
  (let [initial-seen (into #{} (keep :id primary))]
    (first (reduce (fn [[wines seen] wine]
                     (let [wine-id (:id wine)]
                       (if (and wine-id (contains? seen wine-id))
                         [wines seen]
                         [(conj wines wine)
                          (if wine-id (conj seen wine-id) seen)])))
                   [(vec primary) initial-seen]
                   secondary))))

(defn- manual-context-wines
  [state]
  (let [base-selected-ids (or (:selected-wine-ids state) #{})
        selected-ids (cond-> base-selected-ids
                       (:selected-wine-id state) (conj (:selected-wine-id
                                                        state)))]
    (if (seq selected-ids)
      (let [wines (or (:wines state) [])]
        (vec (filter #(contains? selected-ids (:id %)) wines)))
      [])))

(defn- context-wines
  "Derive the set of wines currently in chat context."
  [app-state]
  (let [state @app-state
        mode (state-core/context-mode state)
        manual-wines (manual-context-wines state)
        visible-wines (if (= mode :selection+filters)
                        (vec (or (filtered-sorted-wines app-state) []))
                        [])]
    (case mode
      :summary []
      :selection manual-wines
      :selection+filters (combine-wine-lists manual-wines visible-wines)
      [])))

(defn- context-label-element
  [mode context-count manual-count]
  (case mode
    :summary "Summary only"
    :selection (if (pos? manual-count)
                 [:<> [:span {:style {:fontWeight 700}} (str manual-count)]
                  (if (= manual-count 1) " wine selected" " wines selected")]
                 "No wines selected")
    :selection+filters (if (pos? context-count)
                         [:<>
                          [:span {:style {:fontWeight 700}} (str context-count)]
                          " wines (selected + filters)"]
                         "No wines match the filters")
    "Summary only"))

(defn- context-indicator-props
  [mode context-count manual-count]
  (let [label (context-label-element mode context-count manual-count)]
    (case mode
      :summary {:color "text.secondary" :label label}
      :selection (if (pos? manual-count)
                   {:color "success.main" :label label}
                   {:color "text.secondary" :label label})
      :selection+filters
      (cond (zero? context-count) {:color "text.secondary" :label label}
            (> context-count 50) {:color "common.white"
                                  :label label
                                  :sx {:backgroundColor "error.main"
                                       :padding "2px 6px"
                                       :borderRadius "999px"}}
            (<= context-count 15) {:color "success.main" :label label}
            (<= context-count 50) {:color "warning.main" :label label}
            :else {:color "text.secondary" :label label})
      {:color "text.secondary" :label label})))

(defn- build-wine-search-state
  [app-state context-mode]
  (let [state @app-state
        include? (contains? #{:selection :selection+filters} context-mode)]
    {:filters (:filters state)
     :sort (:sort state)
     :show-out-of-stock? (:show-out-of-stock? state)
     :selected-wine-id (:selected-wine-id state)
     :selected-wine-ids (-> (:selected-wine-ids state)
                            (or #{})
                            (cond-> (:selected-wine-id state)
                                    (conj (:selected-wine-id state)))
                            vec)
     :show-selected-wines? (:show-selected-wines? state)
     :context-mode context-mode
     :include-visible-wines? include?}))

(defn- apply-wine-search-state!
  [app-state search-state]
  (when (map? search-state)
    (swap! app-state
      (fn [state]
        (-> state
            (cond-> (contains? search-state :filters)
                    (assoc
                     :filters
                     (update (:filters search-state) :tasting-window keyword)))
            (cond-> (contains? search-state :sort) (assoc :sort
                                                          (:sort search-state)))
            (cond-> (contains? search-state :show-out-of-stock?)
                    (assoc :show-out-of-stock?
                           (:show-out-of-stock? search-state)))
            (cond-> (contains? search-state :selected-wine-id)
                    (assoc :selected-wine-id (:selected-wine-id search-state)))
            (cond-> (contains? search-state :selected-wine-ids)
                    (assoc :selected-wine-ids
                           (into #{} (:selected-wine-ids search-state))))
            (cond-> (contains? search-state :show-selected-wines?)
                    (assoc :show-selected-wines?
                           (boolean (:show-selected-wines? search-state)))))))
    (cond (contains? search-state :context-mode)
          (when-let [mode (:context-mode search-state)]
            (state-core/set-context-mode! app-state (keyword mode)))
          (contains? search-state :include-visible-wines?)
          (let [mode (if (:include-visible-wines? search-state)
                       :selection+filters
                       (if (or (seq (:selected-wine-ids search-state))
                               (:selected-wine-id search-state))
                         :selection
                         :summary))]
            (state-core/set-context-mode! app-state mode)))))

(defn- sync-conversation-context!
  ([app-state wines]
   (when-let [conversation-id (get-in @app-state
                                      [:chat :active-conversation-id])]
     (sync-conversation-context! app-state wines conversation-id)))
  ([app-state wines conversation-id]
   (let [state @app-state
         context-mode (state-core/context-mode state)
         wine-ids (->> wines
                       (map :id)
                       (remove nil?)
                       vec)
         search-state (build-wine-search-state app-state context-mode)]
     (api/update-conversation-context! app-state
                                       conversation-id
                                       {:wine-ids wine-ids
                                        :wine-search-state search-state}))))

(defn- mobile?
  []
  (boolean (and (exists? js/navigator)
                (pos? (or (.-maxTouchPoints js/navigator) 0)))))

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
       :on-click (fn [event] (.stopPropagation event) (on-pin conversation))
       :disabled pinning?
       :sx {:color (if (true? pinned) "warning.main" "text.secondary")}}
      (cond pinning? [circular-progress {:size 16}]
            (true? pinned) [star {:fontSize "small"}]
            :else [star-border {:fontSize "small"}])])
   (when on-rename
     [icon-button
      {:size "small"
       :on-click (fn [event] (.stopPropagation event) (on-rename conversation))
       :disabled renaming?
       :sx {:color "text.secondary"}}
      (if renaming? [circular-progress {:size 16}] [edit])])
   (when on-delete
     [icon-button
      {:size "small"
       :on-click (fn [event] (.stopPropagation event) (on-delete conversation))
       :disabled deleting?
       :sx {:color "text.secondary"}}
      (if deleting? [circular-progress {:size 16}] [delete])])])

(defn- conversation-row
  [app-state messages
   {:keys [active-id deleting-id renaming-id pinning-id on-delete on-rename
           on-pin on-select]} {:keys [id] :as conversation}]
  (let [active? (= id active-id)
        deleting? (= id deleting-id)
        renaming? (= id renaming-id)
        pinning? (= id pinning-id)]
    [box
     {:on-click #(if on-select
                   (on-select conversation)
                   (open-conversation! app-state messages conversation true))
      :sx
      (let
        [base
         {:px 2
          :py 1.5
          :cursor "pointer"
          :background-color "transparent"
          :border-bottom "1px solid"
          :border-bottom-color "divider"
          :border-left "4px solid transparent"
          :border-radius 1
          :color "text.primary"
          :transition
          "background-color 140ms ease, box-shadow 140ms ease, border-left-color 140ms ease"
          :&:hover {:background-color "rgba(255,255,255,0.05)"}}
         active-style {:background-color "rgba(255,255,255,0.14)"
                       :border-left-color "primary.light"
                       :box-shadow "0 0 0 1px rgba(144,202,249,0.4) inset"
                       :color "common.white"
                       :&:hover {:background-color "rgba(255,255,255,0.18)"}}]
        (cond-> base active? (merge active-style)))}
     [box
      {:sx {:display "flex"
            :align-items "center"
            :justify-content "space-between"
            :gap 1}}
      [typography
       {:variant "body2"
        :sx {:fontWeight (if active? "600" "500")
             :color (if active? "inherit" "text.primary")}}
       (conversation-label conversation)]
      (conversation-action-buttons conversation
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
    :empty [typography
            {:variant "body2" :sx {:px 2 :py 2 :color "text.secondary"}}
            "No conversations yet"]
    (into [:<>] items)))

(defn- conversation-sidebar
  [app-state messages
   {:keys [open? conversations loading? active-id deleting-id renaming-id
           pinning-id scroll-ref scroll-requested? on-delete on-rename on-pin
           on-select]}]
  (if open?
    (let [items (conversations->items app-state
                                      messages
                                      {:active-id active-id
                                       :deleting-id deleting-id
                                       :renaming-id renaming-id
                                       :pinning-id pinning-id
                                       :on-delete on-delete
                                       :on-rename on-rename
                                       :on-pin on-pin
                                       :on-select on-select}
                                      conversations)
          status (cond loading? :loading
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
        [typography
         {:variant "subtitle2"
          :sx {:px 2 :py 1 :border-bottom "1px solid" :border-color "divider"}}
         "Conversations"]
        [box
         {:sx {:max-height "320px" :overflow "auto"}
          :ref (fn [el]
                 (when scroll-ref (reset! scroll-ref el))
                 (when (and scroll-requested? @scroll-requested? el)
                   (.scrollTo el 0 0)
                   (reset! scroll-requested? false)))} content]]])
    (do (when scroll-requested? (reset! scroll-requested? false))
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
      (js/setTimeout #(when @container-ref
                        (.scrollIntoView @container-ref
                                         #js {:behavior "smooth"
                                              :block "nearest"}))
                     100))
    [box
     {:key reset-key
      :sx {:display "flex" :flex-direction "column" :gap 1 :mt 2}
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
                      (let [is-mobile? (and js/navigator.maxTouchPoints
                                            (> js/navigator.maxTouchPoints 0))]
                        (if is-mobile?
                          "Type your message here..."
                          "Type your message here... (or paste a screenshot)")))
       :disabled @disabled?
       :on-change
       #(swap! app-state assoc-in [:chat :draft-message] (.. % -target -value))
       :on-paste #(handle-paste-event % attached-image)}]
     [box
      {:sx {:display "flex"
            :justify-content "flex-end"
            :align-items "center"
            :gap 1
            :flex-wrap "wrap"}}
      (let [is-mobile? (and js/navigator.maxTouchPoints
                            (> js/navigator.maxTouchPoints 0))
            trigger-upload #(when-let [input (js/document.getElementById
                                              "photo-picker-input")]
                              (.click input))]
        [:<>
         [:input
          {:type "file"
           :accept "image/*"
           :style {:display "none"}
           :id "photo-picker-input"
           :on-change #(when-let [file (-> %
                                           .-target
                                           .-files
                                           (aget 0))]
                         (handle-clipboard-image file attached-image))}]
         (when is-mobile?
           [button
            {:variant "outlined"
             :size "small"
             :disabled @disabled?
             :startIcon (r/as-element [camera-alt {:size 14}])
             :on-click #(on-image-capture nil)
             :sx {:minWidth "90px"}} "Camera"])
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
                                       {:size 14
                                        :sx {:color "secondary.light"}}])
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
  [messages on-edit auto-scroll? scroll-container-ref]
  (let [scroll-ref (r/atom nil)
        last-ai-message-ref (r/atom nil)
        messages-atom messages
        edit-handler on-edit
        should-scroll? auto-scroll?]
    (r/create-class
     {:component-did-update
      (fn [_]
        (when (and should-scroll? @should-scroll? @last-ai-message-ref)
          (.scrollIntoView @last-ai-message-ref
                           #js {:behavior "smooth" :block "start"})
          (reset! should-scroll? false)))
      :component-will-unmount
      (fn [] (when scroll-container-ref (reset! scroll-container-ref nil)))
      :reagent-render
      (fn []
        (when should-scroll? @should-scroll?)
        [box
         {:ref #(do (reset! scroll-ref %)
                    (when scroll-container-ref (reset! scroll-container-ref %)))
          :sx {:height "360px"
               :overflow-y "auto"
               :p 2
               :background-color "background.default"
               :border "1px solid"
               :border-color "divider"
               :border-radius 1}}
         (let [current-messages @messages-atom]
           (if (empty? current-messages)
             [typography
              {:variant "body2"
               :sx {:text-align "center" :color "text.secondary" :mt 2}}
              "Start a conversation about your wine cellar..."]
             (doall (for [message current-messages]
                      (let [is-last-message? (= message
                                                (last current-messages))]
                        ^{:key (:id message)}
                        [message-bubble message edit-handler
                         (when is-last-message?
                           #(reset! last-ai-message-ref %))])))))])})))

(defn send-chat-message
  "Send a message to the AI chat endpoint with conversation history and optional image.
   Provider is read from app-state by api.cljs."
  ([app-state message wines include? conversation-history callback]
   (send-chat-message app-state
                      message
                      wines
                      include?
                      conversation-history
                      nil
                      callback))
  ([app-state message wines include? conversation-history image callback]
   (tap> ["send-chat-message" message conversation-history
          {:include? include?}])
   (api/send-chat-message app-state
                          message
                          wines
                          include?
                          conversation-history
                          image
                          callback)))

(defn- handle-send-message
  "Handle sending a message to the AI assistant with optional image"
  [app-state message-text messages is-sending? auto-scroll? & [image]]
  (when (and (not @is-sending?) (or (seq message-text) image))
    (reset! is-sending? true)
    (let [state @app-state
          user-message {:id (random-uuid)
                        :text (or message-text "")
                        :is-user true
                        :timestamp (.getTime (js/Date.))}
          include? (state-core/include-wines? state)
          wines (context-wines app-state)]
      (swap! messages conj user-message)
      (swap! app-state assoc-in [:chat :messages] @messages)
      (when auto-scroll? (reset! auto-scroll? true))
      (persist-conversation-message!
       app-state
       wines
       (cond-> {:is_user true :content (or message-text "")}
         image (assoc :image_data image))
       (fn [conversation-id]
         (sync-conversation-context! app-state wines conversation-id)))
      (send-chat-message
       app-state
       message-text
       wines
       include?
       @messages
       image
       (fn [response]
         (let [ai-message {:id (random-uuid)
                           :text response
                           :is-user false
                           :timestamp (.getTime (js/Date.))}]
           (swap! messages conj ai-message)
           (swap! app-state assoc-in [:chat :messages] @messages)
           (when auto-scroll? (reset! auto-scroll? true))
           (persist-conversation-message!
            app-state
            wines
            {:is_user false :content response}
            (fn [conversation-id]
              (when conversation-id
                (api/update-conversation-provider! app-state
                                                   conversation-id
                                                   (get-in @app-state
                                                           [:ai :provider])))
              (api/load-conversations! app-state {:force? true})))
           (reset! is-sending? false)))))))

(defn- use-edit-state
  [app-state messages]
  (let [editing-message-id (r/atom nil)
        original-messages (r/atom nil)]
    {:editing-message-id editing-message-id
     :handle-edit
     (fn [message-id message-text message-ref]
       (reset! editing-message-id message-id)
       (let [text (or message-text "")
             current @messages
             message-idx (->> current
                              (keep-indexed (fn [idx message]
                                              (when (= (:id message) message-id)
                                                idx)))
                              first)
             truncated (if (some? message-idx)
                         (conj (vec (take message-idx current))
                               (assoc (nth current message-idx) :text text))
                         current)]
         (reset! original-messages current)
         (swap! app-state assoc-in [:chat :draft-message] text)
         (when (and (some? message-idx) (< (inc message-idx) (count current)))
           (reset! messages truncated)
           (swap! app-state assoc-in [:chat :messages] truncated))
         (when @message-ref
           (set! (.-value @message-ref) text)
           (let [input-event (js/Event. "input" #js {:bubbles true})]
             (.dispatchEvent @message-ref input-event))
           (.focus @message-ref))))
     :handle-cancel (fn [message-ref]
                      (when-let [original @original-messages]
                        (reset! messages original)
                        (swap! app-state assoc-in [:chat :messages] original))
                      (reset! original-messages nil)
                      (reset! editing-message-id nil)
                      (swap! app-state update :chat dissoc :draft-message)
                      (when @message-ref (set! (.-value @message-ref) "")))
     :handle-commit #(reset! original-messages nil)
     :is-editing? #(some? @editing-message-id)}))

(defn- api-message->ui
  [message]
  (when message
    {:id (:id message)
     :text (:content message)
     :is-user (:is_user message)
     :timestamp (some-> (:created_at message)
                        js/Date.parse
                        js/Date.)}))

(defn- find-message-index
  [messages message-id]
  (->> messages
       (keep-indexed (fn [idx message] (when (= (:id message) message-id) idx)))
       first))

(defn- commit-local-edit!
  [app-state messages editing-message-id message-ref auto-scroll?
   on-edit-complete is-sending? new-history]
  (reset! messages new-history)
  (swap! app-state assoc-in [:chat :messages] new-history)
  (reset! editing-message-id nil)
  (when @message-ref (set! (.-value @message-ref) ""))
  (swap! app-state update :chat dissoc :draft-message)
  (when auto-scroll? (reset! auto-scroll? true))
  (when on-edit-complete (on-edit-complete))
  (reset! is-sending? true))

(defn- remove-deleted-messages!
  [app-state messages deleted-ids]
  (when-let [ids (seq deleted-ids)]
    (let [delete-set (set ids)
          pruned (swap! messages #(vec (remove (fn [msg]
                                                 (contains? delete-set
                                                            (:id msg)))
                                               %)))]
      (swap! app-state assoc-in [:chat :messages] pruned))))

(defn- sync-context-if-needed!
  [app-state wines conversation-id]
  (when (and (integer? conversation-id) wines)
    (sync-conversation-context! app-state wines conversation-id)))

(defn- enqueue-ai-followup!
  [app-state messages message-text wines include? auto-scroll? is-sending?]
  (let [history @messages]
    (send-chat-message
     app-state
     message-text
     wines
     include?
     history
     (fn [response]
       (let [ai-message {:id (random-uuid)
                         :text response
                         :is-user false
                         :timestamp (.getTime (js/Date.))}]
         (swap! messages conj ai-message)
         (swap! app-state assoc-in [:chat :messages] @messages)
         (when auto-scroll? (reset! auto-scroll? true))
         (persist-conversation-message!
          app-state
          wines
          {:is_user false :content response}
          (fn [conversation-id]
            (when conversation-id
              (api/update-conversation-provider! app-state
                                                 conversation-id
                                                 (get-in @app-state
                                                         [:ai :provider])))
            (api/load-conversations! app-state {:force? true})))
         (reset! is-sending? false))))))

(defn- apply-server-edit!
  [app-state messages message-idx
   {:keys [message deleted-message-ids] :as data}]
  (when-let [sanitized (api-message->ui message)]
    (let [updated (swap! messages #(assoc (vec %) message-idx sanitized))]
      (swap! app-state assoc-in [:chat :messages] updated)))
  (remove-deleted-messages! app-state messages deleted-message-ids)
  data)

(defn- handle-edit-send
  [app-state editing-message-id message-ref messages is-sending? auto-scroll?
   on-edit-complete]
  (when @message-ref
    (let [message-text (.-value @message-ref)]
      (if-let [message-idx (find-message-index @messages @editing-message-id)]
        (let [current @messages
              original-message (nth current message-idx)
              updated-local (assoc original-message :text message-text)
              new-history (conj (vec (take message-idx current)) updated-local)
              state @app-state
              include? (state-core/include-wines? state)
              wines (context-wines app-state)
              conversation-id (get-in @app-state
                                      [:chat :active-conversation-id])
              message-db-id (:id original-message)]
          (commit-local-edit! app-state
                              messages
                              editing-message-id
                              message-ref
                              auto-scroll?
                              on-edit-complete
                              is-sending?
                              new-history)
          (let [follow-up #(enqueue-ai-followup! app-state
                                                 messages
                                                 message-text
                                                 wines
                                                 include?
                                                 auto-scroll?
                                                 is-sending?)
                handle-update-success
                (fn [data]
                  (apply-server-edit! app-state messages message-idx data)
                  (sync-context-if-needed! app-state wines conversation-id)
                  (follow-up))
                handle-update-error
                (fn [error-msg]
                  (reset! is-sending? false)
                  (swap! app-state assoc-in [:chat :error] error-msg)
                  (when (integer? conversation-id)
                    (api/fetch-conversation-messages! app-state
                                                      conversation-id)))]
            (if (and (integer? conversation-id) (integer? message-db-id))
              (api/update-conversation-message!
               app-state
               conversation-id
               message-db-id
               {:content message-text :truncate_after? true}
               (fn [{:keys [success data error]}]
                 (if success
                   (handle-update-success data)
                   (handle-update-error (or error
                                            "Failed to update message")))))
              (do (tap> ["conversation-message-update-skipped"
                         {:conversation-id conversation-id
                          :message-id message-db-id}])
                  (follow-up)))))
        (do (reset! editing-message-id nil)
            (set! (.-value @message-ref) "")
            (swap! app-state update :chat dissoc :draft-message)
            (when on-edit-complete (on-edit-complete)))))))

(defn clear-chat!
  ([app-state messages] (clear-chat! app-state messages nil nil))
  ([app-state messages message-ref pending-image]
   (reset! messages [])
   (when (and message-ref @message-ref) (set! (.-value @message-ref) ""))
   (when pending-image (reset! pending-image nil))
   (swap! app-state (fn [state]
                      (-> state
                          (assoc-in [:chat :messages] [])
                          (assoc-in [:chat :active-conversation-id] nil)
                          (assoc-in [:chat :active-conversation] nil)
                          (assoc-in [:chat :messages-loading?] false)
                          (assoc-in [:chat :creating-conversation?] false)
                          (assoc-in [:chat :draft-message] nil))))))

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
        (when (and (not (string/blank? trimmed)) (not= trimmed title))
          (api/rename-conversation! app-state id trimmed))))))

(defn toggle-pin!
  [app-state {:keys [id pinned]}]
  (when id (api/set-conversation-pinned! app-state id (not (true? pinned)))))

(defn chat-dialog-header
  [{:keys [app-state messages message-ref pending-image conversation-loading?
           sidebar-open? on-toggle-sidebar context-indicator]}]
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
            :sx
            {:textTransform "none" :display "flex" :alignItems "center" :gap 1}}
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
      [box {:sx {:display "flex" :align-items "center" :gap 0.75 :minWidth 0}}
       [box
        {:sx {:display "flex"
              :flex-direction "column"
              :align-items "flex-start"
              :gap 0.25}}
        [ai-toggle/provider-toggle-button app-state
         {:mobile-min-width (if is-mobile? "96px" "120px")
          :sx {:alignSelf "flex-start"}}]
        (when context-indicator context-indicator)]]
      [box
       {:sx {:display "flex" :align-items "center" :gap (if is-mobile? 0.5 1)}}
       conversation-toggle
       [icon-button
        {:on-click #(clear-chat! app-state messages message-ref pending-image)
         :title "Clear chat history"
         :sx {:color "secondary.main"}} [clear-all]]
       [icon-button
        {:on-click #(close-chat! app-state message-ref)
         :title "Close chat"
         :sx {:color "secondary.main"}} [close]]]]]))

(defn chat-main-column
  [{:keys [sidebar-open? show-camera? handle-camera-capture handle-camera-cancel
           messages message-edit-handler handle-send is-sending? app-state
           handle-image-capture pending-image handle-image-remove message-ref
           is-editing? handle-cancel filter-panel auto-scroll?
           messages-scroll-ref]}]
  (let
    [components
     (-> []
         (cond-> filter-panel (conj filter-panel))
         (cond-> @show-camera? (conj [camera-capture handle-camera-capture
                                      handle-camera-cancel]))
         (conj [chat-messages messages message-edit-handler auto-scroll?
                messages-scroll-ref])
         (cond->
           (is-editing?)
           (conj
            [typography
             {:variant "caption" :sx {:color "warning.main" :px 2 :py 0.5}}
             "Editing message - all responses after this will be regenerated"]))
         (conj [chat-input message-ref handle-send is-sending? "chat-input"
                app-state handle-image-capture pending-image
                handle-image-remove])
         (cond-> (is-editing?) (conj [button
                                      {:variant "text"
                                       :size "small"
                                       :sx {:mt 1}
                                       :on-click #(handle-cancel message-ref)}
                                      "Cancel Edit"])))]
    (into [grid {:item true :xs 12 :md (if sidebar-open? 8 12)}] components)))

(defn chat-dialog-content
  [{:keys [dialog-content-ref sidebar main-column]}]
  [dialog-content
   {:ref #(reset! dialog-content-ref %) :sx {:pt 1.5 :pb 1.5 :px 2}}
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
    :full-width true
    :PaperProps {:sx {:py 2 :px 2}}} (chat-dialog-header header-props)
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
        auto-scroll? (r/atom true)
        messages-scroll-ref (r/atom nil)
        edit-state (use-edit-state app-state messages)
        {:keys [editing-message-id handle-edit handle-cancel handle-commit
                is-editing?]}
        edit-state
        handle-send (fn [message-text]
                      (reset! auto-scroll? true)
                      (if (is-editing?)
                        (handle-edit-send app-state
                                          editing-message-id
                                          message-ref
                                          messages
                                          is-sending?
                                          auto-scroll?
                                          handle-commit)
                        (do (handle-send-message app-state
                                                 message-text
                                                 messages
                                                 is-sending?
                                                 auto-scroll?
                                                 @pending-image)
                            (reset! pending-image nil))))
        handle-image-capture (fn [] (reset! show-camera? true))
        handle-camera-capture (fn [image-data]
                                (reset! show-camera? false)
                                (reset! pending-image (:label_image
                                                       image-data)))
        handle-camera-cancel
        (fn [] (reset! show-camera? false) (reset! pending-image nil))
        handle-image-remove (fn [] (reset! pending-image nil))
        message-edit-handler (fn [id text] (handle-edit id text message-ref))]
    (fn [app-state]
      (let [state @app-state
            chat-state (:chat state)
            is-open (:open? chat-state false)
            sidebar-open? (:sidebar-open? chat-state)
            conversation-loading? (:conversation-loading? chat-state)
            messages-loading? (:messages-loading? chat-state)
            conversations (:conversations chat-state)
            active-id (:active-conversation-id chat-state)
            deleting-id (:deleting-conversation-id chat-state)
            pinning-id (:pinning-conversation-id chat-state)
            renaming-id (:renaming-conversation-id chat-state)
            context-wine-list (context-wines app-state)
            conversation-messages (vec (or (:messages chat-state) []))
            wines (or (:wines state) [])
            show-out-of-stock? (:show-out-of-stock? state)
            base-wines (if show-out-of-stock?
                         wines
                         (filter #(pos? (or (:quantity %) 0)) wines))
            total-count (count base-wines)
            context-mode (state-core/context-mode state)
            filters-active? (= context-mode :selection+filters)
            visible-wines (when filters-active?
                            (or (filtered-sorted-wines app-state) []))
            visible-count (if filters-active? (count visible-wines) 0)
            context-count (count context-wine-list)
            manual-count (count (manual-context-wines state))
            indicator-props
            (context-indicator-props context-mode context-count manual-count)
            change-context-mode!
            (fn [mode]
              (state-core/set-context-mode! app-state mode)
              (when-let [_conversation-id (:active-conversation-id
                                           (:chat @app-state))]
                (sync-conversation-context! app-state
                                            (context-wines app-state))))
            context-indicator
            (let [{:keys [color label sx]} indicator-props
                  indicator-label label
                  context-cycle [:summary :selection :selection+filters]
                  cycle-context-mode!
                  (fn []
                    (let [indexed (map-indexed vector context-cycle)
                          idx (or (some (fn [[i mode]]
                                          (when (= mode context-mode) i))
                                        indexed)
                                  0)
                          next-mode (nth context-cycle
                                         (mod (inc idx) (count context-cycle)))]
                      (change-context-mode! next-mode)))
                  toggle-style {:backgroundColor "rgba(255,255,255,0.08)"
                                :borderRadius 1
                                :px 1
                                :py 0.25
                                :border "1px solid rgba(255,255,255,0.2)"
                                :color "rgba(255,255,255,0.8)"
                                :textTransform "uppercase"
                                :fontSize "0.65rem"
                                :fontWeight 600
                                :lineHeight 1.1
                                :letterSpacing "0.05em"
                                :minWidth 0
                                "&:hover" {:backgroundColor
                                           "rgba(255,255,255,0.12)"}}]
              [box {:sx {:display "flex" :flexDirection "column" :gap 0.5}}
               [button
                {:variant "outlined"
                 :size "small"
                 :onClick cycle-context-mode!
                 :sx toggle-style}
                [:span
                 {:style {:display "inline-flex"
                          :flexDirection "column"
                          :alignItems "center"
                          :gap "0.1rem"}} "Toggle"
                 [:span {:style {:display "block"}} "Context"]]]
               [typography
                {:variant "caption"
                 :sx (merge {:color color :fontSize "0.7rem" :lineHeight 1.2}
                            sx)} indicator-label]])
            filter-count-info {:visible visible-count :total total-count}
            filter-panel (when filters-active?
                           (wine-filters/compact-filter-bar app-state
                                                            filter-count-info))
            toggle-sidebar!
            (fn []
              (let [opening? (not sidebar-open?)]
                (if opening?
                  (do (reset! sidebar-scroll-requested? true)
                      (js/setTimeout
                       #(when-let [el @dialog-content-ref]
                          (.scrollTo el #js {:top 0 :behavior "smooth"}))
                       50))
                  (do (reset! sidebar-scroll-requested? false)
                      (reset! auto-scroll? true)
                      (js/setTimeout #(when-let [el @messages-scroll-ref]
                                        (.scrollTo el
                                                   #js {:top (.-scrollHeight el)
                                                        :behavior "smooth"}))
                                     60)))
                (reset! sidebar-scroll-ref nil)
                (when opening? (reset! auto-scroll? false))
                (swap! app-state update-in [:chat :sidebar-open?] not)))
            select-conversation!
            (fn [{:keys [id] :as conversation}]
              (let [already-active? (= id active-id)]
                (reset! auto-scroll? false)
                (open-conversation! app-state messages conversation false)
                (when (and already-active? sidebar-open?) (toggle-sidebar!))))
            sidebar (conversation-sidebar
                     app-state
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
                      :on-delete #(delete-conversation-with-confirm! app-state
                                                                     %)
                      :on-rename #(rename-conversation-with-prompt! app-state %)
                      :on-pin #(toggle-pin! app-state %)
                      :on-select select-conversation!})
            main-column (chat-main-column
                         {:sidebar-open? sidebar-open?
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
                          :handle-cancel handle-cancel
                          :filter-panel filter-panel
                          :auto-scroll? auto-scroll?
                          :messages-scroll-ref messages-scroll-ref})
            header-props {:app-state app-state
                          :messages messages
                          :message-ref message-ref
                          :pending-image pending-image
                          :conversation-loading? conversation-loading?
                          :sidebar-open? sidebar-open?
                          :on-toggle-sidebar toggle-sidebar!
                          :context-indicator context-indicator}
            content-props {:dialog-content-ref dialog-content-ref
                           :sidebar sidebar
                           :main-column main-column}]
        (when (not= @messages conversation-messages)
          (reset! messages conversation-messages))
        (when (and is-open
                   active-id
                   (not messages-loading?)
                   (empty? conversation-messages))
          (api/fetch-conversation-messages! app-state active-id))
        (when (and is-open (not @dialog-opened) @dialog-content-ref)
          (reset! dialog-opened true)
          (reset! auto-scroll? true)
          (js/setTimeout #(do (when @dialog-content-ref
                                (.scrollTo @dialog-content-ref
                                           #js {:top (.-scrollHeight
                                                      @dialog-content-ref)
                                                :behavior "smooth"}))
                              (when-let [el @messages-scroll-ref]
                                (.scrollTo el
                                           #js {:top (.-scrollHeight el)
                                                :behavior "smooth"})))
                         200))
        (when (not is-open)
          (reset! dialog-opened false)
          (reset! auto-scroll? true))
        (chat-dialog-shell {:app-state app-state
                            :is-open is-open
                            :header-props header-props
                            :content-props content-props})))))

(defn wine-chat-fab
  "Floating action button for wine chat"
  [app-state]
  [fab
   {:color "primary"
    :sx {:position "fixed"
         :bottom 16
         :right 16
         :z-index 1000
         "@media (max-width:600px)" {:bottom "auto"
                                     :top "50%"
                                     :transform "translateY(-50%)"
                                     :right 16
                                     :left "auto"}}
    :on-click #(do (swap! app-state assoc-in [:chat :open?] true)
                   (api/load-conversations! app-state {:force? true}))} [chat]])

(defn wine-chat
  "Main wine chat component with FAB and dialog"
  [app-state]
  [:div [wine-chat-fab app-state] [chat-dialog app-state]])
