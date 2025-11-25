(ns wine-cellar.views.admin.devices
  (:require [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.text-field :refer [text-field]]
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.material.stack :refer [stack]]
            [wine-cellar.api :as api]))

(defn status-chip
  [status]
  (let [status-str (or status "unknown")
        color (case status-str
                "active" "success"
                "pending" "warning"
                "blocked" "default"
                "default")]
    [chip {:label status-str :color color :variant "outlined"}]))

(defn device-row
  [{:keys [device_id status last_seen token_expires_at firmware_version]
    :as device} app-state]
  (let [claim (r/atom "")]
    (fn []
      [paper {:sx {:p 2 :mb 1}}
       [stack
        {:direction "row" :spacing 2 :alignItems "center" :flexWrap "wrap"}
        [typography {:variant "subtitle1" :sx {:fontWeight 600}} device_id]
        [status-chip status]
        (when firmware_version
          [chip {:label (str "fw " firmware_version) :size "small"}])
        (when last_seen
          [chip
           {:label (str "last " last_seen) :size "small" :variant "outlined"}])
        (when token_expires_at
          [chip
           {:label (str "exp " token_expires_at)
            :size "small"
            :variant "outlined"}])]
       [stack {:direction "row" :spacing 1 :sx {:mt 1} :alignItems "center"}
        [text-field
         {:label "Claim code"
          :size "small"
          :value @claim
          :on-change #(reset! claim (.. % -target -value))
          :disabled (or (= status "active") (= status "blocked"))
          :sx {:minWidth 180}}]
        (when (not= status "blocked")
          [button
           {:variant "contained"
            :size "small"
            :disabled (= status "active")
            :on-click #(api/approve-device app-state device_id @claim)}
           "Approve"])
        [button
         {:variant (if (= status "blocked") "outlined" "text")
          :size "small"
          :color (if (= status "blocked") "success" "error")
          :on-click #(if (= status "blocked")
                       (api/unblock-device app-state device_id)
                       (api/block-device app-state device_id))}
         (if (= status "blocked") "Unblock" "Block")]
        [button
         {:variant "text"
          :size "small"
          :color "error"
          :on-click #(when (js/confirm (str "Delete device " device_id "?"))
                       (api/delete-device app-state device_id))} "Delete"]]])))

(defn devices-page
  [app-state]
  (let [devices (or (get @app-state :devices/list) [])
        loading? (get @app-state :devices/loading?)
        error (get @app-state :devices/error)
        approve-error (get @app-state :devices/approve-error)]
    [box {:sx {:maxWidth 900 :mx "auto" :my 3}}
     [typography {:variant "h5" :sx {:mb 2}} "Devices"]
     (when loading? [typography {:variant "body2"} "Loading…"])
     (when error
       [typography {:variant "body2" :color "error.main" :sx {:mb 1}} error])
     (when approve-error
       [typography {:variant "body2" :color "error.main" :sx {:mb 1}}
        approve-error])
     (cond (and (not loading?) (seq devices))
           (for [d devices] ^{:key (:device_id d)} [device-row d app-state])
           (and (not loading?) (empty? devices))
           [stack {:spacing 1}
            [typography {:variant "body2" :color "text.secondary"}
             "No devices yet."]
            [button
             {:variant "outlined"
              :size "small"
              :on-click #(api/fetch-devices app-state)} "Refresh"]]
           :else nil)]))
