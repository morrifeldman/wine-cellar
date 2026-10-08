(ns wine-cellar.views.chat.core
  (:require [reagent.core :as r]
            [wine-cellar.views.components.banner :refer [dismissable-banner]]
            [reagent-mui.material.fab :refer [fab]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.material.tooltip :refer [tooltip]]
            [reagent-mui.icons.chat :refer [chat]]
            [reagent-mui.icons.forum :refer [forum]]
            [reagent-mui.icons.close :refer [close]]
            [reagent-mui.icons.add :refer [add]]
            [wine-cellar.views.components.image-upload :refer [camera-capture]]
            [reagent-mui.material.typography :refer [typography]]
            [wine-cellar.views.wines.filters :as wine-filters]
            [wine-cellar.utils.filters :refer [filtered-sorted-wines]]
            [wine-cellar.api :as api]
            [wine-cellar.nav :as nav]
            [wine-cellar.state :as state-core]
            [wine-cellar.views.chat.utils :as chat-utils]
            [wine-cellar.views.chat.context :as chat-context]
            [wine-cellar.views.chat.actions :as chat-actions]
            [wine-cellar.views.chat.message :as chat-message]
            [wine-cellar.views.chat.input :as chat-input]
            [wine-cellar.views.chat.sidebar :as chat-sidebar]))

(defn- mobile? [] (chat-utils/mobile?))

(defn- chat-dialog-header
  [{:keys [app-state message-ref pending-image conversation-loading?
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
          [tooltip
           {:title (if sidebar-open? "Hide Conversations" "Conversations")}
           [:span
            [icon-button
             {:size "small"
              :disabled conversation-loading?
              :on-click on-toggle-sidebar
              :sx {:color "secondary.main"}}
             (if conversation-loading?
               [circular-progress {:size 18}]
               [forum {:fontSize "small"}])]]])]
    [dialog-title
     [box
      {:sx {:display "flex"
            :justify-content "space-between"
            :align-items "center"
            :gap (if is-mobile? 0.5 1)
            :flex-wrap "nowrap"}}
      [box {:sx {:display "flex" :align-items "center" :gap 0.75 :minWidth 0}}
       (when context-indicator context-indicator)]
      [box
       {:sx {:display "flex" :align-items "center" :gap (if is-mobile? 0.5 1)}}
       conversation-toggle
       [tooltip {:title "New chat"}
        [icon-button
         {:on-click
          #(chat-actions/clear-chat! app-state message-ref pending-image)
          :aria-label "New chat"
          :sx {:color "secondary.main"}} [add]]]
       [tooltip {:title "Close"}
        [icon-button
         {:on-click #(chat-actions/close-chat! app-state message-ref)
          :sx {:color "secondary.main"}} [close]]]]]]))

