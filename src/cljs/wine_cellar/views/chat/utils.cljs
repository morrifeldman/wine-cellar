(ns wine-cellar.views.chat.utils
  (:require [clojure.string :as string]))

(defn escape-regex
  [s]
  (when s
    (string/replace s
                    (re-pattern "[-/\\\\^$*+?.()|[\\\\]{}]")
                    "\\\\$REPLACE_PLACEHOLDER")))

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
