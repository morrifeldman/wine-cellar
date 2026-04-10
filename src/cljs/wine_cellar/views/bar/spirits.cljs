(ns wine-cellar.views.bar.spirits
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent-mui.material.box :refer [box]]
            [reagent-mui.material.paper :refer [paper]]
            [reagent-mui.material.typography :refer [typography]]
            [reagent-mui.material.button :refer [button]]
            [reagent-mui.material.chip :refer [chip]]
            [reagent-mui.material.divider :refer [divider]]
            [reagent-mui.material.text-field :as mui-text-field]
            [reagent-mui.material.select :refer [select]]
            [reagent-mui.material.menu-item :refer [menu-item]]
            [reagent-mui.material.icon-button :refer [icon-button]]
            [reagent-mui.material.circular-progress :refer [circular-progress]]
            [reagent-mui.icons.auto-awesome :refer [auto-awesome]]
            [reagent-mui.icons.public :refer [public] :rename {public globe}]
            [reagent-mui.icons.inventory :refer [inventory]]
            [reagent-mui.icons.notes :refer [notes] :rename {notes notes-icon}]
            [reagent-mui.icons.keyboard-arrow-up :refer [keyboard-arrow-up]]
            [reagent-mui.icons.keyboard-arrow-down :refer [keyboard-arrow-down]]
            [wine-cellar.utils.filters :refer [normalize-text]]
            [wine-cellar.api :as api]
            [wine-cellar.views.components :refer
             [dot-separated-row editable-text-field editable-autocomplete-field
              search-text-field section-header]]
            [wine-cellar.views.components.ai-provider-toggle :refer
             [provider-toggle-button]]
            [wine-cellar.views.components.image-upload :refer
             [camera-capture]]))

(def spirit-categories
  ["whiskey" "gin" "rum" "vodka" "tequila" "mezcal" "brandy" "liqueur"
   "vermouth" "other"])

(def ^:private category-labels
  (into {}
        (map (fn [c] [c (str (str/upper-case (subs c 0 1)) (subs c 1))])
             spirit-categories)))

(def ^:private category-colors
  {"whiskey" {:base "180,120,60" :text "#e8c890"}
   "gin" {:base "120,180,120" :text "#b0d8b0"}
   "rum" {:base "200,140,80" :text "#e8c090"}
   "vodka" {:base "160,180,200" :text "#c8d8e8"}
   "tequila" {:base "180,200,100" :text "#d0e080"}
   "mezcal" {:base "160,180,80" :text "#c0d070"}
   "brandy" {:base "180,100,80" :text "#d8a080"}
   "liqueur" {:base "200,120,160" :text "#e8a0c0"}
   "vermouth" {:base "200,180,80" :text "#e8d070"}
   "other" {:base "160,160,160" :text "#c0c0c0"}})

(defn- spirit-search-text
  [s]
  (->> [(:name s) (:category s) (:subcategory s) (:distillery s) (:country s)
        (:region s) (:notes s) (:age_statement s)]
       (filter some?)
       (str/join " ")))

(defn- unique-spirit-values
  [spirits k]
  (->> spirits
       (map k)
       (remove str/blank?)
       distinct
       sort
       vec))

