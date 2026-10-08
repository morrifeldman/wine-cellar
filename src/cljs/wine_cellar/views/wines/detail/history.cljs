(ns wine-cellar.views.wines.detail.history
  (:require
    [clojure.string :as str]
    [goog.string :as gstring]
    [goog.string.format]
    [goog.object :as gobj]
    [reagent-mui.icons.wine-bar :refer [wine-bar]]
    [reagent-mui.icons.history :refer [history] :rename {history history-icon}]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.button :refer [button]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.text-field :refer [text-field]]
    [reagent-mui.material.autocomplete :refer [autocomplete]]
    [wine-cellar.views.components.form-dialog :refer [form-dialog]]
    ["@mui/material/TextField" :default TextField]
    [reagent-mui.material.table :refer [table]]
    [reagent-mui.material.table-body :refer [table-body]]
    [reagent-mui.material.table-cell :refer [table-cell]]
    [reagent-mui.material.table-row :refer [table-row]]
    [reagent.core :as r]
    [wine-cellar.api :as api]
    [wine-cellar.views.components.confirm :refer [confirm!]]
    [wine-cellar.common :as common]
    [wine-cellar.utils.formatting :refer [format-date-iso]]
    [wine-cellar.views.components :refer [detail-section oz-input-field]]))

(defn history-date-cell
  [record]
  [table-cell {:sx {:whiteSpace "nowrap"}}
   (format-date-iso (:occurred_at record))])

(defn history-change-cell
  [record]
  [table-cell
   {:sx {:color
         (if (pos? (:change_amount record)) "secondary.light" "error.light")
         :fontWeight "bold"}}
   (if (pos? (:change_amount record))
     (str "+" (:change_amount record))
     (:change_amount record))])

(def history-reason-display->key
  (into {} (for [[k v] common/inventory-reasons] [v k])))

(defn history-reason-cell
  [record]
  (let [reason-key (:reason record)
        display-label (get common/inventory-reasons
                           (str/lower-case (or reason-key ""))
                           reason-key)]
    [table-cell display-label]))

(defn- enrich-history-with-display-balance
  "Walk history chronologically, track open-bottle state, and attach
  :display_prev_quantity / :display_new_quantity to each row. The display values
  subtract 1 for an in-progress open bottle so the inventory-history balance cell
  matches the 'full bottles' count shown elsewhere (wine card, etc).

  Returns the records re-sorted in the original descending order."
  [history]
  (let [ascending (sort-by (juxt :occurred_at :id) history)
        enriched
        (reduce
         (fn [{:keys [is-open rows]} row]
           (let [open-before is-open
                 open-after (cond (= "coravin_pour" (:reason row)) true
                                  (and is-open (= "drunk" (:reason row))) false
                                  :else is-open)
                 dp (- (or (:previous_quantity row) 0) (if open-before 1 0))
                 dn (- (or (:new_quantity row) 0) (if open-after 1 0))]
             {:is-open open-after
              :rows (conj rows
                          (assoc row
                                 :display_prev_quantity dp
                                 :display_new_quantity dn))}))
         {:is-open false :rows []}
         ascending)]
    (reverse (:rows enriched))))

(defn history-balance-cell
  [record]
  (let [prev (or (:display_prev_quantity record) (:previous_quantity record))
        new (or (:display_new_quantity record) (:new_quantity record))]
    [table-cell {:sx {:whiteSpace "nowrap"}}
     (if-let [oq (:original_quantity record)]
       (if (= (str/lower-case (or (:reason record) "")) "restock")
         (let [prev-oq (- oq (:change_amount record))]
           (str prev " / " prev-oq " → " new " / " oq))
         (str prev " / " oq " → " new " / " oq))
       (str prev " → " new))]))

(defn history-notes-cell [record] [table-cell (:notes record)])

