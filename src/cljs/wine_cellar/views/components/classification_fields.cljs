;; Create a new file:
;; src/cljs/wine_cellar/views/components/classification_fields.cljs
(ns wine-cellar.views.components.classification-fields
  (:require [wine-cellar.utils.formatting :refer
             [unique-countries regions-for-country aocs-for-region
              vineyards-for-region classifications-for-aoc
              designations-for-classification]]
            [wine-cellar.views.components.form :as form]))

(defn classification-fields
  "Shared component for classification fields with cascading dropdowns.
   
   Parameters:
   - app-state: The application state atom
   - path: The path in app-state where classification data is stored
   - classifications: The list of all classifications
   - options: Map of options:
     - :include-designation? - Whether to include the designation field (default: false)
     - :required? - Whether fields are required (default: true for country/region, false for others)
     - :on-change - Optional callback when any field changes"
  [app-state path classifications &
   {:keys [include-designation? required? on-change]
    :or {include-designation? false
         required? {:country true
                    :region true
                    :aoc false
                    :classification false
                    :vineyard false
                    :designation false}}}]
  (let [data (get-in @app-state path)
        country (:country data)
        region (:region data)
        aoc (:aoc data)
        classification (:classification data)
        update-field! (fn [field value]
                        (tap> ["fields-component path field" path field value])
                        (swap! app-state assoc-in (conj path field) value)
                        (when on-change (on-change field value)))]
    [:<>
     [form/form-row
      [form/select-field
       {:label "Country"
        :required (get required? :country true)
        :free-solo true
        :value country
        :options (unique-countries classifications)
        :on-change #(do (tap> ["country on-change" %])
                        (update-field! :country %)
                        ;; Clear dependent fields when country changes
                        (when (not= country %)
                          (update-field! :region nil)
                          (update-field! :aoc nil)
                          (update-field! :classification nil)
                          (update-field! :vineyard nil)
                          (when include-designation?
                            (update-field! :designation nil))))}]
      [form/select-field
       {:label "Region"
        :required (get required? :region true)
        :free-solo true
        :disabled (empty? country)
        :value region
        :options (regions-for-country classifications country)
        :on-change #(do (update-field! :region %)
                        ;; Clear dependent fields when region changes
                        (when (not= region %)
                          (update-field! :aoc nil)
                          (update-field! :classification nil)
                          (update-field! :vineyard nil)
                          (when include-designation?
                            (update-field! :designation nil))))}]
      [form/select-field
       {:label "AOC"
        :required (get required? :aoc false)
        :free-solo true
        :disabled (or (empty? country) (empty? region))
        :value aoc
        :options (aocs-for-region classifications country region)
        :on-change #(do (update-field! :aoc %)
                        ;; Clear dependent fields when AOC changes
                        (when (not= aoc %)
                          (update-field! :classification nil)
                          (when include-designation?
                            (update-field! :designation nil))))}]]
     [form/form-row
      [form/select-field
       {:label "Classification"
        :required (get required? :classification false)
        :free-solo true
        :disabled (or (empty? country) (empty? region))
        :value classification
        :options (classifications-for-aoc classifications country region aoc)
        :on-change #(do (update-field! :classification %)
                        ;; Clear dependent fields when classification
                        ;; changes
                        (when (and include-designation? (not= classification %))
                          (update-field! :designation nil)))}]
      [form/select-field
       {:label "Vineyard"
        :required (get required? :vineyard false)
        :free-solo true
        :disabled (or (empty? country) (empty? region))
        :value (:vineyard data)
        :options (vineyards-for-region classifications country region)
        :on-change #(update-field! :vineyard %)}]
      (when include-designation?
        [form/select-field
         {:label "Designation"
          :required (get required? :designation false)
          :free-solo true
          :disabled (or (empty? country) (empty? region))
          :value (:designation data)
          :options (designations-for-classification classifications
                                                    country
                                                    region
                                                    aoc
                                                    classification)
          :on-change #(update-field! :designation %)}])]]))
