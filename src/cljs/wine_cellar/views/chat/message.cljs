(ns wine-cellar.views.chat.message
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.edit :refer [edit]]
            [reagent-mui.icons.close :refer [close]]
            [wine-cellar.views.chat.utils :as chat-utils :refer
             [escape-regex execute-scroll-intent!]]))

(def ^:private edit-icon-size "0.8rem")
(def ^:private edit-button-spacing 4)

(defn- highlight-text
  [text search-term is-user global-offset current-match-idx]
  (if (or (string/blank? text) (string/blank? search-term))
    text
    (let [escaped (escape-regex search-term)
          regex (js/RegExp. escaped "gi")
          matches (vec (.match text regex))]
      (if (empty? matches)
        text
        (let [parts (string/split text regex)]
          (into
           [:<>] ; This is a vector literal, not a string, so no escaping
                 ; needed here.
           (map-indexed
            (fn [idx part] ; This is a function definition, not a string.
              (if (< idx (count matches))
                (let [is-active? (= (+ global-offset idx) current-match-idx)]
                  [:<> part
                   [box
                    (cond-> {:component "span"
                             :sx {:backgroundColor (if is-active?
                                                     "warning.main"
                                                     (if is-user
                                                       "rgba(255,255,255,0.3)"
                                                       "warning.light"))
                                  :color (if is-active?
                                           "white"
                                           (if is-user "inherit" "black"))
                                  :fontWeight (if is-active? 700 "normal")
                                  :borderRadius "2px"
                                  :boxShadow (if is-active?
                                               "0 0 4px rgba(0,0,0,0.5)"
                                               "none")
                                  :px "2px"}}
                      is-active? (assoc :id "active-search-match"))
                    (nth matches idx)]])
                ; literal.
                part)) ; This is a string.
            parts)))))))

