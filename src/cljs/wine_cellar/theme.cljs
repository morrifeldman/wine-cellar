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
                       :low "#757575"} ;; Gray for low
              :tasting-window {:ready "#2E7D32" ;; Green for ready to drink
                               :young "#1976D2" ;; Blue for too young
                               :past "#FF9800"}} ;; Orange for past prime

    ;; Typography improvements
    :typography {:fontFamily "'Raleway', 'Roboto', 'Helvetica', 'Arial', sans-serif"
                 :h1 {:fontWeight 300, :fontSize "2.5rem"}
                 :h2 {:fontWeight 400, :fontSize "2rem"}
                 :h3 {:fontWeight 400, :fontSize "1.75rem"}
                 :h4 {:fontWeight 500, :fontSize "1.5rem"}
                 :h5 {:fontWeight 500, :fontSize "1.25rem"}
                 :h6 {:fontWeight 500, :fontSize "1rem"}
                 :subtitle1 {:fontSize "1rem", :fontWeight 400}
                 :subtitle2 {:fontSize "0.875rem", :fontWeight 500}
                 :body1 {:fontSize "1rem"}
                 :body2 {:fontSize "0.875rem"}}

    :components {:MuiTypography {:styleOverrides {;; Define default styles for each variant
                                                  :h2 {:fontWeight 400, :fontSize "2rem"}
                                                  :subtitle1 {:fontSize "1rem", :fontWeight 400}
                                                  :body1 {:fontSize "1rem"}
                                                  :body2 {:fontSize "0.875rem"}}}
                 :MuiPaper {:styleOverrides
                            {:root {:boxShadow "0px 3px 15px rgba(0,0,0,0.05)"}}}
                 :MuiButton {:styleOverrides
                             {:root {:textTransform "none"
                                     :fontWeight 500
                                     :borderRadius "8px"}
                              :contained {:boxShadow "none"}
                              :outlined {:borderWidth "1.5px"}}}

                 ;; Updated form control styles for more compact layout
                 :MuiFormControl {:styleOverrides
                                  {:root {:marginBottom "8px"}}} ;; Reduced from 12px

                 :MuiInputLabel {:styleOverrides
                                 {:root {:fontSize "0.9rem"
                                         :transform "translate(14px, 10px) scale(1)"}}} ;; Adjusted label position

                 :MuiOutlinedInput {:styleOverrides
                                    {:root {:fontSize "0.9rem"
                                            :borderRadius "8px"}
                                     :input {:padding "8px 14px"}}} ;; Reduced padding from 10px to 8px

                 :MuiSelect {:styleOverrides
                             {:root {:fontSize "0.9rem"}}}

                 :MuiMenuItem {:styleOverrides
                               {:root {:fontSize "0.9rem"
                                       :minHeight "30px"}}} ;; Reduced from 35px

                 :MuiFormHelperText {:styleOverrides
                                     {:root {:marginTop "0"
                                             :marginBottom "0"
                                             :fontSize "0.7rem"}}} ;; Reduced font size and added marginBottom

                 :MuiTableCell {:styleOverrides
                                {:root {:padding "12px 16px"}
                                 :head {:fontWeight 600
                                        :backgroundColor "rgba(114,47,55,0.05)"}}}
                 :MuiChip {:styleOverrides
                           {:root {:borderRadius "4px"}
                            :filled {:backgroundColor "rgba(114,47,55,0.1)"}}}
                 :MuiCard {:styleOverrides
                           {:root {:borderRadius "12px"
                                   :overflow "hidden"}}}}
    :shape {:borderRadius 8}}))

