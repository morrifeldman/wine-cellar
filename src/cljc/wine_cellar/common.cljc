(ns wine-cellar.common)

(def wine-styles
  #{"Red" "White" "Rosé" "Sparkling" "Fortified" "Orange" "Dessert"})

(def wine-levels
  #{"Gran Reserva" "Gran Selezione" "Reserva" "Reserve" "Riserva"})

;; Location validation
(defn valid-location? [location]
  (and (string? location)
       (re-matches #"^[A-Z]\d+$" location)))

(defn format-location-error []
  "Location must be an uppercase letter followed by a number (e.g., A1, B2, C10)")