(defn coravin-pour-edit-dialog
  [app-state wine-id record open? on-close]
  (r/with-let
   [oz-atom (r/atom (str (js/Math.round (js/parseFloat (or (:oz record) "0")))))
    other-state
    (r/atom {:occurred_at (format-date-iso (:occurred_at record))
             :notes (or (:notes record) "")})]
   [form-dialog
    {:open? @open?
     :title "Edit Coravin Pour"
     :on-close on-close
     :max-width "sm"
     :on-delete (fn []
                  (confirm!
                   app-state
                   {:title "Delete this pour?"
                    :message "The open bottle's running total will adjust."
                    :confirm-label "Delete"
                    :danger? true
                    :on-confirm (fn []
                                  (api/delete-inventory-history app-state
                                                                wine-id
                                                                (:id record))
                                  (on-close))}))
     :on-save (fn []
                (let [amount (js/parseFloat @oz-atom)]
                  (when (and (not (js/isNaN amount)) (pos? amount))
                    (api/update-inventory-history
                     app-state
                     wine-id
                     (:id record)
                     {:oz amount
                      :occurred_at (:occurred_at @other-state)
                      :notes (when-not (str/blank? (:notes @other-state))
                               (str/trim (:notes @other-state)))})
                    (on-close))))}
    [oz-input-field oz-atom
     {:helper-text "Editing this updates the open bottle's running total."}]
    [text-field
     {:type "date"
      :label "Date"
      :value (:occurred_at @other-state)
      :onChange #(swap! other-state assoc :occurred_at (.. % -target -value))
      :fullWidth true
      :sx {"& input[type=date]::-webkit-calendar-picker-indicator"
           {:filter "invert(0.7)" :opacity 0.7}}}]
    [text-field
     {:label "Notes"
      :value (:notes @other-state)
      :onChange #(swap! other-state assoc :notes (.. % -target -value))
      :multiline true
      :rows 3
      :fullWidth true
      :variant "outlined"}]]))

(defn history-edit-dialog
  [app-state wine-id record open? on-close]
  (r/with-let
   [local-state (r/atom nil)]
   (when @open?
     (when (nil? @local-state)
       (let [reason-key (:reason record)
             display-label (get common/inventory-reasons
                                (str/lower-case (or reason-key ""))
                                reason-key)]
         (reset! local-state {:occurred_at (format-date-iso (:occurred_at
                                                             record))
                              :reason reason-key
                              :reason-display display-label
                              :bottles (str (abs (:change_amount record)))
                              :notes (:notes record)})))
     [form-dialog
      {:open? @open?
       :title "Edit History Record"
       :on-close on-close
       :max-width "sm"
       :on-delete (fn []
                    (confirm! app-state
                              {:title "Delete this history record?"
                               :message "The wine's quantity will NOT change."
                               :confirm-label "Delete"
                               :danger? true
                               :on-confirm (fn []
                                             (api/delete-inventory-history
                                              app-state
                                              wine-id
                                              (:id record))
                                             (on-close))}))
       :save-label "Save Changes"
       :on-save (fn []
                  (let [n (js/parseInt (:bottles @local-state) 10)
                        sign (if (neg? (:change_amount record)) -1 1)]
                    (when (and (not (js/isNaN n)) (pos? n))
                      (api/update-inventory-history
                       app-state
                       wine-id
                       (:id record)
                       (assoc @local-state :change_amount (* sign n)))
                      (on-close))))}
      [text-field
       {:type "date"
        :label "Date"
        :value (:occurred_at @local-state)
        :onChange #(swap! local-state assoc :occurred_at (.. % -target -value))
        :fullWidth true
        :sx {"& input[type=date]::-webkit-calendar-picker-indicator"
             {:filter "invert(0.7)" :opacity 0.7}}}]
      [text-field
       {:type "number"
        :label "Bottles"
        :value (:bottles @local-state)
        :onChange #(swap! local-state assoc :bottles (.. % -target -value))
        :fullWidth true
        :helperText "Changing this adjusts your cellar quantity"
        :InputProps {:inputProps {:step "1" :min "1"}}}]
      [autocomplete
       {:freeSolo true
        :options (sort (vals common/inventory-reasons))
        :value (:reason-display @local-state)
        :onInputChange
        (fn [_ new-display _]
          (let [k (get history-reason-display->key new-display new-display)]
            (swap! local-state assoc :reason k :reason-display new-display)))
        :renderInput (fn [params]
                       (let [props (gobj/clone params)]
                         (gobj/set props "label" "Reason")
                         (gobj/set props "variant" "outlined")
                         (gobj/set props "fullWidth" true)
                         (r/create-element TextField props)))}]
      [text-field
       {:label "Notes"
        :value (:notes @local-state)
        :onChange #(swap! local-state assoc :notes (.. % -target -value))
        :multiline true
        :rows 4
        :fullWidth true
        :variant "outlined"}]])))


