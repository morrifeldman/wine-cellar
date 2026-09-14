(ns wine-cellar.dom
  "The handful of DOM ids the app addresses by hand, and the scrolling that
   needs them. They live together so the view that renders an id and the code
   that looks it up can't drift apart.")

(def notes-field-id "tasting-note-notes")

(defn notes-field-text
  "What is currently typed in the open tasting note's textarea, or nil when no
   form is on screen. The field is uncontrolled — its text doesn't reach
   app-state until submit — so asking whether the form holds unsaved work means
   reading the DOM."
  []
  (some-> (.getElementById js/document notes-field-id)
          (.-value)))

(defn recipe-id [id] (str "recipe-" id))

(defn spirit-id [id] (str "spirit-" id))

(defn bar-item-id [id] (str "bar-inventory-item-" id))

(defn- scroll-to-top-of!
  "Smooth-scrolls the window so `dom-id` sits near the top. Delayed a tick so
   the element exists after a state change re-renders."
  [dom-id]
  (js/setTimeout (fn []
                   (when-let [el (.getElementById js/document dom-id)]
                     (let [top (-> (.. el getBoundingClientRect -top)
                                   (+ (.-pageYOffset js/window))
                                   (- 16))]
                       (.scrollTo js/window
                                  #js {:top top :behavior "smooth"}))))
                 100))

(defn scroll-recipe-into-view! [id] (scroll-to-top-of! (recipe-id id)))

(defn scroll-spirit-into-view! [id] (scroll-to-top-of! (spirit-id id)))

(defn scroll-bar-item-into-view!
  "Centers a Mixers item rather than putting it at the top: the items are small
   chips in a long grid, and one pinned to the top edge reads as cropped."
  [id]
  (js/setTimeout
   (fn []
     (when-let [el (.getElementById js/document (bar-item-id id))]
       (.scrollIntoView el #js {:behavior "smooth" :block "center"})))
   100))
