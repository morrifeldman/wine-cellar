(ns wine-cellar.views.chat.context
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button-base :refer [button-base]]
            [reagent-mui.material.list-item-icon :refer [list-item-icon]]
            [reagent-mui.material.list-item-text :refer [list-item-text]]
            [reagent-mui.material.menu :refer [menu]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.icons.arrow-drop-down :refer [arrow-drop-down]]
            [reagent-mui.icons.check :refer [check]]
            [wine-cellar.nav :as nav]
            [wine-cellar.utils.filters :refer
             [filtered-sorted-wines filters-active?]]))

;; What the chat sees is the list, unless the user picked something else:
;; [:chat :scope] is nil (follow the list), {:kind :summary},
;; {:kind :wine :wine-id id}, {:kind :conversation} (stay on what the
;; conversation was last discussing) or {:kind :earlier :wine-ids :label} (go
;; back to wines it discussed before that). Any pick but the summary lasts
;; until the list changes; changing it is a choice too, and the chat follows
;; it. Wines that came from the list remember its settings (:list-state), so
;; picking them again puts the filters back and the chat follows the list.

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

(defn- wine-context
  [state wine-id]
  {:wine-ids [wine-id]
   :label (or (some #(when (= wine-id (:id %)) (wine-name %)) (:wines state))
              (wines-label 1))})

(defn list-context
  "The wines the list offers the chat: the checked wines that the filters and
   history toggle leave visible, else everything visible. An open wine page
   doesn't narrow it; the chat offers that wine on its own."
  [app-state]
  (let [state @app-state
        visible-ids (map :id (filtered-sorted-wines app-state))
        checked (:selected-wine-ids state)]
    (if (seq checked)
      (let [ids (vec (sort (filter checked visible-ids)))]
        {:wine-ids ids :label (wines-label (count ids) "selected")})
      (let [ids (vec (sort visible-ids))]
        {:wine-ids ids
         :label (str (wines-label (count ids))
                     (when (and (seq ids) (filters-active? state))
                       " (filtered)"))}))))

(defn- list-key
  "What the user controls about the list. Wines arriving or being refetched
   don't count as a change; filtering, checking and the history toggle do.
   Missing values read as their defaults, so a restored list compares equal to
   the one it was restored from."
  [{:keys [filters selected-wine-ids show-out-of-stock?]}]
  {:filters filters
   :selected-wine-ids (set selected-wine-ids)
   :show-out-of-stock? (boolean show-out-of-stock?)})

(defn- read-list-state
  [s]
  (when (string? s)
    (try (let [v (reader/read-string s)] (when (map? v) (list-key v)))
         (catch :default _ nil))))

(defn- note-context
  [{:keys [wine_ids label list_state]}]
  (let [list-state (read-list-state list_state)]
    (cond-> {:wine-ids (vec (sort wine_ids)) :label label}
      list-state (assoc :list-state list-state))))

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

(defn- earlier-contexts
  "The other wines the conversation discussed before its latest context, most
   recent first, each once."
  [state]
  (let [notes (->> (get-in state [:chat :messages])
                   (keep :context-note)
                   (map note-context)
                   reverse)
        latest-ids (:wine-ids (first notes))]
    (->> (rest notes)
         (remove #(or (empty? (:wine-ids %)) (= latest-ids (:wine-ids %))))
         (reduce
          (fn [acc {:keys [wine-ids] :as context}]
            (if (some #(= wine-ids (:wine-ids %)) acc) acc (conj acc context)))
          [])
         (take 3))))

(defn summary?
  "Whether the chat is set to see only the cellar summary."
  [state]
  (= :summary (get-in state [:chat :scope :kind])))

(defn chat-context
  "What the chat sees now, as {:wine-ids :label :source}, :source being the
   scope it came from (:list, :wine, :conversation or :summary)."
  [app-state]
  (let [state @app-state
        {:keys [kind wine-id]} (get-in state [:chat :scope])]
    (case kind
      :summary (assoc summary-context :source :summary)
      :wine (assoc (wine-context state wine-id) :source :wine)
      :conversation (assoc (conversation-context state) :source :conversation)
      :earlier (-> (get-in state [:chat :scope])
                   (select-keys [:wine-ids :label :list-state])
                   (assoc :source :earlier))
      (assoc (list-context app-state)
             :source :list
             :list-state (list-key state)))))

(defn- set-scope!
  [app-state scope]
  (swap! app-state assoc-in [:chat :scope] scope))

(defn- fresh-scope
  "A conversation with nothing in it yet is about the page it starts on: the
   wine whose page is open, else the list."
  [state]
  (when-let [wine-id (:selected-wine-id state)] {:kind :wine :wine-id wine-id}))

(defn start-fresh!
  "Point a new, empty conversation at the page it starts on."
  [app-state]
  (set-scope! app-state (fresh-scope @app-state)))

(defn chat-opened!
  "Opening the chat on an empty conversation points it at the page it opened
   on, unless the user had narrowed it to the summary."
  [app-state]
  (let [{:keys [chat] :as state} @app-state]
    (when (and (empty? (:messages chat))
               (nil? (:active-conversation-id chat))
               (not (summary? state)))
      (set-scope! app-state (fresh-scope state)))))

(defn hold-conversation-context!
  "Keep a reopened conversation on what it was discussing until the user picks
   something else or changes the list."
  [app-state]
  (set-scope! app-state {:kind :conversation}))

(defn follow-list-changes!
  "When the user changes the list, any pick but the summary gives way to it, so filtering from the chat always moves what the chat sees."
  [app-state]
  (add-watch app-state
             ::follow-list
             (fn [_ ref old new]
               (when (and (#{:wine :conversation :earlier}
                           (get-in new [:chat :scope :kind]))
                          (not= (list-key old) (list-key new)))
                 (swap! ref update :chat dissoc :scope)))))

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
    (let [{:keys [wine-ids label list-state]} (chat-context app-state)
          latest (some :context-note (rseq (vec prior-messages)))]
      (when (if latest
              (not= wine-ids (:wine-ids (note-context latest)))
              (seq wine-ids))
        (cond-> {:label (if (seq wine-ids) label (:label summary-context))
                 :wine_ids wine-ids}
          (and (seq wine-ids) list-state) (assoc :list_state
                                                 (pr-str list-state)))))))

(defn- count-sx
  [wine-count]
  (cond (zero? wine-count) {:color "text.secondary"}
        (> wine-count 50) {:color "common.white"
                           :backgroundColor "error.main"
                           :padding "0 5px"
                           :borderRadius "999px"}
        (<= wine-count 15) {:color "success.main"}
        :else {:color "warning.main"}))

(def ^:private caption-sx {:fontSize "0.7rem" :lineHeight 1.2})

(defn- context-label
  "The label with its leading count picked out in the count's colour."
  [{:keys [wine-ids label source]}]
  (let [wine-count (count wine-ids)
        count-text (str wine-count " ")]
    (cond (and (zero? wine-count) (not= :list source)) "cellar summary only"
          (string/starts-with? label count-text)
          [:<>
           [:span {:style {:fontWeight 700}}
            [box {:component "span" :sx (count-sx wine-count)}
             (str wine-count)]] (subs label (dec (count count-text)))]
          :else label)))

(defn- earlier-heading
  "Picking wines that came from the list puts its filters back, so say so
   while the list shows something else."
  [state {:keys [list-state]}]
  (if (and list-state (not= list-state (list-key state)))
    "Earlier in this chat · tap to filter the list"
    "Earlier in this chat"))

(defn- scope-options
  "What the chat could look at instead, in menu order. Options that would show
   the same wines as one before them are left out."
  [app-state current previous]
  (let [state @app-state
        page-wine-id (:selected-wine-id state)
        candidates [(assoc (list-context app-state)
                           :source :list
                           :heading "Your list"
                           :scope nil)
                    (when page-wine-id
                      (assoc (wine-context state page-wine-id)
                             :source :wine
                             :heading "This wine"
                             :scope {:kind :wine :wine-id page-wine-id}))
                    ;; A conversation last on the summary is covered by the
                    ;; summary option
                    (when (seq (:wine-ids previous))
                      (assoc previous
                             :source :conversation
                             :heading (earlier-heading state previous)
                             :scope {:kind :conversation}))]
        earlier (for [{:keys [wine-ids] :as context} (earlier-contexts state)]
                  (assoc context
                         :source [:earlier wine-ids]
                         :heading (earlier-heading state context)
                         :scope (-> context
                                    (select-keys [:wine-ids :label :list-state])
                                    (assoc :kind :earlier))))
        summary
        [(assoc summary-context :source :summary :scope {:kind :summary})]]
    (->> (concat (remove nil? candidates) earlier summary)
         (reduce (fn [[seen options] {:keys [wine-ids source] :as option}]
                   (let [k (if (= :summary source) ::summary wine-ids)]
                     (if (contains? seen k)
                       [seen options]
                       [(conj seen k)
                        (conj options
                              (assoc option
                                     :selected?
                                     (if (= :summary (:source current))
                                       (= :summary source)
                                       (and (not= :summary source)
                                            (= wine-ids
                                               (:wine-ids current))))))])))
                 [#{} []])
         second)))

(defn- pick!
  "Point the chat at a menu option. Wines that came from the list put its
   settings back, so the list shows them again and the chat follows it; the
   rest are held on their own."
  [app-state {:keys [scope source list-state]}]
  (if (and list-state (not= :list source))
    (let [{:keys [filters show-out-of-stock? selected-wine-ids]} list-state]
      (swap! app-state #(-> %
                            (assoc :filters filters
                                   :show-out-of-stock? (boolean
                                                        show-out-of-stock?))
                            (update :chat dissoc :scope)))
      ;; The URL owns the checked wines
      (nav/set-selected-wines! (or selected-wine-ids #{})))
    (set-scope! app-state scope)))

(defn context-bar
  "Says what the chat sees and lets the user point it elsewhere: the list, the
   wine whose page is open, what the conversation was discussing, or only the
   cellar summary."
  [_app-state]
  (let [anchor-el (r/atom nil)]
    (fn [app-state]
      (let [state @app-state
            current (chat-context app-state)
            previous (when (seq (get-in state [:chat :messages]))
                       (conversation-context state))
            ;; The next message will move the conversation onto new wines.
            switching? (and previous
                            (not= (:wine-ids current) (:wine-ids previous)))
            close! #(reset! anchor-el nil)]
        [box {:sx {:display "flex" :align-items "center" :minWidth 0}}
         [button-base
          {:aria-label "Choose what the chat sees"
           :aria-haspopup "menu"
           :on-click #(reset! anchor-el (.-currentTarget %))
           :sx {:display "flex"
                :align-items "center"
                :minWidth 0
                :borderRadius 1
                :px 0.5
                :py 0.25
                :ml -0.5
                :color "text.secondary"
                "&:hover" {:backgroundColor "action.hover"}}}
          [typography
           {:variant "caption"
            :noWrap true
            :sx (merge caption-sx {:color "text.primary" :minWidth 0})}
           (if switching?
             [box {:component "span" :sx {:color "secondary.main"}}
              "Switching to "]
             [box {:component "span" :sx {:opacity 0.7}} "Discussing "])
           [context-label current]]
          [arrow-drop-down {:sx {:fontSize "1rem" :flexShrink 0}}]]
         [menu
          {:anchor-el @anchor-el :open (boolean @anchor-el) :on-close close!}
          (for [{:keys [heading selected? source] :as option}
                (scope-options app-state current previous)]
            ^{:key (str (:source option))}
            [menu-item
             {:selected selected?
              :dense true
              :on-click #(do (close!) (pick! app-state option))}
             [list-item-icon
              (when selected? [check {:fontSize "small" :color "primary"}])]
             [list-item-text
              {:primary (if (= :summary source)
                          "Cellar summary only"
                          (r/as-element [context-label option]))
               :secondary heading
               :primaryTypographyProps {:variant "body2"}
               :secondaryTypographyProps {:variant "caption"}}]])]]))))
