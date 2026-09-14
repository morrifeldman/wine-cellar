(ns wine-cellar.state
  (:require [wine-cellar.nav :as nav]))

(def default-recipe-filters
  "Recipe-tab filter selections. They live in app-state so a trip to another
   bar tab and back (a recipe ingredient link, then Back) comes home to the
   same filtered list."
  {:search ""
   :tags #{}
   :spirits #{}
   :subspirits #{}
   :ingredients #{}
   :include-garnishes? false
   :show-ingredient-filter? false
   :open-ingredient-cat nil
   :makeable nil})

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
   :sensor-readings {:latest []
                     :series []
                     :loading-latest? false
                     :loading-series? false
                     :bucket "1d"
                     :range :30d
                     :device-id nil
                     :error nil}
   :blind-tastings {:list []
                    :loading? false
                    :error nil
                    :form {}
                    :show-form? false
                    :show-link-dialog? false
                    :linking-note-id nil
                    :submitting? false}
   :bar {:spirits []
         :inventory-items []
         :recipes []
         :loading? false
         :error nil
         :active-tab :recipes
         :show-spirit-form? false
         :editing-spirit-id nil
         :show-recipe-form? false
         :editing-recipe-id nil
         :new-spirit {}
         :new-recipe {:ingredients []}
         :recipe-filters default-recipe-filters}})

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
  "Add or remove a wine id from the multi-select set. The URL holds the set, so
   the checkbox changes the URL and reads its own state back from it."
  [app-state wine-id checked?]
  (let [ids (or (:selected-wine-ids @app-state) #{})]
    (nav/set-selected-wines!
     (if checked? (conj ids wine-id) (disj ids wine-id)))))

(defn toggle-selected-only!
  "Flip between the whole list and only the checked wines."
  [app-state]
  (nav/show-only-selected! (not (:show-selected-wines? @app-state))))

(defn clear-selected-wines!
  "Remove all manually selected wines and exit selected-only view."
  [app-state]
  (set-context-mode! app-state :summary)
  (nav/set-selected-wines! #{}))
