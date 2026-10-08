(ns wine-cellar.views.wines.detail
  (:require
    [clojure.string :as str]
    [reagent-mui.icons.add :refer [add]]
    [reagent-mui.icons.arrow-back :refer [arrow-back]]
    [reagent-mui.icons.auto-awesome :refer [auto-awesome]]
    [reagent-mui.icons.delete :refer [delete]]
    [reagent-mui.icons.share :refer [share]]
    [reagent-mui.icons.close :refer [close]]
    [reagent-mui.icons.public :refer [public] :rename {public globe}]
    [reagent-mui.icons.wine-bar :refer [wine-bar]]
    [reagent-mui.icons.science :refer [science]]
    [reagent-mui.icons.rate-review :refer [rate-review]]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.button :refer [button]]
    [reagent-mui.material.circular-progress :refer [circular-progress]]
    [reagent-mui.material.grid :refer [grid]]
    [reagent-mui.material.paper :refer [paper]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.tooltip :refer [tooltip]]
    [reagent-mui.material.modal :refer [modal]]
    [reagent-mui.material.backdrop :refer [backdrop]]
    [reagent-mui.material.divider :refer [divider]]
    [reagent-mui.material.dialog :refer [dialog]]
    [reagent-mui.material.dialog-content :refer [dialog-content]]
    [reagent.core :as r]
    [wine-cellar.views.wines.detail.fields :refer
     [editable-alcohol-percentage editable-dosage editable-disgorgement-year
      editable-ai-summary editable-designation editable-country editable-region
      editable-appellation editable-appellation-tier editable-vineyard
      editable-classification editable-styles editable-closure-type
      editable-bottle-format wine-identity-section]]
    [wine-cellar.views.wines.detail.cellar :refer
     [wine-cellar-section wine-provenance-section]]
    [wine-cellar.views.wines.detail.drinking-window :refer
     [wine-tasting-window-section]]
    [wine-cellar.views.wines.detail.history :refer
     [open-bottle-section inventory-history-section]]
    [wine-cellar.api :as api]
    [wine-cellar.views.components.confirm :refer [confirm!]]
    [wine-cellar.nav :as nav]
    [wine-cellar.views.components :refer [detail-section dot-separated-row]]
    [wine-cellar.views.components.image-upload :refer [image-upload]]
    [wine-cellar.views.tasting-notes.form :refer [tasting-note-form]]
    [wine-cellar.views.wines.varieties :refer [wine-varieties-list]]
    [wine-cellar.views.tasting-notes.list :refer [tasting-notes-list]]
    [wine-cellar.views.components.ai-provider-toggle :refer [ai-button]]
    [wine-cellar.views.components.technical-data :refer
     [technical-data-editor]]))

