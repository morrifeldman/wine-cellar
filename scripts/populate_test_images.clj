(ns populate-test-images
  (:require [wine-cellar.db.api :as db]
            [clojure.string :as str])
  (:import [java.util Base64]))

(defn encode-svg [svg-string]
  (let [encoder (Base64/getEncoder)
        bytes (.getBytes svg-string "UTF-8")])
    (str "data:image/svg+xml;base64," (.encodeToString encoder bytes))))

(def red-wine-svg
  "<svg width=\"400\" height=\"600\" xmlns=\"http://www.w3.org/2000/svg\">
     <rect width=\"100%\" height=\"100%\" fill=\"#722f37\"/>
     <rect x=\"20\" y=\"150\" width=\"360\" height=\"300\" fill=\"#fffdf5\" rx=\"10\"/>
     <text x=\"200\" y=\"280\" font-family=\"Georgia, serif\" font-size=\"48\" fill=\"#333\" text-anchor=\"middle\">Red Wine</text>
     <circle cx=\"200\" cy=\"450\" r=\"40\" fill=\"#722f37\" opacity=\"0.2\"/>
   </svg>")

(def white-wine-svg
  "<svg width=\"400\" height=\"600\" xmlns=\"http://www.w3.org/2000/svg\">
     <rect width=\"100%\" height=\"100%\" fill=\"#f9e8c0\"/>
     <rect x=\"20\" y=\"150\" width=\"360\" height=\"300\" fill=\"#ffffff\" rx=\"10\"/>
     <text x=\"200\" y=\"280\" font-family=\"Georgia, serif\" font-size=\"48\" fill=\"#555\" text-anchor=\"middle\">White Wine</text>
     <circle cx=\"200\" cy=\"450\" r=\"40\" fill=\"#f2d16d\" opacity=\"0.3\"/>
   </svg>")

(def rose-wine-svg
  "<svg width=\"400\" height=\"600\" xmlns=\"http://www.w3.org/2000/svg\">
     <rect width=\"100%\" height=\"100%\" fill=\"#fadadd\"/>
     <rect x=\"20\" y=\"150\" width=\"360\" height=\"300\" fill=\"#fff0f5\" rx=\"10\"/>
     <text x=\"200\" y=\"280\" font-family=\"Georgia, serif\" font-size=\"48\" fill=\"#884444\" text-anchor=\"middle\">Rosé</text>
     <path d=\"M200,350 Q230,350 240,380 T200,410 T160,380 T200,350\" fill=\"#e68a96\" opacity=\"0.4\"/>
   </svg>")

(def sparkling-wine-svg
  "<svg width=\"400\" height=\"600\" xmlns=\"http://www.w3.org/2000/svg\">
     <rect width=\"100%\" height=\"100%\" fill=\"#fdfcdc\"/>
     <rect x=\"20\" y=\"150\" width=\"360\" height=\"300\" fill=\"#fff\" border=\"1\"/>
     <text x=\"200\" y=\"280\" font-family=\"Georgia, serif\" font-size=\"40\" fill=\"#444\" text-anchor=\"middle\">Sparkling</text>
     <circle cx=\"150\" cy=\"400\" r=\"5\" fill=\"#d4af37\"/>
     <circle cx=\"180\" cy=\"380\" r=\"8\" fill=\"#d4af37\"/>
     <circle cx=\"220\" cy=\"420\" r=\"6\" fill=\"#d4af37\"/>
     <circle cx=\"250\" cy=\"390\" r=\"4\" fill=\"#d4af37\"/>
   </svg>")

(defn get-image-for-style [style]
  (case (and style (str/lower-case style))
    "red" (encode-svg red-wine-svg)
    "white" (encode-svg white-wine-svg)
    "rose" (encode-svg rose-wine-svg)
    "rosé" (encode-svg rose-wine-svg)
    "sparkling" (encode-svg sparkling-wine-svg)
    "champagne" (encode-svg sparkling-wine-svg)
    (encode-svg red-wine-svg))) ;; Default

(defn run []
  (let [wines (db/get-wines-for-list)]
    (println "Found" (count wines) "wines to update.")
    (doseq [wine wines]
      (let [style (:style wine)
            image (get-image-for-style style)]
        (println "Updating" (:id wine) " (" (:name wine) ") with style" style)
        (db/update-wine!
         (:id wine)
         {:label_image image
          :label_thumbnail image})))
    (println "Done!")))

(run)
