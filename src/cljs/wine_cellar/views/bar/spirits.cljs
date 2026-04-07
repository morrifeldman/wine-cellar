(ns wine-cellar.views.bar.spirits
  (:require
    [clojure.string :as str]
    [reagent.core :as r]
    [reagent-mui.material.box :refer [box]]
    [reagent-mui.material.paper :refer [paper]]
    [reagent-mui.material.typography :refer [typography]]
    [reagent-mui.material.button :refer [button]]
    [reagent-mui.material.text-field :as mui-text-field]
    [reagent-mui.material.select :refer [select]]
    [reagent-mui.material.menu-item :refer [menu-item]]
    [reagent-mui.material.circular-progress :refer [circular-progress]]
    [reagent-mui.material.icon-button :refer [icon-button]]
    [reagent-mui.material.input-adornment :refer [input-adornment]]
    [reagent-mui.icons.add :refer [add]]
    [reagent-mui.icons.close :refer [close]]
    [reagent-mui.icons.auto-awesome :refer [auto-awesome]]
    [reagent-mui.icons.public :refer [public] :rename {public globe}]
    [reagent-mui.icons.inventory :refer [inventory]]
    [reagent-mui.icons.notes :refer [notes] :rename {notes notes-icon}]
    [wine-cellar.utils.filters :refer [normalize-text]]
    [wine-cellar.api :as api]
    [wine-cellar.views.components :refer
     [dot-separated-row editable-text-field editable-autocomplete-field]]
    [wine-cellar.views.components.ai-provider-toggle :refer
     [provider-toggle-button]]
    [wine-cellar.views.components.image-upload :refer [camera-capture]]))

(def spirit-categories
  ["whiskey" "gin" "rum" "vodka" "tequila" "mezcal" "brandy" "liqueur" "other"])

(def ^:private category-labels
  (into {}
        (map (fn [c] [c (str (str/upper-case (subs c 0 1)) (subs c 1))])
             spirit-categories)))

(defn- unique-spirit-values
  [spirits k]
  (->> spirits
       (map k)
       (remove str/blank?)
       distinct
       sort
       vec))

(defn- section-header
  [icon-component label border-color]
  [box
   {:sx {:display "flex"
         :alignItems "center"
         :mb 1.5
         :pb 1
         :borderBottom "1px solid rgba(255,255,255,0.06)"}}
   [box {:sx {:color border-color :display "flex" :mr 1 :opacity 0.85}}
    [icon-component {:fontSize "small"}]]
   [typography
    {:variant "overline"
     :sx {:fontWeight 700
          :letterSpacing "0.1em"
          :color "text.secondary"
          :lineHeight 1}} label]])