(defn- render-rich-text
  [text app-state is-user search-term global-offset current-match-idx]
  (let [link-pattern #"\[([^\]]+)\]\(wine:(\d+)\)"] ; Regex literal, no
                                                    ; escaping issues.
    (if (string/blank? text)
      "" ; Empty string literal.
      (let [link-matches (re-seq link-pattern text)]
        (if (empty? link-matches)
          (highlight-text text
                          search-term
                          is-user
                          global-offset
                          current-match-idx)
          (let [split-regex (js/RegExp. "\\[[^\\]]+\\]\\(wine:\\d+\\)") ; Regex
                                                                        ; literal.
                parts (string/split text split-regex)
                ;; We need to track how many search matches we found in the
                ;; plain text parts to keep the global-offset accurate.
                ;; This is complex because highlight-text handles its own
                ;; internal mapping. For now, we assume search-term doesn't
                ;; overlap with link structure.
               ]
            (into
             [:<>] ; Vector literal.
             (first
              (reduce
               (fn [[elements offset] [idx part]] ; Function definition.
                 (let [escaped-term (escape-regex search-term)
                       match-count
                       (if (seq search-term)
                         (count (vec (.match part
                                             (js/RegExp. escaped-term "gi")))) ; Regex
                                                                               ; literal.
                         0)
                       highlighted-part (highlight-text part
                                                        search-term
                                                        is-user
                                                        offset
                                                        current-match-idx)
                       new-elements
                       (if (< idx (count link-matches))
                         (let [[_ link-text wine-id] (nth link-matches idx)]
                           (conj
                            elements
                            [:<> highlighted-part ; Vector literal.
                             [typography ; Vector literal.
                              {:component "span" ; Map literal.
                               :variant "body2"
                               :sx {:color (if is-user
                                             "secondary.light"
                                             "primary.light")
                                    :textDecoration "underline"
                                    :cursor "pointer"
                                    :fontWeight 600
                                    :whiteSpace "nowrap"
                                    :lineHeight "inherit"
                                    :&:hover {:color (if is-user
                                                       "common.white"
                                                       "primary.main")}} ; Map
                                                                         ; literal.
                               :on-click (fn [e] ; Function definition.
                                           (.preventDefault e)
                                           (.stopPropagation e)
                                           (swap! app-state
                                             (fn [state] ; Function
                                                         ; definition.
                                               (-> state
                                                   (assoc :selected-wine-id
                                                          (js/parseInt wine-id))
                                                   (assoc-in [:chat :open?] ; Keyword
                                                                            ; literal.
                                                             false)))))} ; Map
                                                                         ; literal.
                              link-text]])) ; Vector literal.
                         (conj elements highlighted-part))] ; Vector literal.
                   [new-elements (+ offset match-count)])) ; Vector literal.
               [[] global-offset] ; Vector literal.
               (map-indexed vector parts)))))))))) ; Vector literal.

(defn message-bubble
  "Renders a single chat message bubble"
  [{:keys [text is-user timestamp id]} on-edit app-state global-offset
   last-ai-id]
  (let [chat-state (:chat @app-state)
        search-term (:local-search-term chat-state)
        current-match-idx (:current-match-index chat-state 0)]
    [box
     (cond-> {:sx {:display "flex"
                   :justify-content (if is-user "flex-end" "flex-start")
                   :mb 1}}
       (= id last-ai-id) (assoc :id "latest-ai-response"))
     [paper ; Vector literal.
      {:elevation 2
       :sx {:p 2
            :max-width "80%"
            :background-color (if is-user "primary.main" "container.main")
            :color (if is-user "background.default" "text.primary")
            :word-wrap "break-word"
            :white-space "pre-wrap"
            :border-radius 2
            :position "relative"} ; Map literal.
       :on-click #(on-edit id text)} ; This is a function literal, not a
                                     ; string.
      [typography ; Vector literal.
       {:variant "body2"
        :sx {:color "inherit"
             :white-space "pre-wrap"
             :word-wrap "break-word"
             :line-height 1.6}} ; Map literal.
       (render-rich-text text
                         app-state
                         is-user
                         search-term
                         global-offset
                         current-match-idx)]
      (when timestamp
        [typography ; Vector literal.
         {:variant "caption"
          :sx {:display "block" :mt 0.5 :opacity 0.7 :font-size "0.7em"}} ; Map
                                                                          ; literal.
         (.toLocaleTimeString (js/Date. timestamp))])
      (when (and is-user on-edit)
        [icon-button ; Vector literal.
         {:size "small"
          :sx {:position "absolute"
               :bottom edit-button-spacing
               :right edit-button-spacing
               :color "inherit"
               :opacity 0.7
               :&:hover {:opacity 1}} ; Map literal.
          :on-click #(on-edit id text)} ; Function literal.
         [edit {:sx {:font-size edit-icon-size}}]])]])) ; Vector literal.

(defn attached-image-preview
  [image on-remove]
  [box ; Vector literal.
   {:sx
    {:mb 1 :p 1 :border "1px solid" :border-color "divider" :border-radius 1}} ; Map
                                                                               ; literal.
   [box ; Vector literal.
    {:sx {:display "flex"
          :justify-content "space-between"
          :align-items "center"
          :mb 1}} ; Map literal.
    [typography {:variant "caption" :color "text.secondary"} "Attached image:"] ; Vector
                                                                                ; literal.
    [icon-button ; Vector literal.
     {:size "small" :on-click on-remove :sx {:color "secondary.main"}} ; Map
                                                                       ; literal.
     [close {:sx {:font-size "0.8rem"}}]]] ; Vector literal.
   [box ; Vector literal.
    {:component "img"
     :src image
     :sx {:max-width "100%"
          :max-height "150px"
          :object-fit "contain"
          :border-radius 1}}]]) ; Map literal.

(defn list-view
  "Scrollable container for chat messages. Scroll behavior is controlled
   declaratively via [:chat :scroll-intent] in app-state."
  [messages on-edit app-state]
  (let [scroll-ref (r/atom nil)
        messages-atom messages
        edit-handler on-edit]
    (r/create-class
     {:component-did-update (fn [_]
                              (execute-scroll-intent! app-state @scroll-ref))
      :reagent-render
      (fn []
        (let [current-messages @messages-atom
              chat-state (:chat @app-state)
              search-term (:local-search-term chat-state)
              prefix-match-counts
              (if (seq search-term)
                (let [escaped (escape-regex search-term)
                      regex (js/RegExp. escaped "gi")]
                  (first (reduce (fn [[acc total] msg]
                                   [(conj acc total)
                                    (+ total
                                       (count (vec (.match (or (:text msg) "")
                                                           regex))))])
                                 [[] 0]
                                 current-messages)))
                (repeat (count current-messages) 0))
              last-ai-id (->> current-messages
                              (filter #(not (:is-user %)))
                              last
                              :id)]
          [box
           {:ref #(reset! scroll-ref %)
            :on-scroll (fn [e]
                         (when (get-in @app-state [:chat :open?])
                           (swap! app-state assoc-in
                             [:chat :saved-scroll-pos]
                             (.. e -target -scrollTop))))
            :sx {:height "360px"
                 :overflow-y "auto"
                 :p 2
                 :background-color "background.default"
                 :border "1px solid"
                 :border-color "divider"
                 :border-radius 1}}
           (if (empty? current-messages)
             [typography
              {:variant "body2"
               :sx {:text-align "center" :color "text.secondary" :mt 2}}
              "Start a conversation about your wine cellar..."]
             (doall (for [[idx message] (map-indexed vector current-messages)]
                      (let [msg-id (:id message)
                            global-offset (nth prefix-match-counts idx)]
                        ^{:key msg-id}
                        [message-bubble message edit-handler app-state
                         global-offset last-ai-id]))))]))})))
