(ns wine-cellar.theme
  (:require [reagent-mui.styles :refer [create-theme]]))

(def wine-theme
  (create-theme
   {:palette {:primary {:main "#E8C3C8" ;; Lighter burgundy for primary on
                                        ;; dark background
                        :light "#F5D6DB"
                        :dark "#B89095"}
              :secondary {:main "#A8B47D" ;; Brighter olive green for
                                          ;; contrast
                          :light "#C6D19B"
                          :dark "#8A9A5B"}
              :text {:primary "#F5F5F5" ;; Light text for dark background
                     :secondary "#D0D0D0" ;; Slightly darker for secondary
                                          ;; text
                     :disabled "#606060"} ;; Much more muted gray for
                                          ;; disabled text
              :background {:default "#150A0C" ;; Even deeper burgundy/wine
                                              ;; background (like aged wine
                                              ;; in a dark cellar)
                           :paper "#3A1F23"} ;; Light wine color for all
                                             ;; cards
                                             ;; (consistent card
                                             ;; background)
              :wineCard {:main "#3A1F23"} ;; Same as paper for consistency
              :container {:main "#2A151A"} ;; Intermediate wine color for
                                           ;; major containers
              :success {:main "#2E7D32"}
              :error {:main "#B71C1C"}
              :rating {:high "#E8C3C8" ;; Lighter wine color for high
                                       ;; ratings on dark background
                       :medium "#FFC107" ;; Brighter amber for medium
                       :low "#BDBDBD"} ;; Lighter gray for low
              :tasting-window {:ready "#2E7D32" ;; Green for ready to
                                                ;; drink
                               :young "#1976D2" ;; Blue for too young
                               :past "#FF9800"}} ;; Orange for past prime
    ;; Typography improvements - more compact
    :typography {:fontFamily
                 "'Raleway', 'Roboto', 'Helvetica', 'Arial', sans-serif"
                 :h1 {:fontWeight 300 :fontSize "2.25rem"}
                 :h2 {:fontWeight 400 :fontSize "1.75rem"}
                 :h3 {:fontWeight 400 :fontSize "1.5rem"}
                 :h4 {:fontWeight 500 :fontSize "1.25rem"}
                 :h5 {:fontWeight 500 :fontSize "1.1rem"}
                 :h6 {:fontWeight 500 :fontSize "1rem"}
                 :subtitle1 {:fontSize "0.95rem" :fontWeight 500}
                 :subtitle2 {:fontSize "0.85rem" :fontWeight 500}
                 :body1 {:fontSize "0.95rem"}
                 :body2 {:fontSize "0.85rem"}}
    :components
    {:MuiTypography {:styleOverrides
                     {;; Define default styles for each variant
                      :h2 {:fontWeight 400 :fontSize "1.75rem"}
                      :h4 {:fontWeight 500 :fontSize "1.25rem" :color "#E8C3C8"}
                      :subtitle1 {:fontSize "0.95rem" :fontWeight 500}
                      :body1 {:fontSize "0.95rem"}
                      :body2 {:fontSize "0.85rem"}}}
     :MuiPaper {:styleOverrides {:root {:boxShadow
                                        "0px 3px 15px rgba(0,0,0,0.3)"
                                        :color "#F5F5F5"}}}
     :MuiButton {:styleOverrides {:root {:textTransform "none"
                                         :fontWeight 500
                                         :borderRadius "8px"
                                         "&.Mui-disabled"
                                         {:color "text.disabled"
                                          :borderColor "text.disabled"}}
                                  :contained {:boxShadow "none"}
                                  :outlined {:borderWidth "1.5px"}}}
     ;; More compact form controls
     :MuiFormControl {:styleOverrides {:root {:marginBottom "8px"}}} ;; Reduced
                                                                     ;; from
                                                                     ;; 12px
     :MuiInputLabel {:styleOverrides {:root {:fontSize "0.85rem"}}} ;; Smaller
                                                                    ;; font
     :MuiOutlinedInput {:styleOverrides {:root {:fontSize "0.85rem"
                                                :borderRadius "6px"}
                                         :input {:padding "8px 12px"}}} ;; Reduced
                                                                        ;; padding
     ;; Disabled field styling for better readability
     :MuiInputBase {:styleOverrides
                    {:root {"&.Mui-disabled" {:color "text.disabled"}
                            "&.Mui-disabled input" {:color "text.disabled"}}}}
     :MuiAutocomplete
     {:styleOverrides
      {:root {"&.Mui-disabled" {:color "text.disabled"}
              "&.Mui-disabled .MuiInputBase-input" {:color "text.disabled"}}
       :clearIndicator {:visibility "visible" :color "#D0D0D0"}}}
     :MuiFormLabel {:styleOverrides {:root {"&.Mui-disabled"
                                            {:color "text.secondary"}}}}
     :MuiSelect {:styleOverrides {:root {:fontSize "0.85rem"}}}
     :MuiMenuItem {:styleOverrides {:root {:fontSize "0.85rem"
                                           :minHeight "32px"}}} ;; Reduced
                                                                ;; height
     :MuiFormHelperText {:styleOverrides {:root {:marginTop "0"
                                                 :fontSize "0.7rem"
                                                 :lineHeight "1.2"}}} ;; Smaller
                                                                      ;; helper
                                                                      ;; text
     :MuiTableCell {:styleOverrides {:root {:padding "8px 12px"} ;; Reduced
                                                                 ;; padding
                                     :head {:fontWeight 600
                                            :backgroundColor
                                            "rgba(114,47,55,0.2)"}}}
     :MuiChip {:styleOverrides {:root {:borderRadius "4px"}
                                :filled {:backgroundColor
                                         "rgba(232,195,200,0.2)"}}}
     :MuiCard {:styleOverrides {:root {:borderRadius "8px" ;; Reduced from
                                                           ;; 12px
                                       :overflow "hidden"}}}}
    :shape {:borderRadius 6}})) ;; Reduced from 8
