(ns wine-cellar.theme
  (:require [reagent-mui.styles :refer [create-theme]]))

(def wine-theme
  (create-theme
   {:palette {:primary {:main "#722F37" ;; Burgundy wine color
                        :light "#9A4F57"
                        :dark "#4A1F24"}
              :secondary {:main "#8A9A5B" ;; Olive green (vineyard)
                          :light "#A8B47D"
                          :dark "#5C6A3D"}
              :background {:default "#FFFBF5" ;; Cream/parchment
                           :paper "#FFFFFF"}
              :success {:main "#2E7D32"}
              :error {:main "#B71C1C"}
              :rating {:high "#722F37" ;; Wine color for high ratings
                       :medium "#FFA000" ;; Amber for medium
                       :low "#757575"}} ;; Gray for low

    ;; In Material UI v5, typography variants are defined differently
    :typography {:fontFamily "'Raleway', 'Roboto', 'Helvetica', 'Arial', sans-serif"
                 :h1 {:fontWeight 300}
                 :h2 {:fontWeight 400}
                 :h3 {:fontWeight 400}
                 :h4 {:fontWeight 500}
                 :h5 {:fontWeight 500}
                 :h6 {:fontWeight 500}
                 :subtitle1 {:fontSize "1rem", :fontWeight 400}
                 :subtitle2 {:fontSize "0.875rem", :fontWeight 500}
                 :body1 {:fontSize "1rem"}
                 :body2 {:fontSize "0.875rem"}}

    :components {:MuiTypography {:styleOverrides {;; Define default styles for each variant
                                                  :h2 {:fontWeight 400, :fontSize "3rem"}
                                                  :subtitle1 {:fontSize "1rem", :fontWeight 400}
                                                  :body1 {:fontSize "1rem"}
                                                  :body2 {:fontSize "0.875rem"}}}
                 :MuiPaper {:styleOverrides
                            {:root {:boxShadow "0px 3px 15px rgba(0,0,0,0.05)"}}}
                 :MuiButton {:styleOverrides
                             {:root {:textTransform "none"
                                     :fontWeight 500}}}}
    :shape {:borderRadius 8}}))

