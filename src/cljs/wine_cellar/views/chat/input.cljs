(ns wine-cellar.views.chat.input
  (:require [reagent.core :as r]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.menu :refer [menu]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.icons.send :refer [send]]
            [reagent-mui.icons.camera-alt :refer [camera-alt]]
            [reagent-mui.icons.photo-library :refer [photo-library]]
            [reagent-mui.icons.bookmarks :refer [bookmarks]]
            [wine-cellar.views.chat.utils :refer
             [handle-clipboard-image handle-paste-event]]
            [wine-cellar.views.chat.message :refer [attached-image-preview]]))

(def ^:private preset-deal-sites
  [{:label "Last Bottle"
    :url "https://lastbottlewines.com/products.json?limit=1"
    :message
    "What do you think of today's Last Bottle offer for my cellar? https://lastbottlewines.com/products.json?limit=1"}
   {:label "Last Bubbles"
    :url "https://lastbubbles.com/products.json?limit=1"
    :message
    "What do you think of today's Last Bubbles offer for my cellar? https://lastbubbles.com/products.json?limit=1"}
   {:label "First Bottle"
    :url "https://firstbottlewines.com/products.json"
    :message
    "What do you think of today's First Bottle offers for my cellar? https://firstbottlewines.com/products.json"}])

(defn use-edit-state
  [app-state messages]
  (let [editing-message-id (r/atom nil)
        original-messages (r/atom nil)]
    {:editing-message-id editing-message-id
     :handle-edit
     (fn [message-id message-text message-ref]
       (reset! editing-message-id message-id)
       (let [text (or message-text "")
             current @messages
             message-idx (->> current
                              (keep-indexed (fn [idx message]
                                              (when (= (:id message) message-id)
                                                idx)))
                              first)
             truncated (if (some? message-idx)
                         (conj (vec (take message-idx current))
                               (assoc (nth current message-idx) :text text))
                         current)]
         (reset! original-messages current)
         (swap! app-state assoc-in [:chat :draft-message] text)
         (when (and (some? message-idx) (< (inc message-idx) (count current)))
           (reset! messages truncated)
           (swap! app-state assoc-in [:chat :messages] truncated))
         (when @message-ref
           (set! (.-value @message-ref) text)
           (let [input-event (js/Event. "input" #js {:bubbles true})]
             (.dispatchEvent @message-ref input-event))
           (.focus @message-ref))))
     :handle-cancel (fn [message-ref]
                      (when-let [original @original-messages]
                        (reset! messages original)
                        (swap! app-state assoc-in [:chat :messages] original))
                      (reset! original-messages nil)
                      (reset! editing-message-id nil)
                      (swap! app-state update :chat dissoc :draft-message)
                      (when @message-ref (set! (.-value @message-ref) "")))
     :handle-commit #(reset! original-messages nil)
     :is-editing? #(some? @editing-message-id)}))

(defn- chat-input-actions
  [disabled? on-send message-ref app-state on-image-capture on-cancel-request
   attached-image]
  (let [presets-anchor (r/atom nil)]
    (fn [disabled? on-send message-ref app-state on-image-capture
         on-cancel-request attached-image]
      [box
       {:sx {:display "flex"
             :justify-content "flex-end"
             :align-items "center"
             :gap 1
             :flex-wrap "wrap"}}
       (let [is-mobile? (and js/navigator.maxTouchPoints
                             (> js/navigator.maxTouchPoints 0))
             trigger-upload #(when-let [input (js/document.getElementById
                                               "photo-picker-input")]
                               (.click input))]
         [:<>
          [:input
           {:type "file"
            :accept "image/*"
            :style {:display "none"}
            :id "photo-picker-input"
            :on-change #(when-let [file (-> %
                                            .-target
                                            .-files
                                            (aget 0))]
                          (handle-clipboard-image file attached-image))}]
          [button
           {:variant "outlined"
            :size "small"
            :disabled @disabled?
            :startIcon (r/as-element [bookmarks {:size 14}])
            :on-click #(reset! presets-anchor (.-currentTarget %))
            :sx {:minWidth "80px"}} "Deals"]
          [menu
           {:anchor-el @presets-anchor
            :open (some? @presets-anchor)
            :on-close #(reset! presets-anchor nil)}
           (for [{:keys [label message]} preset-deal-sites]
             [menu-item
              {:key label
               :on-click
               (fn []
                 (reset! presets-anchor nil)
                 (when @message-ref
                   (set! (.-value @message-ref) message)
                   (swap! app-state assoc-in [:chat :draft-message] message)
                   (let [ev (js/Event. "input" #js {:bubbles true})]
                     (.dispatchEvent @message-ref ev))
                   (.focus @message-ref)))} label])]
          (when is-mobile?
            [button
             {:variant "outlined"
              :size "small"
              :disabled @disabled?
              :startIcon (r/as-element [camera-alt {:size 14}])
              :on-click #(on-image-capture nil)
              :sx {:minWidth "90px"}} "Camera"])
          [button
           {:variant "outlined"
            :size "small"
            :disabled @disabled?
            :startIcon (r/as-element [photo-library {:size 14}])
            :on-click trigger-upload
            :sx {:minWidth (if is-mobile? "90px" "100px")}}
           (if is-mobile? "Photos" "Upload")]
          (when @disabled?
            [button
             {:variant "outlined"
              :color "error"
              :size "small"
              :on-click on-cancel-request
              :sx {:minWidth "60px"}} "Stop"])
          [button
           {:variant "contained"
            :disabled @disabled?
            :sx {:minWidth "60px" :px 1}
            :startIcon (if @disabled?
                         (r/as-element [circular-progress
                                        {:size 14
                                         :sx {:color "secondary.light"}}])
                         (r/as-element [send {:size 14}]))
            :on-click
            #(when @message-ref
               (let [message-text (.-value @message-ref)]
                 (when (or (seq (str message-text)) @attached-image)
                   (on-send message-text)
                   (set! (.-value @message-ref) "")
                   (swap! app-state update :chat dissoc :draft-message))))}
           [box
            {:sx {:color (if @disabled? "text.disabled" "inherit")
                  :fontWeight (if @disabled? "600" "normal")
                  :fontSize (if @disabled? "0.8rem" "0.9rem")}}
            (if @disabled? "Sending..." "Send")]]])])))

(defn chat-input
  "Chat input field with send button and camera button - uncontrolled for performance"
  [message-ref on-send disabled? reset-key app-state on-image-capture
   attached-image on-image-remove on-cancel-request]
  (let [has-image? @attached-image
        container-ref (r/atom nil)]
    (when has-image?
      (js/setTimeout #(when @container-ref
                        (.scrollIntoView @container-ref
                                         #js {:behavior "smooth"
                                              :block "nearest"}))
                     100))
    [box
     {:key reset-key
      :sx {:display "flex" :flex-direction "column" :gap 1 :mt 2}
      :ref #(reset! container-ref %)}
     (when has-image? [attached-image-preview has-image? on-image-remove])
     [mui-text-field/text-field
      {:multiline true
       :rows 5
       :fullWidth true
       :variant "outlined"
       :size "small"
       :margin "dense"
       :inputRef #(when %
                    (reset! message-ref %)
                    (when-let [draft (get-in @app-state [:chat :draft-message])]
                      (set! (.-value %) draft)))
       :sx {"& .MuiOutlinedInput-root" {:backgroundColor "background.default"
                                        :border "none"
                                        :borderRadius 2}}
       :placeholder (if has-image?
                      "Add a message to go with your image..."
                      (let [is-mobile? (and js/navigator.maxTouchPoints
                                            (> js/navigator.maxTouchPoints 0))]
                        (if is-mobile?
                          "Type your message here..."
                          "Type your message here... (or paste a screenshot)")))
       :disabled @disabled?
       :on-change
       #(swap! app-state assoc-in [:chat :draft-message] (.. % -target -value))
       :on-paste #(handle-paste-event % attached-image)}]
     [chat-input-actions disabled? on-send message-ref app-state
      on-image-capture on-cancel-request attached-image]]))