(defn- spirit-create-form
  "Scan-first creation: capture label, analyze with AI, create spirit with all fields."
  [app-state]
  (r/with-let
   [show-camera? (r/atom true) label-image (r/atom nil)
    name-val (r/atom "") category-val (r/atom "") submitting? (r/atom false)]
   [paper {:elevation 0 :sx {:p 2 :mb 2 :bgcolor "transparent"}}
    (when @show-camera?
      [camera-capture
       (fn [captured]
         (reset! show-camera? false)
         (reset! label-image (:label_image captured)))
       #(reset! show-camera? false)])
    (when (and @label-image (not @show-camera?))
      [box {:sx {:display "flex" :gap 1 :mb 2 :alignItems "center"}}
       [button
        {:variant "contained"
         :color "secondary"
         :size "small"
         :disabled (:analyzing-spirit-label? @app-state)
         :on-click
         (fn []
           (-> (api/analyze-spirit-label app-state @label-image)
               (.then
                (fn [result]
                  (let [spirit-data
                        (into {}
                              (filter (fn [[_ v]] (and (some? v) (not= v "null"))))
                              (select-keys result
                                           [:name :category :subcategory :distillery
                                            :country :region :age_statement :proof]))]
                    (when (and (:name spirit-data) (:category spirit-data))
                      (reset! submitting? true)
                      (-> (api/create-spirit app-state spirit-data)
                          (.then
                           (fn [created]
                             (reset! submitting? false)
                             (swap! app-state assoc-in
                               [:bar :show-spirit-form?]
                               false)
                             (swap! app-state assoc-in
                               [:bar :editing-spirit-id]
                               (:id created))))
                          (.catch (fn [_] (reset! submitting? false))))))))
               (.catch (fn [err]
                         (swap! app-state assoc :error
                           (str "Failed to analyze label: " err))))))
         :start-icon
         (when-not (:analyzing-spirit-label? @app-state)
           (r/as-element [auto-awesome]))}
        (cond
          (:analyzing-spirit-label? @app-state)
          [box {:sx {:display "flex" :alignItems "center"}}
           [circular-progress {:size 16 :sx {:mr 0.5}}] "Analyzing..."]
          @submitting? "Creating..."
          :else "Analyze & Add")]
       [provider-toggle-button app-state
        {:mobile-min-width "auto"
         :sx {:minWidth "auto" :px 1 :py 0.25}}]
       [button
        {:variant "outlined"
         :size "small"
         :on-click #(do (reset! label-image nil) (reset! show-camera? true))}
        "Retake"]])
    ;; Manual fallback
    (when-not @show-camera?
      [box {:sx {:mt (if @label-image 0 1)}}
       (when-not @label-image
         [box {:sx {:display "flex" :gap 1 :mb 2}}
          [button
           {:variant "outlined"
            :size "small"
            :start-icon (r/as-element [auto-awesome])
            :on-click #(reset! show-camera? true)}
           "Scan Label"]])
       [:form
        {:on-submit
         (fn [e]
           (.preventDefault e)
           (when (and (seq @name-val) (seq @category-val) (not @submitting?))
             (reset! submitting? true)
             (-> (api/create-spirit app-state
                                    {:name @name-val :category @category-val})
                 (.then
                  (fn [created]
                    (reset! submitting? false)
                    (swap! app-state assoc-in [:bar :show-spirit-form?] false)
                    (swap! app-state assoc-in
                      [:bar :editing-spirit-id]
                      (:id created))))
                 (.catch (fn [_] (reset! submitting? false))))))}
        [box
         {:sx {:display "flex" :gap 2 :alignItems "flex-end" :flexWrap "wrap"}}
         [mui-text-field/text-field
          {:value @name-val
           :on-change #(reset! name-val (-> % .-target .-value))
           :label "Name"
           :required true
           :variant "standard"
           :size "small"
           :auto-focus true
           :sx {:flex 1 :minWidth 140}}]
         [select
          {:value @category-val
           :variant "standard"
           :displayEmpty true
           :required true
           :renderValue (fn [v]
                          (if (str/blank? v)
                            "Category"
                            (get category-labels v v)))
           :on-change #(reset! category-val (-> % .-target .-value))
           :sx {:fontSize "0.875rem"
                :minWidth 100
                :color (if (str/blank? @category-val)
                         "text.secondary"
                         "text.primary")}}
          (for [cat spirit-categories]
            ^{:key cat} [menu-item {:value cat} (get category-labels cat)])]
         [button
          {:type "submit"
           :variant "contained"
           :size "small"
           :disabled @submitting?}
          (if @submitting? "Creating..." "Add")]
         [button
          {:variant "outlined"
           :size "small"
           :on-click
           #(do (swap! app-state assoc-in [:bar :show-spirit-form?] false)
                (swap! app-state assoc-in [:bar :new-spirit] {}))}
          "Cancel"]]]])]))

(defn- spirit-detail
  "Inline-edit detail view for a spirit."
  [_app-state]
  (fn [app-state]
    (let [bar (get @app-state :bar)
          editing-id (:editing-spirit-id bar)
          spirit (first (filter #(= (:id %) editing-id) (:spirits bar)))]
      (when spirit
        [paper {:elevation 0 :sx {:p 2 :mb 2 :bgcolor "transparent"}}
         ;; Identity row
           [box {:sx {:mb 3}}
            [dot-separated-row
             [editable-text-field
              {:value (:distillery spirit)
               :on-save
               #(api/update-spirit app-state (:id spirit) {:distillery %})
               :empty-text "Add distillery"
               :inline? true
               :display-sx {:fontSize "1.2rem" :fontWeight 500}}]
             [editable-text-field
              {:value (:name spirit)
               :on-save #(api/update-spirit app-state (:id spirit) {:name %})
               :empty-text "Add name"
               :inline? true
               :display-sx {:fontSize "1.2rem" :fontWeight 500}}]
             [editable-autocomplete-field
              {:value (:category spirit)
               :options spirit-categories
               :option-label #(get category-labels % %)
               :on-save
               #(api/update-spirit app-state (:id spirit) {:category %})
               :format-fn #(get category-labels % %)
               :empty-text "Set category"
               :inline? true}]
             [editable-autocomplete-field
              {:value (:subcategory spirit)
               :options (unique-spirit-values (:spirits bar) :subcategory)
               :free-solo true
               :on-save
               #(api/update-spirit app-state (:id spirit) {:subcategory %})
               :empty-text "Add subcategory"
               :inline? true}]]]
           ;; Origin section
           [box
            {:sx
             {:mt 2 :borderLeft "3px solid rgba(139,195,74,0.7)" :pl 1.5 :pb 2}}
            [section-header globe "Origin" "rgba(139,195,74,0.7)"]
            [dot-separated-row
             [editable-autocomplete-field
              {:value (:country spirit)
               :options (unique-spirit-values (:spirits bar) :country)
               :free-solo true
               :on-save #(api/update-spirit app-state (:id spirit) {:country %})
               :empty-text "Add country"
               :inline? true}]
             [editable-autocomplete-field
              {:value (:region spirit)
               :options (unique-spirit-values (:spirits bar) :region)
               :free-solo true
               :on-save #(api/update-spirit app-state (:id spirit) {:region %})
               :empty-text "Add region"
               :inline? true}]
             [editable-text-field
              {:value (:age_statement spirit)
               :on-save
               #(api/update-spirit app-state (:id spirit) {:age_statement %})
               :format-fn #(if (re-matches #"\d+" (str %)) (str % " yr") %)
               :empty-text "Add age"
               :inline? true}]
             [editable-text-field
              {:value (when (:proof spirit) (str (:proof spirit)))
               :on-save (fn [v]
                          (let [parsed (js/parseInt v)]
                            (api/update-spirit
                             app-state
                             (:id spirit)
                             {:proof (when-not (js/isNaN parsed) parsed)})))
               :format-fn #(str % " proof")
               :empty-text "Add proof"
               :inline? true}]]]
           ;; Cellar section
           [box
            {:sx {:mt 2
                  :borderLeft "3px solid rgba(100,181,246,0.7)"
                  :pl 1.5
                  :pb 2}}
            [section-header inventory "Cellar" "rgba(100,181,246,0.7)"]
            [dot-separated-row
             [editable-text-field
              {:value (when (:quantity spirit) (str (:quantity spirit)))
               :on-save (fn [v]
                          (let [parsed (js/parseInt v)]
                            (api/update-spirit
                             app-state
                             (:id spirit)
                             {:quantity (when-not (js/isNaN parsed) parsed)})))
               :format-fn #(str "qty: " %)
               :empty-text "Add qty"
               :inline? true}]
             [editable-text-field
              {:value (when (:price spirit) (str (:price spirit)))
               :on-save (fn [v]
                          (let [parsed (js/parseFloat v)]
                            (api/update-spirit
                             app-state
                             (:id spirit)
                             {:price (when-not (js/isNaN parsed) parsed)})))
               :format-fn #(str "$" %)
               :empty-text "Add price"
               :inline? true}]
             [editable-text-field
              {:value (:purchase_date spirit)
               :on-save
               #(api/update-spirit app-state (:id spirit) {:purchase_date %})
               :empty-text "Add date"
               :inline? true}]
             [editable-text-field
              {:value (:location spirit)
               :on-save
               #(api/update-spirit app-state (:id spirit) {:location %})
               :empty-text "Add location"
               :inline? true}]]]
           ;; Notes section
           [box
            {:sx
             {:mt 2 :borderLeft "3px solid rgba(255,213,79,0.7)" :pl 1.5 :pb 1}}
            [section-header notes-icon "Notes" "rgba(255,213,79,0.7)"]
            [editable-text-field
             {:value (:notes spirit)
              :on-save #(api/update-spirit app-state (:id spirit) {:notes %})
              :empty-text "Add tasting notes, impressions..."
              :text-field-props {:multiline true :rows 2}}]]
           ;; Actions
           [box {:sx {:display "flex" :gap 1 :justifyContent "flex-end" :mt 2}}
            [button
             {:variant "outlined"
              :color "error"
              :on-click
              #(when (js/confirm (str "Delete " (:name spirit) "?"))
                 (api/delete-spirit app-state (:id spirit))
                 (swap! app-state assoc-in [:bar :editing-spirit-id] nil))}
             "Delete"] [box {:sx {:flex 1}}]
            [button
             {:variant "contained"
              :on-click
              #(swap! app-state assoc-in [:bar :editing-spirit-id] nil)}
             "Done"]]]))))

(defn- spirit-meta
  [spirit]
  (->> [(:subcategory spirit) (:country spirit)
        (when (:age_statement spirit)
          (let [a (:age_statement spirit)]
            (if (re-matches #"\d+" a) (str a " yr") a)))
        (when (:proof spirit) (str (:proof spirit) " proof"))]
       (filter identity)
       (str/join " · ")))

(defn spirit-card
  [app-state spirit]
  [paper
   {:elevation 1
    :sx {:p 1.5 :mb 1 :cursor "pointer" "&:hover" {:bgcolor "action.hover"}}
    :on-click
    #(swap! app-state assoc-in [:bar :editing-spirit-id] (:id spirit))}
   [typography {:variant "body1" :sx {:fontWeight 600 :lineHeight 1.2}}
    (->> [(:distillery spirit) (:name spirit) (:category spirit)]
         (filter seq)
         (str/join " · "))]
   [typography
    {:variant "body2" :sx {:color "text.secondary" :fontSize "0.8rem" :mt 0.25}}
    (spirit-meta spirit)]
   (when (and (:notes spirit) (not= (:notes spirit) ""))
     [typography
      {:variant "body2"
       :sx {:color "text.secondary"
            :fontSize "0.75rem"
            :mt 0.5
            :fontStyle "italic"
            :overflow "hidden"
            :textOverflow "ellipsis"
            :whiteSpace "nowrap"}} (:notes spirit)])])

(defn spirits-tab
  [_app-state]
  (let [search-text (r/atom "")]
    (fn [app-state]
      (let [bar @(r/cursor app-state [:bar])
            spirits (:spirits bar)
            show-form? (:show-spirit-form? bar)
            editing-id (:editing-spirit-id bar)
            loading? (:loading? bar)
            term (normalize-text @search-text)
            filtered
            (if (seq term)
              (filter (fn [s]
                        (some #(when % (str/includes? (normalize-text %) term))
                              [(:name s) (:category s) (:subcategory s)
                               (:distillery s) (:country s) (:region s)
                               (:notes s) (:age_statement s)]))
                      spirits)
              spirits)
            count-label
            (if (seq term)
              (str "Spirits (" (count filtered) "/" (count spirits) ")")
              (str "Spirits (" (count spirits) ")"))]
        [box
         [box {:sx {:display "flex" :justifyContent "space-between" :mb 2}}
          [typography {:variant "h6"} count-label]
          (when-not (or show-form? editing-id)
            [button
             {:variant "outlined"
              :color "primary"
              :start-icon (r/as-element [add])
              :on-click
              #(swap! app-state assoc-in [:bar :show-spirit-form?] true)}
             "Add Spirit"])] (when show-form? [spirit-create-form app-state])
         (when (and (seq spirits) (not loading?))
           [mui-text-field/text-field
            (cond-> {:label "Search spirits"
                     :value @search-text
                     :on-change #(reset! search-text (-> %
                                                         .-target
                                                         .-value))
                     :size "small"
                     :full-width true
                     :sx {:mb 2}}
              (seq @search-text)
              (assoc :InputProps
                     {:endAdornment
                      (r/as-element
                       [input-adornment {:position "end"}
                        [icon-button
                         {:size "small"
                          :edge "end"
                          :on-click #(reset! search-text "")
                          :sx {:color "text.secondary"}}
                         [close {:fontSize "small"}]]])}))])
         (if loading?
           [box {:sx {:display "flex" :justifyContent "center" :py 4}}
            [circular-progress {:color "primary"}]]
           (if (empty? spirits)
             [typography
              {:sx {:color "text.secondary" :textAlign "center" :py 4}}
              "No spirits yet. Add your first bottle!"]
             (for [spirit filtered]
               ^{:key (:id spirit)}
               (if (= (:id spirit) editing-id)
                 [spirit-detail app-state]
                 [spirit-card app-state spirit]))))]))))
