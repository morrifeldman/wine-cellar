(ns wine-cellar.views.wines.detail.drinking-window
  (:require [clojure.string :as str]
            [reagent-mui.icons.schedule :refer [schedule]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.text-field :refer [text-field]]
            [wine-cellar.views.components.form-dialog :refer [form-dialog]]
            [reagent.core :as r]
            [wine-cellar.views.wines.detail.fields :refer
             [editable-tasting-window-commentary]]
            [wine-cellar.api :as api]
            [wine-cellar.utils.vintage :as vintage]
            [wine-cellar.views.components :refer [detail-section]]
            [wine-cellar.views.components.ai-provider-toggle :refer
             [ai-button]]))

(defn wine-tasting-window-suggestion-buttons
  [app-state wine]
  (when-let [suggestion (get @app-state :window-suggestion)]
    [box {:sx {:mt 2 :display "flex" :gap 1 :flexWrap "wrap"}}
     [button
      {:variant "contained"
       :color "secondary"
       :size "small"
       :onClick (fn []
                  (let [{:keys [drink_from_year drink_until_year message]}
                        suggestion]
                    (api/update-wine app-state
                                     (:id wine)
                                     {:drink_from_year drink_from_year
                                      :drink_until_year drink_until_year
                                      :tasting_window_commentary message})
                    (swap! app-state dissoc :window-suggestion)))}
      "Apply Suggestion"]
     [button
      {:variant "outlined"
       :color "secondary"
       :size "small"
       :onClick (fn []
                  (let [{:keys [drink_from_year]} suggestion]
                    (api/update-wine app-state
                                     (:id wine)
                                     {:drink_from_year drink_from_year})))}
      "Apply From Year"]
     [button
      {:variant "outlined"
       :color "secondary"
       :size "small"
       :onClick (fn []
                  (let [{:keys [drink_until_year]} suggestion]
                    (api/update-wine app-state
                                     (:id wine)
                                     {:drink_until_year drink_until_year})))}
      "Apply Until Year"]
     [button
      {:variant "outlined"
       :color "secondary"
       :size "small"
       :onClick (fn []
                  (let [{:keys [message]} suggestion]
                    (api/update-wine app-state
                                     (:id wine)
                                     {:tasting_window_commentary message})))}
      "Apply Commentary"]
     [button
      {:variant "text"
       :color "secondary"
       :size "small"
       :onClick (fn [] (swap! app-state dissoc :window-suggestion))}
      "Dismiss"]]))

(defn wine-tasting-window-suggestion
  [app-state wine]
  (let [suggesting? (:suggesting-drinking-window? @app-state)]
    [box {:sx {:mt 2}}
     [box {:sx {:display "flex" :alignItems "center" :flexWrap "wrap" :gap 1}}
      [ai-button app-state
       {:label "Suggest Drinking Window"
        :busy-label "Suggesting..."
        :busy? suggesting?
        :on-click (fn []
                    (-> (api/suggest-drinking-window app-state wine)
                        (.then (fn [{:keys [drink_from_year drink_until_year
                                            confidence reasoning]
                                     :as suggestion}]
                                 (swap! app-state assoc
                                   :window-suggestion
                                   (assoc suggestion
                                          :message
                                          (str "Drinking window suggested: "
                                               drink_from_year
                                               " to " drink_until_year
                                               " (" confidence
                                               " confidence)\n\n" reasoning)))))
                        (.catch (fn [error]
                                  (swap! app-state assoc
                                    :error
                                    (str "Failed to suggest drinking window: "
                                         error))))))}]]
     [typography {:variant "body2" :sx {:mt 1}}
      (get-in @app-state [:window-suggestion :message])]
     [wine-tasting-window-suggestion-buttons app-state wine]]))

(defn- drinking-window-modal
  [app-state wine open?]
  (r/with-let
   [from-val (r/atom (when-let [y (:drink_from_year wine)] (str y))) until-val
    (r/atom (when-let [y (:drink_until_year wine)] (str y))) error-msg
    (r/atom nil)]
   [form-dialog
    {:open? true
     :title "Drinking Window"
     :on-close #(reset! open? false)
     :on-save
     (fn []
       (let [from (when-not (str/blank? @from-val) (js/parseInt @from-val 10))
             until (when-not (str/blank? @until-val)
                     (js/parseInt @until-val 10))
             err (vintage/valid-tasting-window? from until)]
         (if err
           (reset! error-msg err)
           (do (api/update-wine app-state
                                (:id wine)
                                {:drink_from_year from :drink_until_year until})
               (reset! open? false)))))}
    [text-field
     {:value (or @from-val "")
      :type "number"
      :label "Drink From Year"
      :fullWidth true
      :onChange
      (fn [e] (reset! from-val (.. e -target -value)) (reset! error-msg nil))}]
    [text-field
     {:value (or @until-val "")
      :type "number"
      :label "Drink Until Year"
      :fullWidth true
      :error (boolean @error-msg)
      :helperText @error-msg
      :onChange (fn [e]
                  (reset! until-val (.. e -target -value))
                  (reset! error-msg nil))}]]))

(defn wine-tasting-window-section
  [app-state wine]
  (r/with-let
   [open? (r/atom false)]
   [detail-section {:icon schedule :label "Drinking Window"}
    [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
     (let [status (vintage/tasting-window-status wine)
           window-text (vintage/format-tasting-window-text wine)]
       [typography
        {:variant "body2"
         :color (if (str/blank? window-text)
                  "text.secondary"
                  (vintage/tasting-window-color status))
         :sx {:fontStyle "italic"
              :cursor "pointer"
              :borderRadius 1
              :px 0.5
              :mx -0.5
              "&:hover" {:bgcolor "action.hover"}}
         :onClick #(reset! open? true)}
        (if (str/blank? window-text) "Set drinking window" window-text)])
     [box {:sx {:mt 1}} [editable-tasting-window-commentary app-state wine]]
     [wine-tasting-window-suggestion app-state wine]]
    (when @open? [drinking-window-modal app-state wine open?])]))
