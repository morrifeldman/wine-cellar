(ns wine-cellar.views.chat.core
  (:require [reagent.core :as r]
            [reagent-mui.material.fab :refer [fab]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.icons.chat :refer [chat]]
            [reagent-mui.icons.close :refer [close]]
            [reagent-mui.icons.clear-all :refer [clear-all]]
            [wine-cellar.views.components.image-upload :refer [camera-capture]]
            [wine-cellar.views.components.ai-provider-toggle :as ai-toggle]
            [wine-cellar.views.wines.filters :as wine-filters]
            [wine-cellar.utils.filters :refer [filtered-sorted-wines]]
            [wine-cellar.api :as api]
            [wine-cellar.state :as state-core]
            [wine-cellar.views.chat.utils :as chat-utils]
            [wine-cellar.views.chat.context :as chat-context]
            [wine-cellar.views.chat.actions :as chat-actions]
            [wine-cellar.views.chat.message :as chat-message]
            [wine-cellar.views.chat.input :as chat-input]
            [wine-cellar.views.chat.sidebar :as chat-sidebar]))

(defn- mobile? [] (chat-utils/mobile?))

(defn- chat-dialog-header
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
        {:on-click #(chat-actions/clear-chat! app-state
                                              messages
                                              message-ref
                                              pending-image)
         :title "Clear chat history"
         :sx {:color "secondary.main"}} [clear-all]]
       [icon-button
        {:on-click #(chat-actions/close-chat! app-state message-ref)
         :title "Close chat"
         :sx {:color "secondary.main"}} [close]]]]]))

