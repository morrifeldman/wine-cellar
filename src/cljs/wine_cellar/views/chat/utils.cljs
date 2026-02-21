(ns wine-cellar.views.chat.utils
  (:require [clojure.string :as string]))

(defn escape-regex
  [s]
  (when s (string/replace s (re-pattern "[-/\\\\^$*+?.()|[\\\\]{}]") "\\\\$&")))

(defn calculate-matches
  "Find all occurrences of term in messages. Returns vector of {:message-id :match-idx}."
  [messages term]
  (if (or (empty? messages) (string/blank? term))
    []
    (let [escaped (escape-regex term)
          regex (js/RegExp. escaped "gi")]
      (->> messages
           (mapcat (fn [msg]
                     (let [text (or (:text msg) "")
                           msg-id (:id msg)
                           matches (vec (.matchAll text regex))]
                       (map-indexed (fn [idx _]
                                      {:message-id msg-id :match-idx idx})
                                    matches))))
           vec))))

(defn mobile?
  []
  (boolean (and (exists? js/navigator)
                (pos? (or (.-maxTouchPoints js/navigator) 0)))))

(defn handle-clipboard-image
  "Process an image file/blob from clipboard and set it as attached image"
  [file-or-blob attached-image]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (let [data-url (-> e
                               .-target
                               .-result)
                  img (js/Image.)]
              (set! (.-onload img)
                    (fn []
                      ;; Convert to JPEG format
                      (let [canvas (js/document.createElement "canvas")
                            ctx (.getContext canvas "2d")]
                        (set! (.-width canvas) (.-width img))
                        (set! (.-height canvas) (.-height img))
                        (.drawImage ctx img 0)
                        (let [jpeg-data-url
                              (.toDataURL canvas "image/jpeg" 0.85)]
                          (reset! attached-image jpeg-data-url)))))
              (set! (.-src img) data-url))))
    (.readAsDataURL reader file-or-blob)))

(defn handle-paste-event
  "Handle paste events to detect and process clipboard images"
  [event attached-image]
  (when-let [items (.-items (.-clipboardData event))]
    (dotimes [i (.-length items)]
      (let [item (aget items i)]
        (when (and (.-type item) (.startsWith (.-type item) "image/"))
          (when-let [file (.getAsFile item)]
            (handle-clipboard-image file attached-image)))))))

(defn combine-wine-lists
  "Merge two wine collections, keeping the order of the first and
   removing duplicates by id."
  [primary secondary]
  (let [initial-seen (into #{} (keep :id primary))]
    (first (reduce (fn [[wines seen] wine]
                     (let [wine-id (:id wine)]
                       (if (and wine-id (contains? seen wine-id))
                         [wines seen]
                         [(conj wines wine)
                          (if wine-id (conj seen wine-id) seen)])))
                   [(vec primary) initial-seen]
                   secondary))))

(defn api-message->ui
  [message]
  (when message
    {:id (:id message)
     :text (:content message)
     :is-user (:is_user message)
     :timestamp (some-> (:created_at message)
                        js/Date.parse
                        js/Date.)}))

(defn find-message-index
  [messages message-id]
  (->> messages
       (keep-indexed (fn [idx message] (when (= (:id message) message-id) idx)))
       first))

;; Scroll intent management
;; Intent types: :bottom, :search-match, :position, nil

(defn set-scroll-intent!
  "Set a scroll intent in app-state. Intent is a map like:
   {:type :bottom}
   {:type :search-match}
   {:type :position :top 123}"
  [app-state intent]
  (swap! app-state assoc-in [:chat :scroll-intent] intent))

(defn clear-scroll-intent!
  [app-state]
  (swap! app-state assoc-in [:chat :scroll-intent] nil))

(defn scroll-to-bottom!
  [container]
  (when container (set! (.-scrollTop container) (.-scrollHeight container))))

(defn scroll-to-element!
  [element-id container]
  (when-let [el (.getElementById js/document element-id)]
    (when container
      (let [el-rect (.getBoundingClientRect el)
            container-rect (.getBoundingClientRect container)
            relative-top (- (.-top el-rect) (.-top container-rect))
            current-scroll (.-scrollTop container)
            center-offset (- (/ (.-height container-rect) 2)
                             (/ (.-height el-rect) 2))
            target-scroll (- (+ current-scroll relative-top) center-offset)]
        (.scrollTo container #js {:top target-scroll :behavior "smooth"})))))

(defn scroll-to-element-top!
  [element-id container]
  (when-let [el (.getElementById js/document element-id)]
    (when container
      (let [el-rect (.getBoundingClientRect el)
            container-rect (.getBoundingClientRect container)
            relative-top (- (.-top el-rect) (.-top container-rect))
            target-scroll (- (+ (.-scrollTop container) relative-top) 8)]
        (.scrollTo container #js {:top target-scroll :behavior "smooth"})))))

(defn scroll-to-position!
  [container top]
  (when (and container top)
    (.scrollTo container #js {:top top :behavior "auto"})))

(defn execute-scroll-intent!
  "Execute the current scroll intent and clear it. Returns true if scroll was executed."
  [app-state container]
  (let [intent (get-in @app-state [:chat :scroll-intent])]
    (when intent
      (case (:type intent)
        :bottom (scroll-to-bottom! container)
        :ai-top (scroll-to-element-top! "latest-ai-response" container)
        :search-match (scroll-to-element! "active-search-match" container)
        :position (scroll-to-position! container (:top intent))
        nil)
      (clear-scroll-intent! app-state)
      true)))
