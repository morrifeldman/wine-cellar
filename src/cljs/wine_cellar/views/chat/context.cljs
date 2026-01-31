(ns wine-cellar.views.chat.context
  (:require [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.typography :refer [typography]]
            [wine-cellar.api :as api]
            [wine-cellar.state :as state-core]
            [wine-cellar.utils.filters :refer [filtered-sorted-wines]]
            [wine-cellar.views.chat.utils :refer [combine-wine-lists]]))

(defn manual-context-wines
  [state]
  (let [base-selected-ids (or (:selected-wine-ids state) #{})
        selected-ids (cond-> base-selected-ids
                       (:selected-wine-id state) (conj (:selected-wine-id
                                                        state)))]
    (if (seq selected-ids)
      (let [wines (or (:wines state) [])]
        (vec (filter #(contains? selected-ids (:id %)) wines)))
      [])))

(defn context-wines
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

(defn context-indicator-props
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

(defn build-wine-search-state
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

(defn apply-wine-search-state!
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

(defn sync-conversation-context!
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

(defn indicator-button
  [context-mode indicator-props change-context-mode!]
  (let [{:keys [color label sx]} indicator-props
        context-cycle [:summary :selection :selection+filters]
        cycle-context-mode!
        (fn []
          (let [indexed (map-indexed vector context-cycle)
                idx (or (some (fn [[i mode]] (when (= mode context-mode) i))
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
                      "&:hover" {:backgroundColor "rgba(255,255,255,0.12)"}}]
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
       :sx (merge {:color color :fontSize "0.7rem" :lineHeight 1.2} sx)}
      label]]))
