(ns wine-cellar.views.wines.detail.cellar
  (:require [clojure.string :as str]
            [goog.string :as gstring]
            [goog.string.format]
            [goog.object :as gobj]
            [reagent-mui.icons.wine-bar :refer [wine-bar]]
            [reagent-mui.icons.inventory :refer [inventory]]
            [reagent-mui.icons.receipt :refer [receipt]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.tooltip :refer [tooltip]]
            [reagent-mui.material.autocomplete :refer [autocomplete]]
            [wine-cellar.views.components.form-dialog :refer [form-dialog]]
            ["@mui/material/TextField" :default TextField]
            [reagent.core :as r]
            [wine-cellar.api :as api]
            [wine-cellar.common :as common]
            [wine-cellar.utils.formatting :refer [format-date-iso]]
            [wine-cellar.views.components :refer
             [coravin-pour-dialog detail-section drink-dialog gift-dialog
              minus-menu quantity-control]]))

(defn- cellar-summary
  [wine]
  (let [open? (boolean (:open_bottle_opened_at wine))
        qty (max 0 (- (:quantity wine) (if open? 1 0)))
        original (:original_quantity wine)
        location (when-not (str/blank? (:location wine)) (:location wine))]
    (cond (and original location) (str qty " of " original " · " location)
          original (str qty " of " original)
          location (str qty " bottles · " location)
          :else (str qty " bottles"))))

(defn- open-bottle-level
  "Inline '~N of M oz left' display for a Coravin-opened bottle."
  [wine]
  (let [bottle-oz (common/bottle-format->oz (:bottle_format wine))
        poured (or (some-> (:open_bottle_oz_poured wine)
                           js/parseFloat)
                   0)
        remaining (max 0 (js/Math.round (- bottle-oz poured)))]
    [typography {:variant "body2" :color "text.secondary" :sx {:mt 0.5}}
     (str "Includes 1 Coravin-open bottle (~"
          remaining
          " of "
          (js/Math.round bottle-oz)
          " oz left)")]))

(defn- cellar-edit-modal
  [app-state wine open?]
  (r/with-let
   [laid-down-val (r/atom (when-let [q (:original_quantity wine)] (str q)))
    location-val (r/atom (or (:location wine) "")) error-msg (r/atom nil)]
   [form-dialog
    {:open? true
     :title "Cellar Stock"
     :on-close #(reset! open? false)
     :on-save (fn []
                (let [location (when-not (str/blank? @location-val)
                                 @location-val)
                      laid-down (when-not (str/blank? @laid-down-val)
                                  (js/parseInt @laid-down-val 10))
                      current-qty (:quantity wine)]
                  (if (and laid-down
                           (not (js/isNaN laid-down))
                           (< laid-down current-qty))
                    (reset! error-msg (str "Can't set laid down to " laid-down
                                           " — current stock is " current-qty))
                    (do (api/update-wine app-state
                                         (:id wine)
                                         {:location location
                                          :original_quantity laid-down})
                        (reset! open? false)))))}
    (let [open? (boolean (:open_bottle_opened_at wine))
          full (max 0 (- (:quantity wine) (if open? 1 0)))]
      [box {:sx {:display "flex" :flexDirection "column"}}
       [quantity-control app-state (:id wine) (:quantity wine) (str full)
        (:original_quantity wine)] (when open? [open-bottle-level wine])])
    [box {:sx {:borderTop "1px solid rgba(255,255,255,0.08)" :mt 0.5}}]
    [text-field
     {:value (or @laid-down-val "")
      :label "Laid Down"
      :type "number"
      :fullWidth true
      :size "small"
      :error (boolean @error-msg)
      :helperText (or @error-msg "Bottles originally purchased or laid down")
      :onChange (fn [e]
                  (reset! laid-down-val (.. e -target -value))
                  (reset! error-msg nil))}]
    [text-field
     {:value (or @location-val "")
      :label "Location"
      :fullWidth true
      :size "small"
      :placeholder "e.g. E2, Rack 2, Wine Fridge"
      :onChange (fn [e] (reset! location-val (.. e -target -value)))}]]))