(defn- spirit-create-form
  "Scan-first creation: capture label, analyze with AI, create spirit with all fields."
  [app-state]
  (r/with-let
   [show-camera? (r/atom true) label-image (r/atom nil) name-val (r/atom "")
    category-val (r/atom "") submitting? (r/atom false)]
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
                  (let [spirit-data (into
                                     {}
                                     (filter (fn [[_ v]]
                                               (and (some? v) (not= v "null"))))
                                     (select-keys result
                                                  [:name :category :subcategory
                                                   :distillery :country :region
                                                   :age_statement :proof]))]
                    (when (and (:name spirit-data) (:category spirit-data))
                      (reset! submitting? true)
                      (-> (api/create-spirit app-state spirit-data)
                          (.then (fn [created]
                                   (reset! submitting? false)
                                   (swap! app-state assoc-in
                                     [:bar :show-spirit-form?]
                                     false)
                                   (swap! app-state assoc-in
                                     [:bar :editing-spirit-id]
                                     (:id created))))
                          (.catch (fn [_] (reset! submitting? false))))))))
               (.catch (fn [err]
                         (swap! app-state assoc
                           :error
                           (str "Failed to analyze label: " err))))))
         :start-icon (when-not (:analyzing-spirit-label? @app-state)
                       (r/as-element [auto-awesome]))}
        (cond (:analyzing-spirit-label? @app-state)
              [box {:sx {:display "flex" :alignItems "center"}}
               [circular-progress {:size 16 :sx {:mr 0.5}}] "Analyzing..."]
              @submitting? "Creating..."
              :else "Analyze & Add")]
       [provider-toggle-button app-state
        {:mobile-min-width "auto" :sx {:minWidth "auto" :px 1 :py 0.25}}]
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
            :on-click #(reset! show-camera? true)} "Scan Label"]])
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
           :on-change #(reset! name-val (-> %
                                            .-target
                                            .-value))
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
           :renderValue
           (fn [v] (if (str/blank? v) "Category" (get category-labels v v)))
           :on-change #(reset! category-val (-> %
                                                .-target
                                                .-value))
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
           :disabled @submitting?} (if @submitting? "Creating..." "Add")]
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
             :on-save #(api/update-spirit app-state (:id spirit) {:category %})
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
                          (api/update-spirit app-state
                                             (:id spirit)
                                             {:proof (when-not (js/isNaN parsed)
                                                       parsed)})))
             :format-fn #(str % " proof")
             :empty-text "Add proof"
             :inline? true}]]]
         ;; Cellar section
         [box
          {:sx
           {:mt 2 :borderLeft "3px solid rgba(100,181,246,0.7)" :pl 1.5 :pb 2}}
          [section-header inventory "Cellar" "rgba(100,181,246,0.7)"]
          [dot-separated-row
           [box {:sx {:display "inline-flex" :alignItems "center" :gap 0.5}}
            [typography {:variant "body2"} (str (or (:quantity spirit) 1))]
            [box {:sx {:display "flex" :flexDirection "column" :ml -0.25}}
             [icon-button
              {:size "small"
               :color "inherit"
               :sx {:p 0}
               :on-click #(api/update-spirit app-state
                                             (:id spirit)
                                             {:quantity
                                              (inc (or (:quantity spirit) 1))})}
              [keyboard-arrow-up {:sx {:fontSize 16}}]]
             [icon-button
              {:size "small"
               :color "inherit"
               :sx {:p 0}
               :disabled (<= (or (:quantity spirit) 1) 0)
               :on-click
               #(let [q (max 0 (dec (or (:quantity spirit) 1)))]
                  (api/update-spirit app-state (:id spirit) {:quantity q}))}
              [keyboard-arrow-down {:sx {:fontSize 16}}]]]]
           [editable-text-field
            {:value (when (:price spirit) (str (:price spirit)))
             :on-save (fn [v]
                        (let [parsed (js/parseFloat v)]
                          (api/update-spirit app-state
                                             (:id spirit)
                                             {:price (when-not (js/isNaN parsed)
                                                       parsed)})))
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
             :on-save #(api/update-spirit app-state (:id spirit) {:location %})
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
            :on-click #(swap! app-state assoc-in [:bar :editing-spirit-id] nil)}
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
  (let [finished? (zero? (or (:quantity spirit) 1))]
    [paper
     {:elevation (if finished? 0 1)
      :sx {:p 1.5
           :mb 1
           :cursor "pointer"
           :opacity (if finished? 0.45 1)
           "&:hover" {:bgcolor "action.hover"}}
      :on-click
      #(swap! app-state assoc-in [:bar :editing-spirit-id] (:id spirit))}
     [typography {:variant "body1" :sx {:fontWeight 600 :lineHeight 1.2}}
      (->> [(:distillery spirit) (:name spirit) (:category spirit)]
           (filter seq)
           (str/join " · "))]
     [typography
      {:variant "body2"
       :sx {:color "text.secondary" :fontSize "0.8rem" :mt 0.25}}
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
              :whiteSpace "nowrap"}} (:notes spirit)])]))

