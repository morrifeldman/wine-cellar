(ns wine-cellar.views.chat.context
  (:require [clojure.string :as string]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.tooltip :refer [tooltip]]
            [reagent-mui.icons.close :refer [close]]
            [wine-cellar.state :as state-core]
            [wine-cellar.utils.filters :refer
             [filtered-sorted-wines filters-active?]]))

(def ^:private summary-context {:wine-ids [] :label "Summary only"})

(defn- wines-label
  ([wine-count] (wines-label wine-count nil))
  ([wine-count qualifier]
   (if (zero? wine-count)
     "No wines match"
     (str wine-count
          (when qualifier (str " " qualifier))
          (if (= 1 wine-count) " wine" " wines")))))

(defn- wine-name
  [{:keys [producer name vintage]}]
  (let [text (string/join " " (remove string/blank? [producer name vintage]))]
    (when-not (string/blank? text) text)))

(defn list-context
  "The wines the list offers the chat: the wine page being viewed, else the
   checked wines that the filters and history toggle leave visible, else
   everything visible."
  [app-state]
  (let [state @app-state
        visible-ids (map :id (filtered-sorted-wines app-state))
        wine-id (:selected-wine-id state)
        checked (:selected-wine-ids state)]
    (cond wine-id {:wine-ids [wine-id]
                   :label (or (some #(when (= wine-id (:id %)) (wine-name %))
                                    (:wines state))
                              (wines-label 1))}
          (seq checked) (let [ids (vec (sort (filter checked visible-ids)))]
                          {:wine-ids ids
                           :label (wines-label (count ids) "selected")})
          :else (let [ids (vec (sort visible-ids))]
                  {:wine-ids ids
                   :label (str (wines-label (count ids))
                               (when (and (seq ids) (filters-active? state))
                                 " (filtered)"))}))))

(defn- note-context
  [{:keys [wine_ids label]}]
  {:wine-ids (vec (sort wine_ids)) :label label})

(defn- conversation-context
  "What the active conversation was last discussing: its latest context note,
   or for a conversation from before notes existed, the wines it recorded."
  [state]
  (if-let [note (some :context-note
                      (rseq (vec (get-in state [:chat :messages]))))]
    (note-context note)
    (let [wine-ids (get-in state [:chat :active-conversation :wine_ids])]
      (if (seq wine-ids)
        {:wine-ids (vec (sort wine-ids)) :label (wines-label (count wine-ids))}
        summary-context))))

(defn chat-context
  "What the chat sees now, as {:wine-ids :label :source}. A held conversation
   (one just reopened, or one the user chose to keep on its wines) keeps them
   for as long as the list still shows what it showed when the hold began; any
   change to the list hands the chat back to it. :listed is what the list
   would give."
  [app-state]
  (let [state @app-state
        listed (list-context app-state)
        held-list-ids (get-in state [:chat :held-list-ids])]
    (cond (= :summary (state-core/context-mode state))
          (assoc summary-context :source :summary :listed listed)
          (and held-list-ids (= held-list-ids (:wine-ids listed)))
          (assoc (conversation-context state)
                 :source :conversation
                 :listed listed)
          :else (assoc listed :source :list :listed listed))))

(defn- keep-conversation-context!
  "Keep the chat on what the conversation was discussing rather than whatever
   the list shows now."
  [app-state]
  (swap! app-state assoc-in
    [:chat :held-list-ids]
    (:wine-ids (list-context app-state))))

(defn hold-conversation-context!
  [app-state]
  (state-core/set-context-mode! app-state :wines)
  (keep-conversation-context! app-state))

(defn same-note?
  [a b]
  (= (some-> a
             :wine_ids
             sort
             vec)
     (some-> b
             :wine_ids
             sort
             vec)))

(defn context-note
  "The context note a user turn needs, given the messages before it. Claude
   learns which wines are in focus only from these notes, so one is due
   whenever what the chat sees differs from the latest note — or, with no
   note yet, whenever there are wines to see."
  [app-state prior-messages]
  (when-not (= :bar (:view @app-state))
    (let [{:keys [wine-ids label]} (chat-context app-state)
          latest (some :context-note (rseq (vec prior-messages)))]
      (when (if latest
              (not= wine-ids (:wine-ids (note-context latest)))
              (seq wine-ids))
        {:label (if (seq wine-ids) label (:label summary-context))
         :wine_ids wine-ids}))))

