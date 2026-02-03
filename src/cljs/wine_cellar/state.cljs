(ns wine-cellar.state)

(def initial-app-state
  "Initial app state structure - shared between initialization and reset"
  {:wines []
   :classifications []
   :new-wine {:bottle_format "Standard (750ml)"}
   :error nil
   :loading? false
   :selected-wine-id nil
   :selected-wine-ids #{}
   :show-out-of-stock? false
   :show-selected-wines? false
   :show-wine-form? false
   :show-stats? false
   :stats-metric :bottles
   :stats-window-group :overall
   :show-filters? false
   :show-debug-controls? false
   :tasting-notes []
   :new-tasting-note {}
   :sort {:field :created_at :direction :desc}
   :filters
   {:search "" :country nil :region nil :styles [] :style nil :varieties []}
   :view nil
   :verbose-logging
   {:enabled? false :loading? false :updating? false :error nil}
   :ai {:provider nil ; Will be set from backend default
        :models nil}
   :chat {:open? false
          :messages []
          :conversations []
          :active-conversation nil
          :active-conversation-id nil
          :conversation-loading? false
          :conversations-loaded? false
          :messages-loading? false
          :creating-conversation? false
          :pinning-conversation-id nil
          :renaming-conversation-id nil
          :deleting-conversation-id nil
          :sidebar-open? false
          :context-mode :summary
          :include-visible-wines? false
          :error nil}
   :cellar-conditions {:latest []
                       :series []
                       :loading-latest? false
                       :loading-series? false
                       :bucket "1d"
                       :range :all
                       :device-id nil
                       :error nil}
   :blind-tastings {:list []
                    :loading? false
                    :error nil
                    :form {}
                    :show-form? false
                    :show-link-dialog? false
                    :linking-note-id nil
                    :submitting? false}})

(def ^:private context-modes #{:summary :selection :selection+filters})

(def ^:private context-mode-default :summary)

(defn- normalize-context-mode
  [mode]
  (if (context-modes mode) mode context-mode-default))

(defn- context-mode->include?
  [mode]
  (contains? #{:selection :selection+filters} mode))

(defn context-mode
  "Return the effective chat context mode from state."
  [state]
  (let [mode (get-in state [:chat :context-mode])]
    (cond (context-modes mode) mode
          (get-in state [:chat :include-visible-wines?]) :selection+filters
          (seq (:selected-wine-ids state)) :selection
          (:selected-wine-id state) :selection
          :else context-mode-default)))

(defn set-context-mode!
  "Update chat context mode and keep legacy include flag in sync."
  [app-state mode]
  (let [mode (normalize-context-mode mode)]
    (swap! app-state (fn [state]
                       (-> state
                           (assoc-in [:chat :context-mode] mode)
                           (assoc-in [:chat :include-visible-wines?]
                                     (context-mode->include? mode)))))))

(defn include-wines?
  "Whether the current context should include any wines when chatting."
  [state]
  (context-mode->include? (context-mode state)))

(defn filters-enabled?
  "Whether the current context should include filtered wines."
  [state]
  (= :selection+filters (context-mode state)))

(defn toggle-wine-selection!
  "Add or remove a wine id from the multi-select set."
  [app-state wine-id checked?]
  (swap! app-state (fn [state]
                     (let [ids (or (:selected-wine-ids state) #{})
                           new-ids
                           (if checked? (conj ids wine-id) (disj ids wine-id))
                           new-state (assoc state :selected-wine-ids new-ids)]
                       (if (and (:show-selected-wines? new-state)
                                (empty? new-ids))
                         (assoc new-state :show-selected-wines? false)
                         new-state)))))

(defn clear-selected-wines!
  "Remove all manually selected wines and exit selected-only view."
  [app-state]
  (swap! app-state assoc :selected-wine-ids #{} :show-selected-wines? false)
  (set-context-mode! app-state :summary))