(defn- category-filter-bar
  [selected-categories spirits]
  (let [present (set (map :category spirits))
        cats (filter present spirit-categories)]
    [box
     {:sx
      {:display "flex" :gap 0.5 :flexWrap "wrap" :alignItems "center" :mb 1.5}}
     (for [cat cats]
       (let [active? (contains? @selected-categories cat)
             {:keys [base text]}
             (get category-colors cat {:base "160,160,160" :text "#c0c0c0"})]
         ^{:key cat}
         [chip
          {:label (get category-labels cat cat)
           :size "small"
           :clickable true
           :on-click
           #(swap! selected-categories
              (fn [s] (if (contains? s cat) (disj s cat) (conj s cat))))
           :sx {:height 24
                :fontSize "0.72rem"
                :letterSpacing "0.02em"
                :bgcolor (str "rgba(" base "," (if active? "0.22" "0.06") ")")
                :color text
                :border
                (str "1px solid rgba(" base "," (if active? "0.6" "0.2") ")")
                "&:hover" {:bgcolor (str "rgba(" base ",0.15)")}}}]))
     (when (seq @selected-categories)
       [button
        {:size "small"
         :sx {:ml 0.5 :fontSize "0.7rem" :minWidth 0 :px 1}
         :on-click #(reset! selected-categories #{})} "clear"])]))

(defn- subcategory-filter-bar
  [selected-subcategories spirits]
  (let [cat-order (into {} (map-indexed (fn [i c] [c i]) spirit-categories))
        subcat-pairs (->> spirits
                          (filter #(not (str/blank? (:subcategory %))))
                          (map (fn [s]
                                 {:subcat (:subcategory s) :cat (:category s)}))
                          distinct)
        sorted-pairs (sort-by (fn [{:keys [cat subcat]}] [(get cat-order cat 99)
                                                          subcat])
                              subcat-pairs)]
    (when (seq sorted-pairs)
      [:<> [divider {:sx {:mb 1 :borderColor "rgba(232,195,200,0.35)"}}]
       [box
        {:sx {:display "flex"
              :gap 0.5
              :flexWrap "wrap"
              :alignItems "center"
              :mb 1.5
              :ml 1}}
        (let [indexed (map-indexed vector sorted-pairs)]
          (for [[i {:keys [subcat cat]}] indexed]
            (let [active? (contains? @selected-subcategories subcat)
                  {:keys [base text]} (get category-colors
                                           cat
                                           {:base "160,160,160"
                                            :text "#c0c0c0"})
                  prev-cat (when (pos? i) (:cat (nth sorted-pairs (dec i))))]
              ^{:key subcat}
              [:<>
               (when (and prev-cat (not= prev-cat cat))
                 [divider
                  {:orientation "vertical"
                   :flexItem true
                   :sx {:mx 0.5 :borderColor "rgba(232,195,200,0.5)"}}])
               [chip
                {:label subcat
                 :size "small"
                 :clickable true
                 :on-click
                 #(swap! selected-subcategories (fn [s]
                                                  (if (contains? s subcat)
                                                    (disj s subcat)
                                                    (conj s subcat))))
                 :sx
                 {:height 22
                  :fontSize "0.7rem"
                  :letterSpacing "0.02em"
                  :bgcolor (str "rgba(" base "," (if active? "0.22" "0.06") ")")
                  :color text
                  :border
                  (str "1px solid rgba(" base "," (if active? "0.6" "0.2") ")")
                  "&:hover" {:bgcolor (str "rgba(" base ",0.15)")}}}]])))
        (when (seq @selected-subcategories)
          [button
           {:size "small"
            :sx {:ml 0.5 :fontSize "0.7rem" :minWidth 0 :px 1}
            :on-click #(reset! selected-subcategories #{})} "clear"])]])))

(defn spirits-tab
  [_app-state]
  (let [search-text (r/atom "")
        selected-categories (r/atom #{})
        selected-subcategories (r/atom #{})]
    (fn [app-state]
      (let [bar @(r/cursor app-state [:bar])
            spirits (:spirits bar)
            show-form? (:show-spirit-form? bar)
            editing-id (:editing-spirit-id bar)
            loading? (:loading? bar)
            term (normalize-text @search-text)
            sel-cats @selected-categories
            sel-subcats @selected-subcategories
            cat-filtered
            (cond->> spirits
              (seq term) (filter #(str/includes? (normalize-text
                                                  (spirit-search-text %))
                                                 term))
              (seq sel-cats) (filter #(contains? sel-cats (:category %))))
            filtered (cond->> cat-filtered
                       (seq sel-subcats) (filter #(contains? sel-subcats
                                                             (:subcategory %)))
                       true (sort-by #(if (pos? (or (:quantity %) 1)) 0 1)))]
        [box (when show-form? [spirit-create-form app-state])
         (when (and (seq spirits) (not loading?))
           [:<>
            [search-text-field
             {:search-atom search-text :label "Search spirits"}]
            [category-filter-bar selected-categories spirits]
            (when (seq sel-cats)
              [subcategory-filter-bar selected-subcategories cat-filtered])])
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
