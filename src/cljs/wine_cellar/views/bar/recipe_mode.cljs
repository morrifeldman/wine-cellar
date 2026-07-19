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