(defn- pour-notes-text
  "Combined oz + user notes string for a coravin_pour history row."
  [record]
  (let [oz (:oz record)
        notes (:notes record)
        oz-str (when oz (str (gstring/format "%.1f" (js/parseFloat oz)) " oz"))]
    (cond (and oz-str (seq notes)) (str oz-str " — " notes)
          oz-str oz-str
          :else (or notes ""))))

(defn- coravin-pour-notes-cell [record] [table-cell (pour-notes-text record)])

(defn inventory-history-row
  [app-state wine-id record]
  (r/with-let [edit-open? (r/atom false)]
              (let [coravin? (= "coravin_pour" (:reason record))]
                [:<>
                 (when @edit-open?
                   (if coravin?
                     [coravin-pour-edit-dialog app-state wine-id record
                      edit-open? #(reset! edit-open? false)]
                     [history-edit-dialog app-state wine-id record edit-open?
                      #(reset! edit-open? false)]))
                 [table-row
                  {:onClick #(reset! edit-open? true)
                   :sx {:cursor "pointer" "&:hover" {:bgcolor "action.hover"}}}
                  [history-date-cell record] [history-change-cell record]
                  [history-reason-cell record] [history-balance-cell record]
                  (if coravin?
                    [coravin-pour-notes-cell record]
                    [history-notes-cell record])]])))

(defn- open-bottle-pour-row
  [app-state wine-id record]
  (r/with-let [edit-open? (r/atom false)]
              [:<>
               (when @edit-open?
                 [coravin-pour-edit-dialog app-state wine-id record edit-open?
                  #(reset! edit-open? false)])
               [table-row
                {:onClick #(reset! edit-open? true)
                 :sx {:cursor "pointer" "&:hover" {:bgcolor "action.hover"}}}
                [table-cell {:sx {:whiteSpace "nowrap" :color "text.secondary"}}
                 (format-date-iso (:occurred_at record))]
                [table-cell {:sx {:whiteSpace "nowrap"}}
                 (pour-notes-text record)]]]))

(defn open-bottle-section
  [app-state wine]
  (when (:open_bottle_opened_at wine)
    (let [bottle-oz (common/bottle-format->oz (:bottle_format wine))
          poured (or (some-> (:open_bottle_oz_poured wine)
                             js/parseFloat)
                     0)
          remaining (max 0 (js/Math.round (- bottle-oz poured)))
          history (get-in @app-state [:inventory-history (:id wine)])
          ;; coravin_pour rows since the most recent drunk row (or all if
          ;; none)
          last-drunk-id (->> history
                             (filter #(= (:reason %) "drunk"))
                             (map :id)
                             (apply max 0))
          pours (->> history
                     (filter #(and (= (:reason %) "coravin_pour")
                                   (> (:id %) last-drunk-id)))
                     (sort-by :id))]
      [detail-section {:icon wine-bar :label "Open Bottle"}
       [box
        {:sx {:display "flex" :alignItems "baseline" :gap 2 :flexWrap "wrap"}}
        [typography {:variant "body2" :color "text.secondary"}
         (str "Opened " (format-date-iso (:open_bottle_opened_at wine)))]
        [typography {:variant "body2"}
         (str "~"
              remaining
              " oz left "
              "(of "
              (js/Math.round bottle-oz)
              " oz)")]]
       (when (seq pours)
         [box {:sx {:mt 1.5 :overflow-x "auto"}}
          [table
           {:size "small" :sx {:width "100%" "& td" {:borderBottom "none"}}}
           [table-body
            (for [p pours]
              ^{:key (:id p)} [open-bottle-pour-row app-state (:id wine) p])]]])
       [box {:sx {:mt 1.5}}
        [button
         {:size "small"
          :variant "outlined"
          :color "primary"
          :onClick #(api/finish-open-bottle app-state (:id wine))}
         "Finish bottle"]]])))

(defn inventory-history-section
  [app-state wine]
  (let [raw-history (get-in @app-state [:inventory-history (:id wine)])
        history (enrich-history-with-display-balance raw-history)]
    [detail-section {:icon history-icon :label "Inventory History"}
     (if (empty? history)
       [typography
        {:variant "body2" :color "text.secondary" :fontStyle "italic"}
        "No inventory history recorded yet."]
       [box {:sx {:overflow-x "auto"}}
        [table
         {:size "small" :sx {:width "100%" "& td" {:borderBottom "none" :px 1}}}
         [table-body
          (for [record history]
            ^{:key (:id record)}
            [inventory-history-row app-state (:id wine) record])]]])]))