(defn- chat-main-column
  [{:keys [sidebar-open? show-camera? handle-camera-capture handle-camera-cancel
           messages message-edit-handler message-fork-handler handle-send
           is-sending? app-state handle-image-capture pending-image
           handle-image-remove message-ref is-editing? handle-cancel
           filter-panel on-cancel-request]}]
  (let
    [components
     (->
       []
       (cond-> filter-panel (conj filter-panel))
       (cond-> @show-camera? (conj [camera-capture handle-camera-capture
                                    handle-camera-cancel]))
       (conj [chat-message/list-view messages message-edit-handler
              message-fork-handler app-state])
       (cond->
         (is-editing?)
         (conj
          [box {:component "span" :sx {:display "block"}}
           [typography
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
  [{:keys [dialog-content-ref sidebar main-column error on-dismiss-error]}]
  [dialog-content
   {:ref #(reset! dialog-content-ref %) :sx {:pt 1.5 :pb 1.5 :px 2}}
   ;; The dialog covers the page's own banner, so chat failures show here.
   (when error
     [dismissable-banner
      {:text error
       :severity "error"
       :aria-label "Dismiss chat error"
       :on-dismiss on-dismiss-error
       :sx {:mb 2}}])
   (into [grid {:container true :spacing 2}]
         (cond-> []
           sidebar (conj sidebar)
           true (conj main-column)))])

(defn- chat-dialog-shell
  [{:keys [is-open on-close header-props content-props]}]
  [dialog
   {:open is-open
    :on-close on-close
    :max-width "md"
    :full-width true
    :PaperProps {:sx {:py 2 :px 2 :height "85vh"}}}
   (chat-dialog-header header-props) (chat-dialog-content content-props)])

(defn chat-dialog
  "Main chat dialog component"
  [app-state]
  (let [;; The conversation itself lives in app-state; this is a view of it
        ;; for the components that read or truncate it.
        messages (r/cursor app-state [:chat :messages])
        message-ref (r/atom nil)
        is-sending? (r/atom false)
        show-camera? (r/atom false)
        pending-image (r/atom nil)
        dialog-content-ref (r/atom nil)
        dialog-opened (r/atom false)
        sidebar-scroll-ref (r/atom nil)
        sidebar-scroll-requested? (r/atom false)
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
        handle-send
        (fn [message-text]
          (chat-utils/set-scroll-intent! app-state {:type :bottom})
          (cond (is-editing?) (when (seq message-text)
                                (chat-actions/handle-edit-send
                                 app-state
                                 editing-message-id
                                 message-ref
                                 is-sending?
                                 cancel-fn-atom
                                 handle-commit))
                (and (empty? message-text) (not @pending-image))
                (chat-actions/ask-again! app-state is-sending? cancel-fn-atom)
                :else (do (chat-actions/handle-send-message app-state
                                                            message-text
                                                            is-sending?
                                                            cancel-fn-atom
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
        message-edit-handler (fn [id text] (handle-edit id text message-ref))
        message-fork-handler
        (fn [id] (chat-actions/fork-conversation! app-state id is-sending?))]
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
            conversation-messages (vec (or (:messages chat-state) []))
            wines (or (:wines state) [])
            show-out-of-stock? (:show-out-of-stock? state)
            base-wines (if show-out-of-stock?
                         wines
                         (filter #(pos? (or (:quantity %) 0)) wines))
            total-count (count base-wines)
            wines-mode? (= :wines (state-core/context-mode state))
            visible-count
            (if wines-mode? (count (filtered-sorted-wines app-state)) 0)
            bar-view? (= :bar (:view state))
            context-indicator (if bar-view?
                                [typography
                                 {:variant "caption"
                                  :sx {:color "text.secondary"
                                       :fontSize "0.7rem"}} "Bar inventory"]
                                [chat-context/context-bar app-state])
            filter-count-info {:visible visible-count :total total-count}
            filter-panel (when (and (not bar-view?) wines-mode?)
                           (wine-filters/filter-bar app-state
                                                    filter-count-info
                                                    {:paper-sx
                                                     {:backgroundColor
                                                      "background.default"}}))
            toggle-sidebar!
            (fn []
              (let [opening? (not sidebar-open?)]
                (if opening?
                  (do (reset! sidebar-scroll-requested? true)
                      (js/setTimeout
                       #(when-let [el @dialog-content-ref]
                          (.scrollTo el #js {:top 0 :behavior "smooth"}))
                       50))
                  (chat-utils/set-scroll-intent! app-state {:type :bottom}))
                (reset! sidebar-scroll-ref nil)
                (swap! app-state update-in [:chat :sidebar-open?] not)))
            select-conversation!
            (fn [{:keys [id] :as conversation}]
              (let [already-active? (= id active-id)
                    current-search (:sidebar-search-text (:chat @app-state))]
                (swap! app-state assoc-in [:chat :saved-scroll-pos] nil)
                (when (seq current-search)
                  (swap! app-state update
                    :chat assoc
                    :local-search-term current-search
                    :current-match-index 0)
                  (chat-utils/set-scroll-intent! app-state
                                                 {:type :search-match}))
                (chat-actions/open-conversation! app-state conversation false)
                (when (and already-active? sidebar-open?) (toggle-sidebar!))))
            sidebar
            (chat-sidebar/conversation-sidebar
             app-state
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
                          :message-fork-handler message-fork-handler
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
                          :on-cancel-request handle-cancel-request})
            header-props {:app-state app-state
                          :message-ref message-ref
                          :pending-image pending-image
                          :conversation-loading? conversation-loading?
                          :sidebar-open? sidebar-open?
                          :on-toggle-sidebar toggle-sidebar!
                          :context-indicator context-indicator}
            content-props {:dialog-content-ref dialog-content-ref
                           :sidebar sidebar
                           :main-column main-column
                           :error (:error chat-state)
                           :on-dismiss-error
                           #(swap! app-state update :chat dissoc :error)}]
        (when (and is-open
                   active-id
                   (not messages-loading?)
                   (empty? conversation-messages))
          (api/fetch-conversation-messages! app-state active-id))
        (when (and is-open (not @dialog-opened) @dialog-content-ref)
          (reset! dialog-opened true)
          (let [saved-pos (get-in @app-state [:chat :saved-scroll-pos])]
            (js/setTimeout #(do (when @dialog-content-ref
                                  (.scrollTo @dialog-content-ref
                                             #js {:top (.-scrollHeight
                                                        @dialog-content-ref)
                                                  :behavior "smooth"}))
                                (chat-utils/set-scroll-intent!
                                 app-state
                                 (if saved-pos
                                   {:type :position :top saved-pos}
                                   {:type :bottom})))
                           200)))
        (when (not is-open) (reset! dialog-opened false))
        (chat-dialog-shell {:is-open is-open
                            :on-close #(chat-actions/close-chat! app-state
                                                                 message-ref)
                            :header-props header-props
                            :content-props content-props})))))

(defn wine-chat-fab
  "Floating action button for wine chat"
  []
  [fab
   {:color "primary"
    :sx {:position "fixed"
         :bottom "calc(16px + env(safe-area-inset-bottom))"
         :right 16
         :z-index 1000}
    ;; on-navigate opens the chat from the URL, and Back closes it again
    :on-click #(nav/open-modal! :chat)} [chat]])

(defn wine-chat
  "Main wine chat component with FAB and dialog"
  [app-state]
  [:div [wine-chat-fab] [chat-dialog app-state]])