(defn- count-sx
  [wine-count]
  (cond (zero? wine-count) {:color "text.secondary"}
        (> wine-count 50) {:color "common.white"
                           :backgroundColor "error.main"
                           :padding "2px 6px"
                           :borderRadius "999px"}
        (<= wine-count 15) {:color "success.main"}
        :else {:color "warning.main"}))

(def ^:private caption-sx {:fontSize "0.7rem" :lineHeight 1.2})

(defn- wines-caption
  [{:keys [wine-ids label]}]
  (let [wine-count (count wine-ids)
        count-text (str wine-count " ")]
    [typography
     {:variant "caption" :sx (merge caption-sx (count-sx wine-count))}
     (if (string/starts-with? label count-text)
       [:<> [:span {:style {:fontWeight 700}} (str wine-count)]
        (subs label (dec (count count-text)))]
       label)]))

(defn- summary-caption
  [app-state]
  [tooltip {:title "Include wines"}
   [typography
    {:variant "caption"
     :role "button"
     :aria-label "Include wines"
     :tabIndex 0
     :onClick #(state-core/set-context-mode! app-state :wines)
     :onKeyDown #(when (#{"Enter" " "} (.-key %))
                   (.preventDefault %)
                   (state-core/set-context-mode! app-state :wines))
     :sx (merge
          caption-sx
          {:color "text.secondary" :cursor "pointer" "&:hover" {:opacity 0.8}})}
    "Summary only"]])

(defn- switch-to-list-chip
  [app-state listed]
  (let [wine-count (count (:wine-ids listed))]
    [chip
     {:label (if (pos? wine-count)
               (str "Switch to the " (wines-label wine-count) " in your list")
               "Use your current list")
      :size "small"
      :variant "outlined"
      :color "secondary"
      :onClick #(swap! app-state update :chat dissoc :held-list-ids)
      :sx {:height 20 :fontSize "0.65rem" :maxWidth "100%"}}]))

(defn- keep-chip
  [app-state {:keys [wine-ids label]}]
  [chip
   {:label (if (seq wine-ids) (str "Keep " label) "Keep summary only")
    :size "small"
    :variant "outlined"
    :color "secondary"
    :onClick #(keep-conversation-context! app-state)
    :sx {:height 20 :fontSize "0.65rem" :maxWidth "100%"}}])

(defn- previous-caption
  [{:keys [label]}]
  [typography
   {:variant "caption"
    :sx (merge caption-sx {:color "text.secondary" :whiteSpace "nowrap"})}
   (str label " →")])

(defn context-bar
  "Shows what the chat sees and lets the user narrow it to the cellar summary
   or widen it back to wines."
  [app-state]
  (let [{:keys [wine-ids source listed] :as context} (chat-context app-state)
        summary? (empty? wine-ids)
        offer-list? (and (= :conversation source)
                         (not= wine-ids (:wine-ids listed)))
        ;; The list has moved away from what the conversation was about, so
        ;; the next message will change the wines; say so before it's sent.
        previous (when (and (= :list source)
                            (seq (get-in @app-state [:chat :messages])))
                   (let [previous (conversation-context @app-state)]
                     (when (not= wine-ids (:wine-ids previous)) previous)))]
    [box
     {:sx {:display "flex"
           :align-items "center"
           :gap 0.5
           :flex-wrap "wrap"
           :minWidth 0}}
     (cond (= :summary source) [summary-caption app-state]
           ;; The reopened conversation had moved to the summary; the chip
           ;; beside this is the way back to wines.
           (and summary? (= :conversation source))
           [typography
            {:variant "caption"
             :sx (merge caption-sx {:color "text.secondary"})} "Summary only"]
           :else [:<> (when previous [previous-caption previous])
                  [wines-caption context]
                  [tooltip {:title "Chat about the cellar summary only"}
                   [icon-button
                    {:size "small"
                     :aria-label "Chat about the cellar summary only"
                     :onClick #(state-core/set-context-mode! app-state :summary)
                     :sx {:p 0.25 :color "text.secondary"}}
                    [close {:sx {:fontSize "0.8rem"}}]]]])
     (when offer-list? [switch-to-list-chip app-state listed])
     (when previous [keep-chip app-state previous])]))
