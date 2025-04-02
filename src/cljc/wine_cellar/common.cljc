(ns wine-cellar.common)

(def wine-styles
  #{"Red"
    "White"
    "Rosé"
    "Sparkling"
    "Rosé Sparkling"
    "Fortified"
    "Orange"
    "Dessert"})

(def wine-levels
  #{"Joven" "Crianza" "Reserva" "Gran Reserva" ; Rioja
     "Riserva" "Gran Selezione" ; Chianti
     "Reserve"})

;; Location validation
(defn valid-location? [location]
  (and (string? location)
       (re-matches #"^[A-Z]\d+$" location)))

(def format-location-error
  "Location must be an uppercase letter followed by a number (e.g., A1, B2, C10)")
