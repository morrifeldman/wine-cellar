(ns wine-cellar.common
  (:require [clojure.string :as str]))

(def ai-providers #{:anthropic :openai :gemini})

(defn provider-label
  "Get display label for provider"
  [provider]
  (case provider
    :openai "OpenAI"
    :anthropic "Anthropic"
    :gemini "Gemini"
    nil "..."))

(def wine-styles
  #{"Red" "White" "Rosé" "Sparkling" "Rosé Sparkling" "Red Sparkling"
    "Fortified" "Orange" "Dessert"})

(def ^:private default-style-key "red")

(def ^:private style-metadata
  {"red" {:canonical "Red"
          :palette :red
          :wset-style "RED"
          :default-color :garnet
          :default-intensity :medium}
   "white" {:canonical "White"
            :palette :white
            :wset-style "WHITE"
            :default-color :amber
            :default-intensity :medium}
   "rosé" {:canonical "Rosé"
           :palette :rose
           :wset-style "ROSE"
           :default-color :copper
           :default-intensity :medium}
   "rose" {:canonical "Rosé"
           :palette :rose
           :wset-style "ROSE"
           :default-color :copper
           :default-intensity :medium}
   "sparkling" {:canonical "Sparkling"
                :palette :white
                :wset-style "SPARKLING"
                :default-color :amber
                :default-intensity :medium}
   "red sparkling" {:canonical "Red Sparkling"
                    :palette :red
                    :wset-style "SPARKLING"
                    :default-color :garnet
                    :default-intensity :medium}
   "rosé sparkling" {:canonical "Rosé Sparkling"
                     :palette :rose
                     :wset-style "SPARKLING"
                     :default-color :copper
                     :default-intensity :medium}
   "rose sparkling" {:canonical "Rosé Sparkling"
                     :palette :rose
                     :wset-style "SPARKLING"
                     :default-color :copper
                     :default-intensity :medium}
   "fortified" {:canonical "Fortified"
                :palette :red
                :wset-style "FORTIFIED"
                :default-color :garnet
                :default-intensity :medium}
   "orange" {:canonical "Orange"
             :palette :white
             :wset-style "WHITE"
             :default-color :amber
             :default-intensity :medium}
   "dessert" {:canonical "Dessert"
              :palette :white
              :wset-style "WHITE"
              :default-color :amber
              :default-intensity :medium}})

(defn normalize-style-key
  "Normalize a wine style string for lookup."
  [style]
  (when style
    (-> style
        str/trim
        str/lower-case
        (str/replace #"\s+" " "))))

(defn humanize-key
  "Convert a keyword or string key into a human-readable Title Case string.
   e.g. :total-acidity -> \"Total Acidity\""
  [k]
  (-> (name k)
      (str/replace #"[-_]" " ")
      (str/replace #"\b\w" str/upper-case)))

(defn style->info
  "Return metadata for a wine style, including canonical label, palette category, default color and WSET style code. Falls back to red when style is unknown."
  [style]
  (let [style-key (normalize-style-key style)
        info (or (get style-metadata style-key)
                 (get style-metadata default-style-key))]
    (if info
      (cond-> info
        (and (not (get style-metadata style-key)) style)
        (assoc :canonical (or style (:canonical info))))
      (get style-metadata default-style-key))))

(def wine-levels
  #{"Joven" "Crianza" "Reserva" "Gran Reserva" ; Rioja
    "Riserva" "Gran Selezione" ; Chianti
    "Reserve"})

(def bottle-formats
  ["Standard (750ml)" "Half Bottle (375ml)" "Magnum (1.5L)" "Split (187.5ml)"
   "Jeroboam (3L)" "Double Magnum (3L)" "Imperial (6L)" "Salmanazar (9L)"
   "Balthazar (12L)" "Nebuchadnezzar (15L)"])

(def closure-type-options
  ["Natural cork" "Technical cork" "Micro-agglomerated cork" "Colmated cork"
   "Agglomerated cork" "Cork (unspecified)" "Screw cap"
   "Synthetic cork (extruded)" "Synthetic cork (molded)" "Glass stopper"
   "Crown cap" "T-top" "Zork" "Other/Unknown"])

(def closure-types (set closure-type-options))

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
    :readiness ["TOO YOUNG" "DRINK OR HOLD" "DRINK" "TOO OLD"]}})

(def technical-data-keys
  [:soil :vine-age :harvest :fermentation :aging :elevage :food-pairings :notes
   :vintner-notes :cooperage :oak :new-oak :yeast :malo-lactic :fining
   :filtration :ph :total-acidity :residual-sugar :base-vintage :reserve-wines
   :bottling-date :release-date :production-volume :certification])
