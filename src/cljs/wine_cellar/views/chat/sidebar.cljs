(ns wine-cellar.views.chat.sidebar
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [reagent-mui.material.grid :refer [grid]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.input-adornment :refer [input-adornment]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.icons.close :refer [close]]
            [reagent-mui.icons.edit :refer [edit]]
            [reagent-mui.icons.star :refer [star]]
            [reagent-mui.icons.star-border :refer [star-border]]
            [reagent-mui.icons.delete :refer [delete]]
            [reagent-mui.icons.arrow-upward :refer [arrow-upward]]
            [reagent-mui.icons.arrow-downward :refer [arrow-downward]]
            [wine-cellar.views.chat.utils :as chat-utils]))

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
           on-pin on-select]} {:keys [id match_count] :as conversation}]
  (let [active? (= id active-id)
        deleting? (= id deleting-id)
        renaming? (= id renaming-id)
        pinning? (= id pinning-id)
        ;; For active conversation, use client-side match count for
        ;; consistency with navigation counter
        chat-state (:chat @app-state)
        display-match-count (if (and active? (seq (:search-matches chat-state)))
                              (count (:search-matches chat-state))
                              match_count)]
    [box
     {:on-click #(if on-select
                   (on-select conversation)
                   ;; Fallback if no select handler, though logic should be
                   ;; in action
                   (js/console.warn
                    "Warning: No on-select handler for conversation row"))
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
      [box {:sx {:display "flex" :flex-direction "column" :overflow "hidden"}}
       [typography
        {:variant "body2"
         :noWrap true
         :sx {:fontWeight (if active? "600" "500")
              :color (if active? "inherit" "text.primary")}}
        (conversation-label conversation)]
       (when (and display-match-count (pos? display-match-count))
         [typography
          {:variant "caption"
           :sx {:color "warning.main" :fontSize "0.7rem" :fontWeight 600}}
          (str display-match-count
               " match"
               (when (not= 1 display-match-count) "es"))])]
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

(defn- conversation-search-bar
  [app-state search-text handle-search]
  (let [chat-state (:chat @app-state)
        term (:local-search-term chat-state)
        matches (:search-matches chat-state [])
        current-idx (:current-match-index chat-state 0)
        total (count matches)
        search-val search-text
        active? (and (seq term) (= term search-val) (pos? total))]
    [box {:sx {:p 1 :border-bottom "1px solid" :border-color "divider"}}
     [mui-text-field/text-field
      {:fullWidth true
       :size "small"
       :placeholder "Search..."
       :variant "outlined"
       :value search-val
       :onChange (fn [e] (handle-search (.. e -target -value)))
       :InputProps
       {:endAdornment
        (when (or (seq search-val) active?)
          (r/as-element
           [input-adornment {:position "end"}
            [box {:sx {:display "flex" :alignItems "center" :gap 0.5}}
             (when active?
               [:<>
                [typography
                 {:variant "caption"
                  :aria-label "search match counter"
                  :sx {:color "text.secondary"
                       :whiteSpace "nowrap"
                       :mx 0.5
                       :minWidth "30px"
                       :textAlign "center"}}
                 (str (inc current-idx) " / " total)]
                [box {:sx {:display "flex" :flexDirection "column"}}
                 [icon-button
                  {:size "small"
                   :aria-label "previous match"
                   :on-click (fn [_]
                               (let [next-idx (if (<= current-idx 0)
                                                (dec total)
                                                (dec current-idx))]
                                 (swap! app-state assoc-in
                                   [:chat :current-match-index]
                                   next-idx)
                                 (chat-utils/set-scroll-intent!
                                  app-state
                                  {:type :search-match})))
                   :sx {:p 0 :color "text.secondary"}}
                  [arrow-upward {:fontSize "1rem"}]]
                 [icon-button
                  {:size "small"
                   :aria-label "next match"
                   :on-click (fn [_]
                               (let [next-idx (if (>= current-idx (dec total))
                                                0
                                                (inc current-idx))]
                                 (swap! app-state assoc-in
                                   [:chat :current-match-index]
                                   next-idx)
                                 (chat-utils/set-scroll-intent!
                                  app-state
                                  {:type :search-match})))
                   :sx {:p 0 :color "text.secondary"}}
                  [arrow-downward {:fontSize "1rem"}]]]])
             (when (seq search-val)
               [icon-button
                {:size "small"
                 :onClick #(handle-search "")
                 :sx {:p 0.5 :color "text.secondary"}}
                [close {:fontSize "small"}]])]]))}}]]))

(defn conversation-sidebar
  [app-state messages
   {:keys [open? conversations loading? active-id deleting-id renaming-id
           pinning-id scroll-ref scroll-requested? on-delete on-rename on-pin
           on-select search-text handle-search]}]
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
        [conversation-search-bar app-state search-text handle-search]
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
