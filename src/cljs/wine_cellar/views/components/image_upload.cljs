(ns wine-cellar.views.components.image-upload
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.icons.camera-alt :refer [camera-alt]]
            [reagent-mui.icons.close :refer [close]]))

(defn create-thumbnail
  [data-url callback]
  (let [img (js/Image.)]
    (set! (.-onload img)
          (fn []
            (let [canvas (js/document.createElement "canvas")
                  ctx (.getContext canvas "2d")
                  max-thumb-size 100
                  width (.-width img)
                  height (.-height img)
                  scale (min (/ max-thumb-size width) (/ max-thumb-size height))
                  new-width (js/Math.floor (* width scale))
                  new-height (js/Math.floor (* height scale))]
              (set! (.-width canvas) new-width)
              (set! (.-height canvas) new-height)
              (.drawImage ctx img 0 0 new-width new-height)
              (callback (.toDataURL canvas "image/jpeg" 0.7)))))
    (set! (.-src img) data-url)))

;; Camera capture component
(defn camera-capture
  [on-capture on-cancel]
  (let [video-ref (r/atom nil)
        stream-ref (r/atom nil)
        camera-active (r/atom false)
        capture-handler on-capture
        cancel-handler on-cancel]
    (r/create-class
     {:component-did-mount
      (fn []
        (-> (js/navigator.mediaDevices.getUserMedia
             #js {:video #js {:facingMode "environment"
                              :focusMode "continuous"
                              :exposureMode "continuous"
                              :whiteBalanceMode "continuous"
                              :width #js {:ideal 1920}
                              :height #js {:ideal 1080}}})
            (.then (fn [stream]
                     (reset! stream-ref stream)
                     (reset! camera-active true)
                     (when-let [video @video-ref]
                       (set! (.-srcObject video) stream)
                       (.play video)
                       ;; Try to apply manual focus for better depth of
                       ;; field
                       (let [track (first (.getVideoTracks stream))
                             capabilities (.getCapabilities track)]
                         (when (aget capabilities "focusDistance")
                           (.applyConstraints
                            track
                            #js {:advanced #js [#js {:focusDistance 0.1}]}))))))
            (.catch (fn [err]
                      (js/console.error "Error accessing camera:" err)))))
      :component-will-unmount (fn []
                                (when-let [stream @stream-ref]
                                  (doseq [track (.getTracks stream)]
                                    (.stop track))))
      :reagent-render
      (fn [] [box
              {:sx {:position "fixed"
                    :top 0
                    :left 0
                    :right 0
                    :bottom 0
                    :bgcolor "rgba(0,0,0,0.9)"
                    :zIndex 9999
                    :display "flex"
                    :flexDirection "column"
                    :alignItems "center"
                    :justifyContent "center"}}
              [icon-button
               {:onClick cancel-handler
                :sx {:position "absolute" :top 16 :right 16 :color "white"}}
               [close]]
              [box
               {:sx {:position "relative"
                     :maxWidth "100%"
                     :maxHeight "70vh"
                     :overflow "hidden"
                     :borderRadius 2
                     :mb 2
                     :border "1px solid rgba(255,255,255,0.3)"}}
               [:video
                {:ref #(reset! video-ref %)
                 :autoPlay true
                 :playsInline true
                 :muted true
                 :style
                 {:maxWidth "100%" :maxHeight "70vh" :background "black"}}]]
              [button
               {:variant "contained"
                :color "primary"
                :disabled (not @camera-active)
                :onClick
                (fn []
                  (when-let [video @video-ref]
                    (let [canvas (js/document.createElement "canvas")
                          ctx (.getContext canvas "2d")]
                      (set! (.-width canvas) (.-videoWidth video))
                      (set! (.-height canvas) (.-videoHeight video))
                      (.drawImage ctx video 0 0)
                      (let [data-url (.toDataURL canvas "image/jpeg" 0.85)]
                        (create-thumbnail data-url
                                          (fn [thumbnail]
                                            (capture-handler
                                             {:label_image data-url
                                              :label_thumbnail thumbnail})))))))
                :sx {:mb 2}} "Take Photo"]])})))

;; Image preview component
(defn render-image-preview
  [image-data]
  [box
   {:sx {:position "relative"
         :width "100%"
         :display "flex"
         :flexDirection "column"
         :alignItems "center"}}
   [box
    {:component "img"
     :src image-data
     :sx {:maxWidth "100%"
          :maxHeight "300px"
          :objectFit "contain"
          :borderRadius 1}}]])

;; Camera controls component - we've moved this functionality directly into the
;; image-upload component
;; to handle the label type parameter

;; Main image upload component
(defn image-upload
  "Component for uploading and displaying wine label images
   Options:
   - image-data: Current image data (base64 string) or nil
   - on-image-change: Function to call when image changes (receives image data object)
   - on-image-remove: Function to call when image is removed
   - disabled: Whether the upload controls should be disabled
   - label-type: Type of label ('front' or 'back')"
  [_props]
  (let [show-camera (r/atom false)
        uploading (r/atom false)]
    (fn [{:keys [image-data on-image-change disabled label-type]}]
      (let [label-text (if (= label-type "back") "back" "front")]
        [box {:sx {:width "100%"}}
         ;; Show camera modal if active
         (when @show-camera
           [camera-capture
            (fn [image-data]
              (reset! show-camera false)
              (if (= label-type "back")
                ;; For back label, we don't need a thumbnail
                (on-image-change {:back_label_image (:label_image image-data)})
                ;; For front label, we keep the existing behavior
                (on-image-change image-data))) #(reset! show-camera false)])
         ;; Image preview or camera controls
         (if image-data
           ;; Show image preview
           [render-image-preview image-data]
           ;; Show camera controls
           [box
            {:sx {:display "flex"
                  :flexDirection "column"
                  :alignItems "center"
                  :p 2
                  :border "1px dashed rgba(0,0,0,0.2)"
                  :borderRadius 1}}
            [typography
             {:variant "body2"
              :color "text.secondary"
              :sx {:mb 2 :textAlign "center"}}
             (str "Add " label-text " label image")]
            ;; Camera button
            [button
             {:variant "contained"
              :color "primary"
              :disabled (or disabled @uploading)
              :onClick #(reset! show-camera true)
              :startIcon (r/as-element [camera-alt])
              :size "medium"}
             (str "Take " (str/capitalize label-text) " Photo")]
            ;; Loading indicator
            (when @uploading
              [box {:sx {:mt 2 :display "flex" :justifyContent "center"}}
               [typography {:variant "body2" :color "text.secondary"}
                "Processing image..."]])])]))))
