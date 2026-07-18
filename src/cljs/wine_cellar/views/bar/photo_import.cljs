(ns wine-cellar.views.bar.photo-import
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.icons.camera-alt :refer [camera-alt]]
            [reagent-mui.icons.file-upload :refer [file-upload]]
            [wine-cellar.views.components.image-upload :refer
             [camera-capture file->jpeg-data-url]]
            [wine-cellar.api :as api]))

(defn- choose-photo-view
  [image-data show-camera?]
  (let [is-mobile? (and js/navigator.maxTouchPoints
                        (> js/navigator.maxTouchPoints 0))
        trigger-upload #(when-let [el (js/document.getElementById
                                       "recipe-photo-input")]
                          (.click el))]
    [box
     {:sx {:display "flex"
           :flexDirection "column"
           :alignItems "center"
           :gap 2
           :py 2}}
     [:input
      {:type "file"
       :accept "image/*"
       :style {:display "none"}
       :id "recipe-photo-input"
       :data-testid "recipe-photo-input"
       :on-change
       #(when-let [file (-> %
                            .-target
                            .-files
                            (aget 0))]
          (file->jpeg-data-url file (fn [jpeg] (reset! image-data jpeg))))}]
     [typography {:variant "body2" :sx {:color "text.secondary"}}
      "Photograph or upload a recipe page — a cookbook page, recipe card, or screenshot."]
     (when is-mobile?
       [button
        {:variant "contained"
         :start-icon (r/as-element [camera-alt])
         :on-click #(reset! show-camera? true)} "Take Photo"])
     [button
      {:variant (if is-mobile? "outlined" "contained")
       :start-icon (r/as-element [file-upload])
       :on-click trigger-upload} "Upload Image"]]))

(defn- preview-view
  [app-state image-data]
  [box
   {:sx
    {:display "flex" :flexDirection "column" :alignItems "center" :gap 2 :py 1}}
   [box
    {:component "img"
     :src @image-data
     :sx
     {:maxWidth "100%" :maxHeight "50vh" :objectFit "contain" :borderRadius 1}}]
   [box {:sx {:display "flex" :gap 1}}
    [button
     {:variant "contained"
      :data-testid "extract-recipe-button"
      :on-click (fn []
                  (api/extract-recipe-from-image! app-state @image-data)
                  (reset! image-data nil))} "Extract Recipe"]
    [button {:variant "outlined" :on-click #(reset! image-data nil)}
     "Retake"]]])

(defn photo-import-dialog
  "Dialog for importing a cocktail recipe from a photo: choose or take a
   photo, preview it, then extract via AI (opens save-recipe-dialog)."
  [_app-state]
  (let [image-data (r/atom nil)
        show-camera? (r/atom false)]
    (fn [app-state]
      (let [{:keys [open? extracting?]} (get-in @app-state [:bar :photo-import])
            close! (fn []
                     (reset! image-data nil)
                     (reset! show-camera? false)
                     (swap! app-state assoc-in [:bar :photo-import] {}))]
        [:<>
         (when @show-camera?
           [camera-capture
            (fn [captured]
              (reset! image-data (:label_image captured))
              (reset! show-camera? false)) #(reset! show-camera? false)])
         [dialog
          {:open (boolean open?)
           :on-close (fn [] (when-not extracting? (close!)))
           :max-width "sm"
           :full-width true} [dialog-title "Import Recipe from Photo"]
          [dialog-content
           (cond extracting? [box
                              {:sx {:display "flex"
                                    :flexDirection "column"
                                    :alignItems "center"
                                    :gap 2
                                    :py 3}} [circular-progress]
                              [typography {:variant "body2"}
                               "Extracting recipe..."]]
                 @image-data [preview-view app-state image-data]
                 :else [choose-photo-view image-data show-camera?])]
          [dialog-actions
           [button {:on-click close! :disabled (boolean extracting?)}
            "Cancel"]]]]))))