(defn wine-cellar-section
  [app-state wine]
  (r/with-let
   [modal-open? (r/atom false) anchor-el (r/atom nil) gift-open? (r/atom false)
    coravin-open? (r/atom false) drink-open? (r/atom false)]
   (let [wine-id (:id wine)
         qty (:quantity wine)
         bottle-open? (boolean (:open_bottle_opened_at wine))]
     [detail-section {:icon inventory :label "Cellar"}
      [box {:sx {:display "flex" :alignItems "center" :gap 1}}
       [box
        {:sx {:flex 1
              :cursor "pointer"
              :borderRadius 1
              :px 0.5
              :mx -0.5
              "&:hover" {:bgcolor "action.hover"}}
         :onClick #(reset! modal-open? true)}
        [typography {:variant "body1"} (cellar-summary wine)]]
       [tooltip {:title "Bottle actions" :arrow true}
        [:span
         [icon-button
          {:size "small"
           :color "inherit"
           :disabled (zero? qty)
           :onClick
           (fn [e] (.stopPropagation e) (reset! anchor-el (.-currentTarget e)))}
          [wine-bar {:fontSize "small" :color "primary"}]]]]
       [minus-menu app-state wine-id anchor-el #{:drink :coravin-pour :gift}
        #(reset! gift-open? true) #(reset! coravin-open? true)
        #(reset! drink-open? true)]]
      (when bottle-open? [open-bottle-level wine])
      (when @modal-open? [cellar-edit-modal app-state wine modal-open?])
      (when @drink-open?
        [drink-dialog app-state wine-id qty drink-open?
         #(reset! drink-open? false)])
      (when @gift-open?
        [gift-dialog app-state wine-id gift-open? #(reset! gift-open? false)])
      (when @coravin-open?
        [coravin-pour-dialog app-state wine-id coravin-open?
         #(reset! coravin-open? false)])])))

(defn- provenance-summary
  [wine]
  (let [price (:price wine)
        purveyor (when-not (str/blank? (:purveyor wine)) (:purveyor wine))
        date (format-date-iso (:purchase_date wine))
        price-str (when price (str "$" (gstring/format "%.2f" price)))]
    (if (and (nil? price-str) (nil? purveyor) (nil? date))
      "Add purchase details"
      (str/join
       " "
       (filter identity
               [(when price-str (str "Paid " price-str))
                (when purveyor
                  (if price-str (str "from " purveyor) (str "From " purveyor)))
                (when date (str "on " date))])))))

(defn- provenance-edit-modal
  [app-state wine open?]
  (r/with-let
   [price-val (r/atom (when-let [p (:price wine)] (gstring/format "%.2f" p)))
    purveyor-val (r/atom (or (:purveyor wine) "")) date-val
    (r/atom (format-date-iso (:purchase_date wine)))]
   (let [all-wines (:wines @app-state)
         existing-purveyors (->> all-wines
                                 (map :purveyor)
                                 (filter #(and % (not (str/blank? %))))
                                 (distinct)
                                 (sort)
                                 (vec))]
     [form-dialog
      {:open? true
       :title "Purchase Details"
       :on-close #(reset! open? false)
       :on-save (fn []
                  (let [price (when-not (str/blank? @price-val)
                                (js/parseFloat @price-val))
                        purveyor (when-not (str/blank? @purveyor-val)
                                   @purveyor-val)
                        date (when-not (str/blank? @date-val) @date-val)]
                    (api/update-wine
                     app-state
                     (:id wine)
                     {:price price :purveyor purveyor :purchase_date date})
                    (reset! open? false)))}
      [text-field
       {:value (or @price-val "")
        :type "number"
        :label "Price"
        :fullWidth true
        :InputProps {:startAdornment "$"}
        :onChange (fn [e] (reset! price-val (.. e -target -value)))}]
      [autocomplete
       {:freeSolo true
        :options existing-purveyors
        :value @purveyor-val
        :onChange (fn [_ v] (when v (reset! purveyor-val v)))
        :onInputChange (fn [_ v _] (reset! purveyor-val v))
        :renderInput (fn [params]
                       (let [props (gobj/clone params)]
                         (gobj/set props "label" "Purchased From")
                         (gobj/set props "variant" "outlined")
                         (gobj/set props "fullWidth" true)
                         (r/create-element TextField props)))}]
      [text-field
       {:value (or @date-val "")
        :type "date"
        :label "Purchase Date"
        :fullWidth true
        :InputLabelProps {:shrink true}
        :onChange (fn [e] (reset! date-val (.. e -target -value)))}]])))

(defn wine-provenance-section
  [app-state wine]
  (r/with-let [open? (r/atom false)]
              [detail-section {:icon receipt :label "Provenance"}
               [box
                {:sx {:cursor "pointer"
                      :borderRadius 1
                      :px 0.5
                      :mx -0.5
                      "&:hover" {:bgcolor "action.hover"}}
                 :onClick #(reset! open? true)}
                [typography {:variant "body1"} (provenance-summary wine)]]
               (when @open? [provenance-edit-modal app-state wine open?])]))
