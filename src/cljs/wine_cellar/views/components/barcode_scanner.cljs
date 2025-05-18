(ns wine-cellar.views.components.barcode-scanner
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.dialog-actions :refer [dialog-actions]]
            [reagent-mui.material.dialog-title :refer [dialog-title]]
            [reagent-mui.material.dialog-content :refer [dialog-content]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.icons.qr-code-scanner :refer [qr-code-scanner]]
            ["quagga" :as Quagga]))

(defn init-scanner
  [callback-fn]
  (Quagga/init
   (clj->js {:inputStream {:name "Live"
                           :type "LiveStream"
                           :target "#scanner-container"
                           :constraints {:width 640 :height 480}}
             :locator {:patchSize "medium" :halfSample false}
             :numOfWorkers 2
             :decoder {:readers ["ean_reader" "upc_reader"]
                       :debug {:drawBoundingBox true
                               :drawScanLine true
                               :showPattern true}}
             :locate true})
   (fn [err]
     (if err
       (js/console.error "Error initializing Quagga:" err)
       (do
         (Quagga/start)
         ;; Use Quagga's built-in drawing functions for visualization
         (Quagga/onProcessed
          (fn [result]
            (when result
              (let [drawing-ctx (.. Quagga -canvas -ctx -overlay)
                    drawing-canvas (.. Quagga -canvas -dom -overlay)]
                (when drawing-ctx
                  ;; Clear the canvas
                  (.clearRect
                   drawing-ctx
                   0
                   0
                   (js/parseInt (.getAttribute drawing-canvas "width"))
                   (js/parseInt (.getAttribute drawing-canvas "height")))
                  ;; Draw all boxes except the main result box
                  (when (.-boxes result)
                    (doseq [box (filter #(not= % (.-box result))
                                        (.-boxes result))]
                      (.. Quagga
                          -ImageDebug
                          (drawPath box
                                    (clj->js {:x 0 :y 1})
                                    drawing-ctx
                                    (clj->js {:color "green" :lineWidth 2})))))
                  ;; Draw the main result box in blue
                  (when (.-box result)
                    (.. Quagga
                        -ImageDebug
                        (drawPath (.-box result)
                                  (clj->js {:x 0 :y 1})
                                  drawing-ctx
                                  (clj->js {:color "#00F" :lineWidth 2}))))
                  ;; Draw the scan line in red if a code was found
                  (when (and (.-codeResult result)
                             (.-code (.-codeResult result)))
                    (.. Quagga
                        -ImageDebug
                        (drawPath (.-line result)
                                  (clj->js {:x "x" :y "y"})
                                  drawing-ctx
                                  (clj->js {:color "red" :lineWidth 3})))))))))
         ;; Handle successful detection
         (Quagga/onDetected (fn [result]
                              (let [code (-> result
                                             .-codeResult
                                             .-code)]
                                (callback-fn code)
                                (Quagga/stop)))))))))

(defn barcode-scanner
  "A reusable barcode scanner component with direct manual entry.
   
   Props:
   - value: The current barcode value
   - on-change: Callback function when barcode value changes (manual entry)
   - on-scan: Callback function when a barcode is successfully scanned
   - label: (optional) Label for the barcode field"
  [{:keys [value on-change on-scan label]}]
  (let [scanner-open (r/atom false)
        scanning-active (r/atom false)]
    (fn [{:keys [value on-change on-scan label]}]
      [box {:sx {:mb 2}}
       ;; Barcode field with scan button
       [text-field
        {:fullWidth true
         :label (or label "Barcode")
         :value (or value "")
         :onChange #(on-change (-> %
                                   .-target
                                   .-value))
         :InputProps {:endAdornment (r/as-element [icon-button
                                                   {:color "primary"
                                                    :onClick
                                                    #(reset! scanner-open true)}
                                                   [qr-code-scanner]])}}]
       ;; Scanner dialog
       [dialog
        {:open @scanner-open
         :fullWidth true
         :maxWidth "sm"
         :onClose #(do (when @scanning-active
                         (Quagga/stop)
                         (reset! scanning-active false))
                       (reset! scanner-open false))
         :TransitionProps {:onEntered (fn []
                                        ;; Auto-start scanner when dialog
                                        ;; opens
                                        (reset! scanning-active true)
                                        (js/setTimeout
                                         #(init-scanner
                                           (fn [code]
                                             (reset! scanning-active false)
                                             (on-scan code)
                                             (reset! scanner-open false)))
                                         100))}} [dialog-title "Scan Barcode"]
        [dialog-content
         ;; Scanner container
         [box {:sx {:mb 2}}
          [box
           {:id "scanner-container"
            :sx {:width "100%"
                 :height "300px"
                 :border "1px solid #ccc"
                 :borderRadius 1
                 :overflow "hidden"
                 :position "relative"
                 :mt 2}}]
          [typography
           {:variant "caption" :sx {:mt 1 :display "block" :textAlign "center"}}
           "Position barcode in view of camera"]]]
        [dialog-actions
         [button
          {:onClick #(do (when @scanning-active
                           (Quagga/stop)
                           (reset! scanning-active false))
                         (reset! scanner-open false))} "Cancel"]]]])))
