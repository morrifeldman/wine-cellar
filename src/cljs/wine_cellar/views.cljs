(ns wine-cellar.views
  (:require [reagent.core :as r]
            ["@mui/material/Button" :default Button]
            ["@mui/material/styles" :refer [ThemeProvider createTheme]]))

(def theme (createTheme #js {}))

(defn app []
  [:> ThemeProvider {:theme theme}
    [:> Button {:color "primary"
                :variant "contained"}
     "Hello, Material UI!"]])