(defn image-zoom-modal
  [image-data image-title on-remove]
  [modal {:open true :onClose #(nav/back!) :closeAfterTransition true}
   [backdrop {:sx {:color "white"} :open true}
    [box
     {:sx {:position "absolute"
           :top "50%"
           :left "50%"
           :transform "translate(-50%, -50%)"
           :width "90vw"
           :height "90vh"
           :bgcolor "background.paper"
           :borderRadius 2
           :boxShadow 24
           :p 2
           :display "flex"
           :flexDirection "column"
           :outline "none"}}
     ;; Header with title and close button
     [box
      {:sx {:display "flex"
            :justifyContent "space-between"
            :alignItems "center"
            :mb 2}} [typography {:variant "h6"} image-title]
      [box {:sx {:display "flex" :alignItems "center" :gap 1}}
       (when on-remove
         [button
          {:size "small"
           :color "error"
           :variant "text"
           :onClick (fn [] (on-remove) (nav/back!))} "Remove"])
       [button {:onClick #(nav/back!) :sx {:minWidth "auto" :p 1}} [close]]]]
     ;; Image container
     [box
      {:sx {:flex 1
            :display "flex"
            :justifyContent "center"
            :alignItems "center"
            :overflow "auto"}}
      [box
       {:component "img"
        :src image-data
        :sx {:maxWidth "100%"
             :maxHeight "100%"
             :objectFit "contain"
             :borderRadius 1}}]]]]])

(defn clickable-wine-image
  [label-type {:keys [data on-change on-remove]}]
  [box {:sx {:position "relative"}}
   [image-upload
    {:image-data data
     :label-type label-type
     :on-image-change on-change
     :on-image-remove on-remove}]
   ;; Click overlay for zoom (only when image exists)
   (when data
     [box
      {:sx {:position "absolute"
            :top 0
            :left 0
            :right 0
            :height "300px"
            :cursor "zoom-in"
            :display "flex"
            :alignItems "center"
            :justifyContent "center"
            :bgcolor "rgba(0,0,0,0)"
            :transition "background-color 0.2s"
            :pointerEvents "auto"
            ":hover" {:bgcolor "rgba(0,0,0,0.1)"}}
       :onClick #(do (.stopPropagation %) (nav/open-modal! :zoom label-type))}
      [box
       {:sx {:opacity 0
             :transition "opacity 0.2s"
             :bgcolor "rgba(0,0,0,0.7)"
             :color "white"
             :px 2
             :py 1
             :borderRadius 1
             :fontSize "0.875rem"
             :pointerEvents "none"
             ":hover" {:opacity 1}}} "Click to zoom"]])])

(defn- wine-label-images
  "The zoom modal is reopened from a URL that names a label, so each label's
   image, title and remove action have to be derivable from that name alone."
  [app-state wine]
  {"front" {:data (:label_image wine)
            :title "Front Wine Label"
            :on-change #(api/update-wine-image app-state (:id wine) %)
            :on-remove #(api/update-wine-image
                         app-state
                         (:id wine)
                         (assoc wine :label_image nil :label_thumbnail nil))}
   "back" {:data (:back_label_image wine)
           :title "Back Wine Label"
           :on-change #(api/update-wine-image app-state (:id wine) %)
           :on-remove #(api/update-wine-image
                        app-state
                        (:id wine)
                        (assoc wine :back_label_image nil))}})

(defn wine-images-section
  [app-state wine]
  (let [labels (wine-label-images app-state wine)
        zoomed (get labels (:zoomed-image @app-state))]
    [:<>
     (when (:data zoomed)
       [image-zoom-modal (:data zoomed) (:title zoomed) (:on-remove zoomed)])
     [grid {:item true :xs 6} [clickable-wine-image "front" (labels "front")]]
     [grid {:item true :xs 6} [clickable-wine-image "back" (labels "back")]]]))

(defn wine-terroir-section
  [app-state wine]
  [detail-section {:icon globe :label "Terroir"}
   [dot-separated-row [editable-country app-state wine]
    [editable-region app-state wine] [editable-appellation app-state wine]
    [editable-appellation-tier app-state wine]]
   [dot-separated-row [editable-classification app-state wine]
    [editable-designation app-state wine] [editable-vineyard app-state wine]]])

(def ^:private sparkling-styles #{"Sparkling" "Red Sparkling" "Rose Sparkling"})

(defn wine-composition-section
  [app-state wine]
  [detail-section {:icon wine-bar :label "Composition"}
   [dot-separated-row [editable-styles app-state wine]
    [editable-alcohol-percentage app-state wine]
    [editable-bottle-format app-state wine]
    [editable-closure-type app-state wine]]
   (when (contains? sparkling-styles (:style wine))
     [box {:sx {:display "flex" :alignItems "baseline" :gap 0.5 :mt 1.5}}
      [dot-separated-row [editable-disgorgement-year app-state wine]
       [editable-dosage app-state wine]]
      [box {:component "span" :sx {:fontSize "1rem" :lineHeight 1}} "🫧"]])
   [divider {:sx {:my 1.5}}]
   [box {:sx {:mt 1}} [wine-varieties-list app-state (:id wine)]]])

(defn wine-ai-summary-section
  [app-state wine]
  (let [generating? (:generating-ai-summary? @app-state)]
    [detail-section {:icon auto-awesome :label "Summary"}
     [box {:sx {:display "flex" :flexDirection "column" :gap 1}}
      [editable-ai-summary app-state wine]
      [box
       {:sx
        {:mt 1 :display "flex" :alignItems "center" :flexWrap "wrap" :gap 1}}
       [ai-button app-state
        {:label "Generate AI Summary"
         :busy-label "Generating..."
         :busy? generating?
         :on-click (fn []
                     (swap! app-state assoc :generating-ai-summary? true)
                     (-> (api/generate-wine-summary app-state wine)
                         (.then (fn [summary]
                                  (swap! app-state update
                                    :wines
                                    (fn [wines]
                                      (map #(if (= (:id %) (:id wine))
                                              (assoc % :ai_summary summary)
                                              %)
                                           wines)))
                                  (swap! app-state assoc-in
                                    [:force-edit-ai-summary (:id wine)]
                                    true)
                                  (swap! app-state dissoc
                                    :generating-ai-summary?)))
                         (.catch (fn [error]
                                   (swap! app-state assoc
                                     :error
                                     (str "Failed to generate summary: " error))
                                   (swap! app-state dissoc
                                     :generating-ai-summary?)))))}]]]]))

(defn wine-technical-notes-section
  [app-state wine]
  [detail-section {:icon science :label "Technical Notes"}
   [technical-data-editor
    {:metadata (or (:metadata wine) {})
     :on-change
     (fn [new-metadata]
       (api/update-wine app-state (:id wine) {:metadata new-metadata}))}]])

(defn wine-tasting-notes-section
  [app-state wine]
  (let [on-close #(nav/back!)]
    [detail-section {:icon rate-review :label "Tasting Notes"}
     [tasting-notes-list app-state (:id wine)]
     [tooltip {:title "Add tasting note" :placement "right" :arrow true}
      [button
       {:size "small"
        :sx {:mt 1 :color "text.secondary" :minWidth 0 :p 0.5}
        :on-click #(nav/open-modal! :note "new")} [add {:fontSize "small"}]]]
     [dialog
      {:open (or (:show-tasting-note-form? @app-state)
                 (boolean (:editing-note-id @app-state)))
       :onClose on-close
       :maxWidth "md"
       :fullWidth true}
      [dialog-content {:sx {:p 0}}
       [tasting-note-form app-state (:id wine) on-close]]]]))

(defn wine-detail
  [app-state wine]
  [paper
   {:elevation 2
    :sx
    {:p 4
     :mb 3
     :borderRadius 2
     :position "relative"
     :overflow "hidden"
     :bgcolor "container.main"
     :backgroundImage
     "linear-gradient(to right, rgba(114,47,55,0.03), rgba(255,255,255,0))"}}
   [grid {:container true :spacing 2 :sx {:mb 1}}
    [wine-images-section app-state wine]] [wine-identity-section app-state wine]
   [wine-terroir-section app-state wine]
   [wine-composition-section app-state wine]
   [wine-cellar-section app-state wine] [wine-provenance-section app-state wine]
   [wine-tasting-window-section app-state wine]
   [wine-ai-summary-section app-state wine]
   [wine-technical-notes-section app-state wine]
   [open-bottle-section app-state wine]
   [inventory-history-section app-state wine]
   [wine-tasting-notes-section app-state wine]])

(defn wine-loading-view
  "Show loading state while fetching wines"
  []
  [box
   {:sx {:display "flex"
         :justifyContent "center"
         :alignItems "center"
         :minHeight "400px"}} [circular-progress]
   [typography {:sx {:ml 2}} "Loading wine details..."]])

(defn delete-button-click-handler
  "Handle delete wine button click"
  [app-state selected-wine-id selected-wine]
  (fn []
    (confirm! app-state
              {:title (str "Delete "
                           (str/join " "
                                     (remove nil?
                                             [(:producer selected-wine)
                                              (:name selected-wine)
                                              (:vintage selected-wine)]))
                           "?")
               :message "This wine and its history will be gone for good."
               :confirm-label "Delete"
               :danger? true
               ;; A failed delete is already on the banner; stay on the
               ;; wine.
               :on-confirm #(.then (api/delete-wine app-state selected-wine-id)
                                   (fn [_] (nav/replace-wines!))
                                   (fn [_]))})))

(defn share-wine-url
  "Build a shareable URL for the given wine id"
  [selected-wine-id]
  (str (.. js/window -location -origin) "/wine/" selected-wine-id))

(defn- wine-share-title
  [wine]
  (str/trim (str (or (:producer wine) "")
                 (when (and (:producer wine) (:name wine)) " ")
                 (or (:name wine) "")
                 (when (:vintage wine) (str " " (:vintage wine))))))

(defn- copy-to-clipboard
  [url on-done]
  (-> (.writeText (.-clipboard js/navigator) url)
      (.then #(on-done :copied))
      (.catch #(on-done :error))))

(defn share-button
  "Share button with native Web Share API and clipboard fallback.
   Shows 'Copied!' on the button briefly after a successful copy."
  [_ _]
  (let [status (r/atom :idle)]
    (fn [selected-wine-id selected-wine]
      (let [url (share-wine-url selected-wine-id)
            flash! (fn [s]
                     (reset! status s)
                     (js/setTimeout #(reset! status :idle) 2000))
            on-click
            (fn []
              (let [nav js/navigator
                    title (wine-share-title selected-wine)
                    data (clj->js {:title title :text title :url url})]
                (if (and (.-share nav) (.canShare nav data))
                  (-> (.share nav data)
                      (.catch (fn [_]
                                (copy-to-clipboard
                                 url
                                 #(flash! (if (= % :copied) :copied :error))))))
                  (copy-to-clipboard url
                                     #(flash!
                                       (if (= % :copied) :copied :error))))))
            label (case @status
                    :copied "Copied!"
                    :error "Copy failed"
                    "Share")]
        [button
         {:variant "outlined"
          :color "primary"
          :start-icon (r/as-element [share])
          :onClick on-click} label]))))

(defn wine-action-buttons
  "Render the back, share and delete buttons for wine details"
  [app-state selected-wine-id selected-wine]
  [box {:sx {:mt 2 :display "flex" :gap 2 :justifyContent "space-between"}}
   [button
    {:variant "contained"
     :color "primary"
     :start-icon (r/as-element [arrow-back])
     :onClick #(nav/back!)} "Back to List"]
   [box {:sx {:display "flex" :gap 2}}
    [share-button selected-wine-id selected-wine]
    [button
     {:variant "outlined"
      :color "error"
      :start-icon (r/as-element [delete])
      :onClick
      (delete-button-click-handler app-state selected-wine-id selected-wine)}
     "Delete Wine"]]])

(defn wine-details-content
  "Render the wine details with action buttons"
  [app-state selected-wine-id selected-wine]
  [box {:sx {:mb 3}} [wine-detail app-state selected-wine]
   [wine-action-buttons app-state selected-wine-id selected-wine]])

(defn wine-details-section
  [app-state]
  (when-let [selected-wine-id (:selected-wine-id @app-state)]
    ;; If wines collection is empty, fetch all wines first
    (when (and (empty? (:wines @app-state)) (not (:loading? @app-state)))
      (api/fetch-wines app-state))
    (if (:loading? @app-state)
      [wine-loading-view]
      ;; Show wine details once loaded
      (when-let [selected-wine (first (filter #(= (:id %) selected-wine-id)
                                              (:wines @app-state)))]
        [wine-details-content app-state selected-wine-id selected-wine]))))
