(ns wine-cellar.views.bar.recipe-mode
  "Full-screen 'recipe mode' for mixing a cocktail: one recipe, large type,
   and a screen wake lock so the display stays on while your hands are busy."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.dialog :refer [dialog]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.icons.close :refer [close]]))

(defonce ^:private wake-lock-sentinel (atom nil))
(defonce ^:private audio-context (atom nil))
(defonce ^:private mode-active? (atom false))

(defn- request-wake-lock!
  []
  (when-let [wl (.-wakeLock js/navigator)]
    (-> (.request wl "screen")
        (.then (fn [sentinel] (reset! wake-lock-sentinel sentinel)))
        (.catch (fn [err]
                  (js/console.warn "Screen wake lock unavailable" err))))))

(defn- release-wake-lock!
  []
  (when-let [sentinel @wake-lock-sentinel]
    (reset! wake-lock-sentinel nil)
    (-> (.release sentinel)
        (.catch (fn [_] nil)))))

(defn- handle-visibility-change
  "The browser drops the wake lock whenever the tab is hidden; take it back
   when the user returns while recipe mode is still open."
  []
  (when (and @mode-active? (= "visible" (.-visibilityState js/document)))
    (request-wake-lock!)))

(defn- unlock-audio!
  "Browsers only let a page play sound once the user has tapped something, so
   the audio context is created and resumed on the tap that starts a timer,
   ready for the beep when it ends."
  []
  (when-let [AudioContext (or (.-AudioContext js/window)
                              (.-webkitAudioContext js/window))]
    (when-not @audio-context (reset! audio-context (AudioContext.)))
    (.resume @audio-context)))

(defn- beep!
  []
  (when-let [ctx @audio-context]
    (doseq [offset [0 0.3 0.6]]
      (let [start (+ (.-currentTime ctx) offset)
            osc (.createOscillator ctx)
            gain (.createGain ctx)]
        (set! (.. osc -frequency -value) 880)
        (.setValueAtTime (.-gain gain) 0.3 start)
        (.setValueAtTime (.-gain gain) 0 (+ start 0.18))
        (.connect osc gain)
        (.connect gain (.-destination ctx))
        (.start osc start)
        (.stop osc (+ start 0.2))))))

(defn- finish-timer!
  []
  (beep!)
  ;; iPhones can't vibrate from a web page; the beep and flash cover them
  (when (.-vibrate js/navigator) (.vibrate js/navigator #js [300 150 300])))

(defn- step-timer
  "Tap to start, tap again to cancel. Counts against an end time rather than
   ticks, because a phone slows timers down when it is busy."
  [{:keys [action seconds]}]
  (r/with-let
   [state (r/atom {:status :idle}) interval (atom nil) stop!
    (fn []
      (some-> @interval
              js/clearInterval)
      (reset! interval nil)) tick!
    (fn []
      (let [remaining (- (:ends-at @state) (js/Date.now))]
        (if (pos? remaining)
          (swap! state assoc :remaining-ms remaining)
          (do (stop!) (reset! state {:status :done}) (finish-timer!))))) start!
    (fn []
      (unlock-audio!)
      (reset! state {:status :running
                     :ends-at (+ (js/Date.now) (* 1000 seconds))
                     :remaining-ms (* 1000 seconds)})
      (reset! interval (js/setInterval tick! 100)))]
   (let [{:keys [status remaining-ms]} @state
         elapsed-pct
         (if (= status :running) (- 100 (/ remaining-ms seconds 10)) 0)]
     [box
      {:component "button"
       :data-testid "step-timer"
       :on-click #(case status
                    :idle (start!)
                    (do (stop!) (reset! state {:status :idle})))
       :sx {:flex "1 1 140px"
            :minHeight 84
            :px 2
            :py 1.25
            :border 2
            :borderColor "primary.main"
            :borderRadius 2
            :color "text.primary"
            :font "inherit"
            :cursor "pointer"
            :textAlign "center"
            :background (str "linear-gradient(to right, rgba(232,195,200,0.22) "
                             elapsed-pct
                             "%, transparent "
                             elapsed-pct
                             "%)")
            :animation (when (= status :done) "stepTimerFlash 0.6s 5")
            "@keyframes stepTimerFlash"
            {"0%, 100%" {:backgroundColor "transparent"}
             "50%" {:backgroundColor "rgba(232,195,200,0.5)"}}}}
      [typography
       {:sx {:fontSize "1rem"
             :letterSpacing "0.08em"
             :textTransform "uppercase"
             :color "text.secondary"}} action]
      [typography
       {:sx {:fontSize "2rem"
             :fontWeight 700
             :lineHeight 1.2
             :color "primary.main"
             :fontVariantNumeric "tabular-nums"}}
       (case status
         :idle (str seconds "s")
         :running (js/Math.ceil (/ remaining-ms 1000))
         :done "Done")]])
   (finally (stop!))))

(defn- ingredient-line
  [{:keys [amount unit name]}]
  (let [quantity (str/join " " (filter seq [amount unit]))]
    [box {:component "li" :sx {:mb 1.25}}
     [typography {:sx {:fontSize "1.4rem" :lineHeight 1.35}}
      (when (seq quantity)
        [box
         {:component "span" :sx {:fontWeight 700 :color "primary.main" :mr 1}}
         quantity]) name]]))

(defn recipe-mode-dialog
  "Mounted only while recipe mode is open, so the wake lock's lifetime is
   tied to the component's."
  [app-state recipe]
  (r/with-let
   [_
    (do (reset! mode-active? true)
        (request-wake-lock!)
        (.addEventListener js/document
                           "visibilitychange"
                           handle-visibility-change))]
   (let [close! #(swap! app-state assoc-in [:bar :recipe-mode?] false)]
     [dialog
      {:open true
       :full-screen true
       :on-close close!
       :PaperProps {:sx {:bgcolor "background.default"
                         :backgroundImage "none"}}}
      ;; No width/100% here: without CssBaseline box-sizing is content-box,
      ;; so width + padding would overflow the viewport on mobile
      [box {:sx {:p {:xs 2.5 :sm 4} :maxWidth 700 :mx "auto"}}
       [box
        {:sx {:display "flex"
              :alignItems "flex-start"
              :justifyContent "space-between"
              :gap 1
              :mb 2}}
        [typography
         {:sx {:fontSize {:xs "1.9rem" :sm "2.3rem"}
               :fontWeight 600
               :lineHeight 1.15
               :color "primary.main"}} (:name recipe)]
        [icon-button {:on-click close! :sx {:mt 0.5 :color "text.secondary"}}
         [close]]]
       (when-let [caption (:caption recipe)]
         [typography
          {:sx {:fontStyle "italic"
                :color "text.secondary"
                :fontSize "1.05rem"
                :mb 2}} caption])
       [box {:component "ul" :sx {:listStyleType "none" :pl 0 :mt 0 :mb 3.5}}
        (map-indexed (fn [idx ingredient]
                       ^{:key idx} [ingredient-line ingredient])
                     (:ingredients recipe))]
       (when-let [instructions (:instructions recipe)]
         [box {:sx {:mb 3}}
          (for [[idx line] (map-indexed
                            vector
                            (remove str/blank? (str/split-lines instructions)))]
            ^{:key idx}
            [typography {:sx {:fontSize "1.3rem" :lineHeight 1.5 :mb 1.5}}
             line])])
       (when-let [timers (seq (:timers recipe))]
         [box {:sx {:display "flex" :flexWrap "wrap" :gap 1.5 :mb 3}}
          (for [[idx timer] (map-indexed vector timers)]
            ^{:key idx} [step-timer timer])])
       (when-let [notes (:notes recipe)]
         [typography
          {:sx {:fontSize "1.05rem"
                :fontStyle "italic"
                :color "text.secondary"
                :whiteSpace "pre-line"}} notes])]])
   (finally (reset! mode-active? false)
            (.removeEventListener js/document
                                  "visibilitychange"
                                  handle-visibility-change)
            (release-wake-lock!)
            ;; If the recipe view unmounts underneath us (Done/Edit/
            ;; Delete), don't leave the flag armed for the next recipe
            (swap! app-state assoc-in [:bar :recipe-mode?] false))))
