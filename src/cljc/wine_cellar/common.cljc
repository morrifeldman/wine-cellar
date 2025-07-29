(ns wine-cellar.common)

(def wine-styles
  #{"Red" "White" "Rosé" "Sparkling" "Rosé Sparkling" "Fortified" "Orange"
    "Dessert"})

(def wine-levels
  #{"Joven" "Crianza" "Reserva" "Gran Reserva" ; Rioja
    "Riserva" "Gran Selezione" ; Chianti
    "Reserve"})

;; Location validation
(defn valid-location?
  [location]
  (or (nil? location)
      (and (string? location) (re-matches #"^[A-Z]\d+$" location))))

(def format-location-error
  "Location must be an uppercase letter followed by a number (e.g., A1, B2, C10) or empty for restaurants/tastings")

;; WSET Level 3 Lexicon
(def wset-lexicon
  {:primary
   {:floral ["ACACIA" "HONEYSUCKLE" "CHAMOMILE" "ELDERFLOWER" "GERANIUM"
             "BLOSSOM" "ROSE" "VIOLET"]
    :green-fruit ["APPLE" "GOOSEBERRY" "PEAR" "PEAR DROP" "QUINCE" "GRAPE"]
    :citrus-fruit ["GRAPEFRUIT" "LEMON" "LIME (JUICE OR ZEST?)" "ORANGE PEEL"
                   "LEMON PEEL"]
    :stone-fruit ["PEACH" "APRICOT" "NECTARINE"]
    :tropical-fruit ["BANANA" "LYCHEE" "MANGO" "MELON" "PASSION FRUIT"
                     "PINEAPPLE"]
    :red-fruit ["REDCURRANT" "CRANBERRY" "RASPBERRY" "STRAWBERRY" "RED CHERRY"
                "RED PLUM"]
    :black-fruit ["BLACKCURRANT" "BLACKBERRY" "BRAMBLE" "BLUEBERRY"
                  "BLACK CHERRY" "BLACK PLUM"]
    :dried-cooked-fruit ["FIG" "PRUNE" "RAISIN" "SULTANA" "KIRSCH" "JAMMINESS"
                         "BAKED/STEWED FRUITS" "PRESERVED FRUITS"]
    :herbaceous ["GREEN BELL PEPPER (CAPSICUM)" "GRASS" "TOMATO LEAF"
                 "ASPARAGUS" "BLACKCURRANT LEAF"]
    :herbal ["EUCALYPTUS" "MINT" "MEDICINAL" "LAVENDER" "FENNEL" "DILL"]
    :pungent-spice ["BLACK/WHITE PEPPER" "LIQUORICE"]
    :other ["FLINT" "WET STONES" "WET WOOL"]}
   :secondary
   {:yeast ["BISCUIT" "BREAD" "TOAST" "PASTRY" "BRIOCHE" "BREAD DOUGH" "CHEESE"]
    :mlf ["BUTTER" "CHEESE" "CREAM"]
    :oak ["VANILLA" "CLOVES" "NUTMEG" "COCONUT" "BUTTERSCOTCH" "TOAST" "CEDAR"
          "CHARRED WOOD" "SMOKE" "CHOCOLATE" "COFFEE" "RESINOUS"]}
   :tertiary
   {:deliberate-oxidation ["ALMOND" "MARZIPAN" "HAZELNUT" "WALNUT" "CHOCOLATE"
                           "COFFEE" "TOFFEE" "CARAMEL"]
    :fruit-development ["DRIED APRICOT" "MARMALADE" "DRIED APPLE" "DRIED BANANA"
                        "FIG" "PRUNE" "TAR" "DRIED BLACKBERRY" "DRIED CRANBERRY"
                        "COOKED BLACKBERRY" "COOKED RED PLUM"]
    :bottle-age ["PETROL" "KEROSENE" "CINNAMON" "GINGER" "NUTMEG" "TOAST"
                 "NUTTY" "MUSHROOM" "HAY" "HONEY" "LEATHER" "EARTH"
                 "FOREST FLOOR" "MUSHROOM" "GAME" "TOBACCO" "TAR" "SMOKE"]}
   ;; WSET Level 3 Enums - using ALL CAPS for controlled vocabulary
   :enums
   {:wine-styles ["WHITE" "ROSE" "RED" "SPARKLING" "FORTIFIED"]
    :clarity ["CLEAR" "HAZY"]
    :intensity ["PALE" "MEDIUM" "DEEP"]
    :nose-intensity ["LIGHT" "MEDIUM-" "MEDIUM" "MEDIUM+" "PRONOUNCED"]
    :development ["YOUTHFUL" "DEVELOPING" "FULLY DEVELOPED"
                  "TIRED/PAST ITS BEST"]
    :condition ["CLEAN" "UNCLEAN"]
    :sweetness ["DRY" "OFF-DRY" "MEDIUM-DRY" "MEDIUM-SWEET" "SWEET" "LUSCIOUS"]
    :acidity ["LOW" "MEDIUM-" "MEDIUM" "MEDIUM+" "HIGH"]
    :tannin ["LOW" "MEDIUM-" "MEDIUM" "MEDIUM+" "HIGH"]
    :alcohol ["LOW" "MEDIUM-" "MEDIUM" "MEDIUM+" "HIGH"]
    :body ["LIGHT" "MEDIUM-" "MEDIUM" "MEDIUM+" "FULL"]
    :mousse ["DELICATE" "CREAMY" "AGGRESSIVE"]
    :flavour-intensity ["LIGHT" "MEDIUM-" "MEDIUM" "MEDIUM+" "PRONOUNCED"]
    :finish ["SHORT" "MEDIUM-" "MEDIUM" "MEDIUM+" "LONG"]
    :quality-level ["FAULTY" "POOR" "ACCEPTABLE" "GOOD" "VERY GOOD"
                    "OUTSTANDING"]
    :readiness ["TOO YOUNG" "DRINK OR HOLD" "DRINK" "TOO OLD"]}
   ;; Wine Style Color Options
   :colors {:white ["LEMON-GREEN" "LEMON" "GOLD" "AMBER" "BROWN"]
            :rose ["PINK" "SALMON" "ORANGE"]
            :red ["PURPLE" "RUBY" "GARNET" "TAWNY" "BROWN"]
            :sparkling ["LEMON-GREEN" "LEMON" "GOLD" "PINK" "SALMON"]
            :fortified ["LEMON" "GOLD" "AMBER" "BROWN" "RUBY" "GARNET"
                        "TAWNY"]}})