(defn- chat-main-column
  [{:keys [sidebar-open? show-camera? handle-camera-capture handle-camera-cancel
           messages message-edit-handler handle-send is-sending? app-state
           handle-image-capture pending-image handle-image-remove message-ref
           is-editing? handle-cancel filter-panel auto-scroll?
           messages-scroll-ref on-cancel-request saved-scroll-pos]}]
  (let
    [components
     (->
       []
       (cond-> filter-panel (conj filter-panel))
       (cond-> @show-camera? (conj [camera-capture handle-camera-capture
                                    handle-camera-cancel]))
       (conj [chat-message/list-view messages message-edit-handler auto-scroll?
              messages-scroll-ref app-state saved-scroll-pos])
       (cond->
         (is-editing?)
         (conj
          [box {:component "span" :sx {:display "block"}}
           [reagent-mui.material.typography/typography
            {:variant "caption" :sx {:color "warning.main" :px 2 :py 0.5}}
            "Editing message - all responses after this will be regenerated"]]))
       (conj [chat-input/chat-input message-ref handle-send is-sending?
              "chat-input" app-state handle-image-capture pending-image
              handle-image-remove on-cancel-request])
       (cond-> (is-editing?) (conj [button
                                    {:variant "text"
                                     :size "small"
                                     :sx {:mt 1}
                                     :on-click #(handle-cancel message-ref)}
                                    "Cancel Edit"])))]
    (into [grid {:item true :xs 12 :md (if sidebar-open? 8 12)}] components)))

(defn- chat-dialog-content
  [{:keys [dialog-content-ref sidebar main-column]}]
  [dialog-content
   {:ref #(reset! dialog-content-ref %) :sx {:pt 1.5 :pb 1.5 :px 2}}
   (into [grid {:container true :spacing 2}]
         (cond-> []
           sidebar (conj sidebar)
           true (conj main-column)))])

(defn- chat-dialog-shell
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
        saved-scroll-pos (r/atom nil)
        cancel-fn-atom (r/atom nil)
        timeout-id (r/atom nil)
        handle-search
        (fn [val]
          (swap! app-state assoc-in [:chat :sidebar-search-text] val)
          (when @timeout-id (js/clearTimeout @timeout-id))
          (reset! timeout-id (js/setTimeout #(api/load-conversations!
                                              app-state
                                              {:search-text val})
                                            300)))
        edit-state (chat-input/use-edit-state app-state messages)
        {:keys [editing-message-id handle-edit handle-cancel handle-commit
                is-editing?]}
        edit-state
        handle-send (fn [message-text]
                      (reset! auto-scroll? true)
                      (if (is-editing?)
                        (chat-actions/handle-edit-send app-state
                                                       editing-message-id
                                                       message-ref
                                                       messages
                                                       is-sending?
                                                       cancel-fn-atom
                                                       auto-scroll?
                                                       handle-commit)
                        (do (chat-actions/handle-send-message app-state
                                                              message-text
                                                              messages
                                                              is-sending?
                                                              cancel-fn-atom
                                                              auto-scroll?
                                                              @pending-image)
                            (reset! pending-image nil))))
        handle-cancel-request (fn []
                                (when-let [cancel @cancel-fn-atom] (cancel))
                                (reset! cancel-fn-atom nil)
                                (reset! is-sending? false))
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
            search-text (:sidebar-search-text chat-state "")
            is-open (:open? chat-state false)
            sidebar-open? (:sidebar-open? chat-state)
            conversation-loading? (:conversation-loading? chat-state)
            messages-loading? (:messages-loading? chat-state)
            conversations (:conversations chat-state)
            active-id (:active-conversation-id chat-state)
            deleting-id (:deleting-conversation-id chat-state)
            pinning-id (:pinning-conversation-id chat-state)
            renaming-id (:renaming-conversation-id chat-state)
            context-wine-list (chat-context/context-wines app-state)
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
            manual-count (count (chat-context/manual-context-wines state))
            indicator-props (chat-context/context-indicator-props context-mode
                                                                  context-count
                                                                  manual-count)
            change-context-mode! (fn [mode]
                                   (state-core/set-context-mode! app-state mode)
                                   (when-let [_conversation-id
                                              (:active-conversation-id
                                               (:chat @app-state))]
                                     (chat-context/sync-conversation-context!
                                      app-state
                                      (chat-context/context-wines app-state))))
            context-indicator [chat-context/indicator-button context-mode
                               indicator-props change-context-mode!]
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
              (let [already-active? (= id active-id)
                    current-search (:sidebar-search-text (:chat @app-state))]
                (reset! auto-scroll? false)
                (reset! saved-scroll-pos nil)
                (when (seq current-search)
                  (swap! app-state assoc-in
                    [:chat :local-search-term]
                    current-search))
                (chat-actions/open-conversation! app-state
                                                 messages
                                                 conversation
                                                 false)
                (when (and already-active? sidebar-open?) (toggle-sidebar!))))
            sidebar
            (chat-sidebar/conversation-sidebar
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
              :on-delete
              #(chat-actions/delete-conversation-with-confirm! app-state %)
              :on-rename
              #(chat-actions/rename-conversation-with-prompt! app-state %)
              :on-pin #(chat-actions/toggle-pin! app-state %)
              :on-select select-conversation!
              :search-text search-text
              :handle-search handle-search})
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
                          :messages-scroll-ref messages-scroll-ref
                          :on-cancel-request handle-cancel-request
                          :saved-scroll-pos saved-scroll-pos})
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
        ;; Automatically update search matches when messages or term change
        (let [term (get-in state [:chat :local-search-term])
              current-matches (get-in state [:chat :search-matches])]
          (when (and (seq term) @messages)
            (let [new-matches (chat-utils/calculate-matches @messages term)]
              (when (not= new-matches current-matches)
                (swap! app-state assoc-in [:chat :search-matches] new-matches)
                (when (empty? current-matches)
                  (swap! app-state assoc-in [:chat :current-match-index] 0))))))
        (when (and is-open
                   active-id
                   (not messages-loading?)
                   (empty? conversation-messages))
          (api/fetch-conversation-messages! app-state active-id))
        (when (and is-open (not @dialog-opened) @dialog-content-ref)
          (reset! dialog-opened true)
          (reset! auto-scroll? (nil? @saved-scroll-pos))
          (js/setTimeout #(do (when @dialog-content-ref
                                (.scrollTo @dialog-content-ref
                                           #js {:top (.-scrollHeight
                                                      @dialog-content-ref)
                                                :behavior "smooth"}))
                              (when-let [el @messages-scroll-ref]
                                (if-let [top @saved-scroll-pos]
                                  (.scrollTo el #js {:top top :behavior "auto"})
                                  (.scrollTo el
                                             #js {:top (.-scrollHeight el)
                                                  :behavior "smooth"}))))
                         200))
        (when (not is-open)
          (reset! dialog-opened false)
          (reset! auto-scroll? true))
        (chat-dialog-shell {:app-state app-state
                            :is-open is-open
                            :header-props header-props
                            :content-props content-props})))))

(defn- smart-open-chat!
  [app-state]
  ;; Always default to :selection+filters mode when opening chat
  (state-core/set-context-mode! app-state :selection+filters)
  (swap! app-state assoc-in [:chat :open?] true)
  (api/load-conversations! app-state {:force? true}))

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
                                     :top "42%"
                                     :transform "translateY(-50%)"
                                     :right 16
                                     :left "auto"}}
    :on-click #(smart-open-chat! app-state)} [chat]])

(defn wine-chat
  "Main wine chat component with FAB and dialog"
  [app-state]
  [:div [wine-chat-fab app-state] [chat-dialog app-state]])
