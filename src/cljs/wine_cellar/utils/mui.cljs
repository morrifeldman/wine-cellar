(ns wine-cellar.utils.mui)

(defn- shallow-js->clj
  [o]
  (into {} (for [k (js/Object.keys o)] [(keyword k) (unchecked-get o k)])))

(defn safe-js-props
  "Safely converts a JS props object (e.g. from MUI renderInput) to a Clojure map
   shallowly, while also specifically converting nested prop objects like
   InputProps, inputProps, and InputLabelProps to maps.
   This prevents stack overflow from recursive js->clj on React elements,
   while ensuring reagent-mui can manipulate the props (e.g. dissoc)."
  [params]
  (let [p (shallow-js->clj params)
        p (if (:InputProps p) (update p :InputProps shallow-js->clj) p)
        p (if (:inputProps p) (update p :inputProps shallow-js->clj) p)
        p
        (if (:InputLabelProps p) (update p :InputLabelProps shallow-js->clj) p)]
    p))
