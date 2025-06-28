(ns vivino-process
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [wine-cellar.ai.anthropic :as ai]
            [org.httpkit.client :as http]
            [jsonista.core :as json])
  (:import [java.util Base64]
           [java.net URL]
           [javax.imageio ImageIO]
           [java.awt.image BufferedImage]
           [java.awt RenderingHints]))

;; Utility functions
(defn parse-csv
  "Parse CSV file and return maps with header keys"
  [filename]
  (with-open [reader (io/reader filename)]
    (let [data (csv/read-csv reader)
          headers (map #(-> %
                            (str/replace #"\s+" "-")
                            (str/lower-case)
                            keyword)
                       (first data))
          rows (rest data)]
      (mapv #(zipmap headers %) rows))))

(defn has-tasting-content?
  "Check if cleaned text qualifies as substantial tasting content"
  [cleaned-text]
  (and (seq cleaned-text)
       (> (count cleaned-text) 3) ; Basic minimum length
       (not (re-matches #"^[A-Z]{1,3}\d+\s*$" cleaned-text)) ; Not just wine
                                                             ; codes like
                                                             ; "B6"
       (not (re-matches #"^\s*$" cleaned-text))))

(defn clean-note-text
  "Clean note text by removing structured data (prices, locations, ratings, etc.)"
  [text]
  (-> text
      (str/replace #"\$\d+(?:\.\d{2})?" "")
      (str/replace #"\b[A-Za-z]\d+\b" "")
      (str/replace #"\b\d{1,2}/\d{1,2}/\d{2,4}:?\s*" "")
      (str/replace #"\b\d{1,2}/\d{1,2}:?\s*" "")
      (str/replace #"WE\s+\d+" "")
      (str/replace #"WS\s+\d+" "")
      (str/replace #"RP\s+\d+" "")
      (str/replace #"JD\s+\d+" "")
      (str/replace #"\d+\s*Decanter\s*World\s*Wine\s*Awards" "")
      (str/replace #"\([A-Z]{1,3}\)" "")
      ;; Remove bottle reference phrases from note text
      (str/replace #"(?i)\b(from\s+)?(last|first|second|third|final)\s+bottle\b"
                   "")
      (str/replace #",\s*" " ")
      (str/replace #"\s+" " ")
      str/trim))

(defn parse-date-separated-notes
  "Split personal note by dates and extract individual tasting experiences"
  [note]
  (when (and note (not (str/blank? note)))
    (let [;; Split by date patterns like "2/13/25:" or "7/17/24:"
          date-splits (str/split note #"(?=\d{1,2}/\d{1,2}/\d{2,4}:)")
          ;; Filter out empty strings and process each section
          note-sections (filter #(not (str/blank? %)) date-splits)]
      (map (fn [section]
             (let [date-match (re-find #"^(\d{1,2}/\d{1,2}/\d{2,4}):\s*(.+)"
                                       section)
                   date (when date-match (second date-match))
                   text (if date-match (nth date-match 2) (str/trim section))]
               {:date date :text (str/trim text)}))
           note-sections))))

(defn parse-personal-note
  "Extract price, location, ratings, and structured tasting info from personal note"
  [note]
  (when (and note (not (str/blank? note)))
    (let [price-match (re-find #"\$(\d+(?:\.\d{2})?)" note)
          location-match (re-find #"\b([A-Za-z]\d+)\b" note)
          date-match (re-find #"\b(\d{1,2}/\d{1,2}/\d{2,4}|\d{1,2}/\d{1,2})\b"
                              note)
          ;; Extract professional ratings and sources
          we-match (re-find #"WE\s+(\d+)" note) ; Wine Enthusiast
          ws-match (re-find #"WS\s+(\d+)" note) ; Wine Spectator
          rp-match (re-find #"RP\s+(\d+)" note) ; Robert Parker
          jd-match (re-find #"JD\s+(\d+)" note) ; James Dunnuck
          decanter-match (re-find #"(\d+)\s*Decanter" note) ; Decanter World
                                                            ; Wine Awards
          reviewer-match (re-find #"\(([A-Z]{1,3})\)" note) ; Reviewer
                                                            ; initials like
                                                            ; (VB)
          ;; Extract structured information
          price (when price-match (parse-double (second price-match)))
          location (when location-match
                     (str/upper-case (second location-match)))
          tasting-date (second date-match)
          ;; Professional rating extraction
          professional-rating (or (when we-match (parse-long (second we-match)))
                                  (when ws-match (parse-long (second ws-match)))
                                  (when rp-match (parse-long (second rp-match)))
                                  (when jd-match (parse-long (second jd-match)))
                                  (when decanter-match
                                    (parse-long (second decanter-match))))
          professional-source (cond we-match "Wine Enthusiast"
                                    ws-match "Wine Spectator"
                                    rp-match "Robert Parker"
                                    jd-match "James Dunnuck"
                                    decanter-match "Decanter"
                                    :else nil)
          reviewer-initials (second reviewer-match)
          ;; Parse multiple date-separated notes
          date-separated-notes (parse-date-separated-notes note)
          ;; Clean note by removing extracted structured data
          cleaned-note (clean-note-text note)
          ;; Check if remaining text has substantial tasting content
          tasting-notes (when (has-tasting-content? cleaned-note) cleaned-note)]
      {:price price
       :location location
       :tasting-date tasting-date
       :professional-rating professional-rating
       :professional-source professional-source
       :reviewer-initials reviewer-initials
       :tasting-notes tasting-notes
       :date-separated-notes date-separated-notes
       :note cleaned-note ; Keep full cleaned note for reference
      })))

(defn create-tasting-note-classification-prompt
  "Creates a prompt for classifying tasting notes as personal or external"
  [tasting-text]
  (str
   "Classify this wine tasting note as either PERSONAL or EXTERNAL:\n\n"
   "Note: \"" tasting-text
   "\"\n\n"
   "PERSONAL = written by the wine owner (personal pronouns like 'I/we', food pairings with specific meals, "
   "personal experiences, future plans like 'will try again', casual conversational tone)\n\n"
   "EXTERNAL = professional review or critic note (formal wine terminology, publication names, "
   "reviewer names, numerical scores, structured tasting format, third-person professional tone)\n\n"
   "If EXTERNAL, also extract:\n"
   "- Publication: Wine Enthusiast, Wine Spectator, Robert Parker, James Dunnuck, Decanter, etc.\n"
   "- Reviewer: critic name if mentioned (e.g., 'Kerin O'Keefe', 'James Suckling')\n"
   "- Score: numerical rating if mentioned (e.g., '89', '95', '4.2')\n\n"
   "Return ONLY a valid JSON object with these fields:\n"
   "{\"type\": \"PERSONAL|EXTERNAL\", \"publication\": \"...\", \"reviewer\": \"...\", \"score\": \"...\"}\n\n"
   "Use null for missing fields. Return only the JSON, no explanatory text."))

(def haiku "claude-3-5-haiku-20241022")

(defn classify-with-haiku
  "Use Haiku model for fast, cheap text classification"
  [prompt-text]
  (let [content [{:type "text" :text prompt-text}]]
    (ai/call-anthropic-api content true haiku)))

(defn classify-tasting-note-with-ai
  "Classifies a tasting note as personal or external using Haiku model.
   Returns a map with :type, :publication, :reviewer, :score"
  [tasting-text]
  (when (and tasting-text (not (str/blank? tasting-text)))
    (try (let [prompt (create-tasting-note-classification-prompt tasting-text)]
           (println "  -> Classifying with AI:"
                    (subs tasting-text 0 (min 50 (count tasting-text)))
                    "...")
           (classify-with-haiku prompt))
         (catch Exception e
           (println "  -> AI classification failed:" (.getMessage e))
           {:type "PERSONAL" :publication nil :reviewer nil :score nil}))))

(defn create-tasting-note
  "Creates a tasting note from raw text, applying cleaning and classification logic.
   Returns nil if text doesn't qualify as substantial tasting content."
  [raw-text tasting-date professional-rating professional-source
   reviewer-initials vivino-rating]
  (let [;; Clean the raw text
        cleaned-text (clean-note-text raw-text)
        ;; Check if cleaned text has substantial content
        has-content? (has-tasting-content? cleaned-text)]
    (when has-content?
      (let [;; Use professional rating/source if available, otherwise AI
            has-professional-data? (or professional-rating professional-source)
            ai-classification (when (not has-professional-data?)
                                (classify-tasting-note-with-ai cleaned-text))
            ;; Determine if truly external based on having actual external
            ;; source
            ai-is-external? (= (:type ai-classification) "EXTERNAL")
            ai-has-publication? (and ai-is-external?
                                     (not (str/blank? (:publication
                                                       ai-classification))))
            is-external? (or (boolean has-professional-data?)
                             (boolean ai-has-publication?))
            source-name (cond professional-source professional-source
                              ai-has-publication? (:publication
                                                   ai-classification)
                              :else nil)
            reviewer (or reviewer-initials
                         (when is-external? (:reviewer ai-classification)))
            final-rating (or professional-rating vivino-rating)]
        {:note cleaned-text
         :rating final-rating
         :tasting_date tasting-date
         :is_external is-external?
         :source (when is-external?
                   (str source-name
                        (when reviewer (str " (" reviewer ")"))))}))))

(defn map-wine-style
  "Map Vivino wine types to our app's styles"
  [vivino-type]
  (case vivino-type
    "Red Wine" "Red"
    "White Wine" "White"
    "Rosé Wine" "Rosé"
    "Sparkling" "Sparkling"
    "Dessert Wine" "Dessert"
    "Fortified Wine" "Fortified"
    ;; Default fallback
    (or vivino-type "Red")))

(defn convert-vivino-rating
  "Convert Vivino 5-star rating (1.0-5.0) to 100-point scale"
  [vivino-rating]
  (when vivino-rating
    (let [rating (if (string? vivino-rating)
                   (parse-double vivino-rating)
                   vivino-rating)]
      (when rating ; Only proceed if we have a valid numeric rating
        (cond (>= rating 4.8) (+ 95 (* (- rating 4.8) 25)) ; 4.8-5.0 → 95-100
              (>= rating 4.5) (+ 90 (* (- rating 4.5) 16.7)) ; 4.5-4.8 →
                                                             ; 90-95
              (>= rating 4.0) (+ 85 (* (- rating 4.0) 10))   ; 4.0-4.5 →
                                                             ; 85-90
              (>= rating 3.5) (+ 80 (* (- rating 3.5) 10))   ; 3.5-4.0 →
                                                             ; 80-85
              (>= rating 3.0) (+ 75 (* (- rating 3.0) 10))   ; 3.0-3.5 →
                                                             ; 75-80
              (>= rating 2.5) (+ 70 (* (- rating 2.5) 10))   ; 2.5-3.0 →
                                                             ; 70-75
              (>= rating 2.0) (+ 65 (* (- rating 2.0) 10))   ; 2.0-2.5 →
                                                             ; 65-70
              :else (+ 60 (* (- rating 1.0) 5)) ; 1.0-2.0 → 60-65
        )))))

(defn create-thumbnail
  "Create high-quality thumbnail from BufferedImage, maintaining aspect ratio"
  [buffered-image max-size]
  (let [width (.getWidth buffered-image)
        height (.getHeight buffered-image)
        scale (min (/ max-size width) (/ max-size height))
        new-width (int (* width scale))
        new-height (int (* height scale))
        ;; Use TYPE_INT_RGB for JPEG compatibility (no transparency)
        thumbnail
        (BufferedImage. new-width new-height BufferedImage/TYPE_INT_RGB)
        g2d (.createGraphics thumbnail)]
    ;; Set high-quality rendering hints
    (.setRenderingHint g2d
                       RenderingHints/KEY_INTERPOLATION
                       RenderingHints/VALUE_INTERPOLATION_BICUBIC)
    (.setRenderingHint g2d
                       RenderingHints/KEY_RENDERING
                       RenderingHints/VALUE_RENDER_QUALITY)
    (.setRenderingHint g2d
                       RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_ON)
    (.setRenderingHint g2d
                       RenderingHints/KEY_COLOR_RENDERING
                       RenderingHints/VALUE_COLOR_RENDER_QUALITY)
    ;; Fill with white background first (important for JPEG)
    (.setColor g2d java.awt.Color/WHITE)
    (.fillRect g2d 0 0 new-width new-height)
    (.drawImage g2d buffered-image 0 0 new-width new-height nil)
    (.dispose g2d)
    thumbnail))

(defn save-image-files
  "Download image and save full image file only"
  [url wine-index images-dir]
  (try (when (and url (not (str/blank? url)))
         (println "  -> Downloading image from" url "for wine" wine-index)
         (let [image (ImageIO/read (URL. url))
               ;; Create filename for full image only
               full-filename
               (str "wine_" (format "%03d" wine-index) "_full.jpg")
               full-path (str images-dir "/" full-filename)]
           ;; Save full image only
           (ImageIO/write image "jpg" (io/file full-path))
           {:image_file full-filename :full_path full-path}))
       (catch Exception e
         (println "  -> Failed to process image from" url ":" (.getMessage e))
         nil)))

(defn process-vivino-wine
  "Convert Vivino wine data to our app format"
  [wine-data cellar-data price-data wine-index images-dir]
  (let [winery (:winery wine-data)
        wine-name (:wine-name wine-data)
        vintage-str (:vintage wine-data)
        ;; Parse personal note for price/location
        personal-note (:personal-note wine-data)
        parsed-note (parse-personal-note personal-note)
        ;; Extract purveyor/bottle info from personal note
        purveyor-match (re-find
                        #"(?i)\b(last|first|second|third|final)\s+bottle\b"
                        personal-note)
        purveyor (when purveyor-match
                   (let [bottle-type (str/lower-case (str/trim
                                                      (second purveyor-match)))]
                     (str (str/capitalize bottle-type) " Bottle")))
        ;; Look up quantity from cellar data
        cellar-entry (first (filter #(and (= (:winery %) winery)
                                          (= (:wine-name %) wine-name)
                                          (= (:vintage %) vintage-str))
                                    cellar-data))
        quantity (if cellar-entry
                   (or (parse-long (:user-cellar-count cellar-entry)) 0)
                   0)
        ;; Look up price from price data or personal note
        price-entry (first (filter #(and (= (:winery %) winery)
                                         (= (:wine-name %) wine-name)
                                         (= (:vintage %) vintage-str))
                                   price-data))
        price (or (:price parsed-note)
                  (when price-entry
                    (-> (:wine-price price-entry)
                        (str/replace #"USD " "")
                        parse-double))) ; nil if no price found
        ;; Download and save label image files
        label-url (:label-image wine-data)
        image-data (when label-url
                     (save-image-files label-url wine-index images-dir))]
    (merge
     {:producer winery
      :name wine-name
      :vintage (parse-long vintage-str)
      :country (:country wine-data)
      :region (:region wine-data)
      :style (map-wine-style (:wine-type wine-data))
      :location (:location parsed-note) ; nil if no location in personal
                                        ; note
      :quantity quantity
      :price price
      :purveyor purveyor
      ;; Convert Vivino rating and prepare tasting note data
      :vivino-rating-original (:your-rating wine-data)
      :rating (convert-vivino-rating (:your-rating wine-data))
      :tasting-notes
      (let [vivino-rating (convert-vivino-rating (:your-rating wine-data))
            professional-rating (:professional-rating parsed-note)
            professional-source (:professional-source parsed-note)
            reviewer-initials (:reviewer-initials parsed-note)
            date-separated-notes (:date-separated-notes parsed-note)
            ;; Create notes from different sources
            notes-collection []]
        ;; Add review from review field if present
        (let [notes-collection (if (and (:your-review wine-data)
                                        (not (str/blank? (:your-review
                                                          wine-data))))
                                 (if-let [review-note (create-tasting-note
                                                       (:your-review wine-data)
                                                       nil ; no date
                                                       professional-rating
                                                       professional-source
                                                       reviewer-initials
                                                       vivino-rating)]
                                   (conj notes-collection review-note)
                                   notes-collection)
                                 notes-collection)
              ;; Add date-separated notes from personal note
              notes-collection
              (if (seq date-separated-notes)
                (into notes-collection
                      (keep
                       (fn [note-entry]
                         (let [raw-text (:text note-entry)
                               note-date (:date note-entry)
                               ;; Determine rating - use professional if
                               ;; available, otherwise vivino for single
                               ;; notes
                               rating-to-use
                               (cond professional-rating professional-rating
                                     (and (= 1 (count date-separated-notes))
                                          (not professional-rating))
                                     vivino-rating
                                     :else nil)]
                           (create-tasting-note raw-text
                                                note-date
                                                professional-rating
                                                professional-source
                                                reviewer-initials
                                                rating-to-use)))
                       date-separated-notes))
                ;; Fallback to single tasting note if available
                (if (and (:tasting-notes parsed-note)
                         (not (str/blank? (:tasting-notes parsed-note))))
                  (if-let [fallback-note (create-tasting-note
                                          (:tasting-notes parsed-note)
                                          (:tasting-date parsed-note)
                                          professional-rating
                                          professional-source
                                          reviewer-initials
                                          (or professional-rating
                                              vivino-rating))]
                    (conj notes-collection fallback-note)
                    notes-collection)
                  notes-collection))]
          ;; Return the collection of notes, or nil if empty
          (when (seq notes-collection) notes-collection)))
      :vivino-personal-note (:note parsed-note)
      :vivino-url (:link-to-wine wine-data)
      ;; Raw data for reference
      :raw-review (:your-review wine-data)
      :raw-personal-note personal-note
      :raw-wine-type (:wine-type wine-data)}
     ;; Add image data if available
     (when image-data image-data))))

(defn load-vivino-data
  "Load all Vivino CSV files"
  []
  {:wines (parse-csv "vivino_data/full_wine_list.csv")
   :cellar (parse-csv "vivino_data/cellar.csv")
   :prices (parse-csv "vivino_data/user_prices.csv")})

(defn process-sample
  "Process first N wines and save to EDN file"
  [take-n drop-n output-file]
  (let [{:keys [wines cellar prices]} (load-vivino-data)
        sample-wines (take take-n (drop drop-n wines))
        images-dir "vivino_images"]
    ;; Create images directory
    (.mkdirs (io/file images-dir))
    (println "Loading Vivino data...")
    (println "Found"
             (count wines)
             "wines,"
             (count cellar)
             "cellar entries,"
             (count prices)
             "price entries")
    (println "Processing" take-n "wines after dropping" drop-n)
    (println "Images will be saved to:" images-dir)
    (let [processed
          (doall
           (map-indexed
            (fn [index wine-data]
              (println "Processing:"
                       index
                       (:winery wine-data)
                       (:wine-name wine-data))
              (process-vivino-wine wine-data cellar prices index images-dir))
            sample-wines))]
      (println "Saving processed data to" output-file)
      (spit output-file (with-out-str (pprint processed)))
      (println "\n=== PROCESSING SUMMARY ===")
      (println "Total processed:" (count processed))
      (println "With image files:" (count (filter :image_file processed)))
      (println "With thumbnail files:"
               (count (filter :thumbnail_file processed)))
      (println "With ratings:" (count (filter :vivino-rating processed)))
      (println "With reviews:" (count (filter :vivino-review processed)))
      (println "Saved to:" output-file)
      (println "Images saved to:" images-dir)
      processed)))

(defn load-image-as-base64
  "Convert image file back to base64 for database import"
  [image-path]
  (try (when (.exists (io/file image-path))
         (with-open [input (io/input-stream image-path)]
           (.encodeToString (Base64/getEncoder) (.readAllBytes input))))
       (catch Exception e
         (println "Failed to load image" image-path ":" (.getMessage e))
         nil)))

;; Import functions
(defn generate-thumbnail-from-full-image
  "Generate high-quality thumbnail from full image file"
  [full-image-path]
  (when (and full-image-path (.exists (io/file full-image-path)))
    (try (println "    -> Generating thumbnail from:" full-image-path)
         (let [full-image (ImageIO/read (io/file full-image-path))]
           (println "    -> Full image dimensions:" (.getWidth full-image)
                    "x" (.getHeight full-image))
           (let [thumbnail (create-thumbnail full-image 100)]
             (println "    -> Thumbnail dimensions:" (.getWidth thumbnail)
                      "x" (.getHeight thumbnail))
             (with-open [output (java.io.ByteArrayOutputStream.)]
               ;; Use JPEG writer with quality settings to match JavaScript
               ;; (0.7 quality)
               (let [writers (javax.imageio.ImageIO/getImageWritersByFormatName
                              "jpg")
                     writer (.next writers)
                     write-param (.getDefaultWriteParam writer)]
                 (.setCompressionMode
                  write-param
                  javax.imageio.ImageWriteParam/MODE_EXPLICIT)
                 (.setCompressionQuality write-param 0.7)
                 (with-open [image-output-stream
                             (javax.imageio.ImageIO/createImageOutputStream
                              output)]
                   (.setOutput writer image-output-stream)
                   (.write writer
                           nil
                           (javax.imageio.IIOImage. thumbnail nil nil)
                           write-param)
                   (.dispose writer)))
               (let [write-result true
                     byte-array (.toByteArray output)
                     base64-result
                     (.encodeToString (java.util.Base64/getEncoder) byte-array)]
                 (println "    -> ImageIO write result:" write-result)
                 (println "    -> Byte array length:" (count byte-array))
                 (println "    -> Base64 length:" (count base64-result))
                 (println "    -> Base64 preview:"
                          (subs base64-result 0 (min 50 (count base64-result)))
                          "...")
                 ;; Return raw base64 without data URL prefix (frontend
                 ;; adds it)
                 base64-result))))
         (catch Exception e
           (println "    -> Failed to generate thumbnail from" full-image-path
                    ":" (.getMessage e))
           (.printStackTrace e)
           nil))))

(defn transform-wine-for-api
  "Transform processed wine data for API consumption"
  [wine-data]
  (let [;; Load full image as base64 if it exists
        label-image (when (:full_path wine-data)
                      (let [result (load-image-as-base64 (:full_path
                                                          wine-data))]
                        (println "  -> Full image base64 length:"
                                 (when result (count result)))
                        result))
        ;; Generate new high-quality thumbnail from full image
        label-thumbnail (when (:full_path wine-data)
                          (let [result (generate-thumbnail-from-full-image
                                        (:full_path wine-data))]
                            (println "  -> Generated thumbnail base64 length:"
                                     (when result (count result)))
                            result))]
    (-> wine-data
        ;; Remove processing-only fields
        (dissoc :image_file
                :thumbnail_file
                :full_path
                :thumb_path
                :vivino-personal-note
                :raw-wine-type
                :raw-review
                :raw-personal-note
                :vivino-rating-original
                :vivino-url
                :tasting-notes) ; Handle separately
        ;; Add images
        (assoc :label_image label-image :label_thumbnail label-thumbnail)
        ;; Remove nil values for cleaner API calls
        (->> (filter (fn [[k v]] (not (nil? v))))
             (into {})))))

(defn create-wine-via-api
  "Create a wine via the backend API"
  [wine-data & {:keys [auth-token]}]
  (let [api-data (transform-wine-for-api wine-data)
        url "http://localhost:3000/api/wines"
        headers (cond-> {"Content-Type" "application/json"}
                  auth-token (assoc "Cookie" auth-token))]
    (println "Creating wine:" (:producer wine-data) (:name wine-data))
    (try (let [response @(http/post url
                                    {:body (json/write-value-as-string api-data)
                                     :headers headers
                                     :as :text})]
           (if (= 201 (:status response))
             (let [created-wine (json/read-value
                                 (:body response)
                                 json/keyword-keys-object-mapper)]
               (println "  ✅ Created wine with ID:" (:id created-wine))
               created-wine)
             (do (println "  ❌ Failed to create wine:"
                          (:status response)
                          (:body response))
                 nil)))
         (catch Exception e
           (println "  ❌ Error creating wine:" (.getMessage e))
           nil))))

(defn convert-date
  "Convert MM/dd/yy or MM/dd/yyyy format to ISO date (YYYY-MM-DD)"
  [date-str]
  (when (and date-str (not (str/blank? date-str)))
    (try (let [[month day year] (str/split date-str #"/")
               year-num (parse-long year)
               ;; Handle both 2-digit and 4-digit years
               full-year (cond (>= year-num 1900) year-num ; Already 4-digit
                               (< year-num 50) (+ 2000 year-num) ; 2-digit: 25
                                                                 ; -> 2025
                               :else (+ 1900 year-num)) ; 2-digit: 85 ->
                                                        ; 1985
               ;; Pad month and day with zeros
               padded-month (format "%02d" (parse-long month))
               padded-day (format "%02d" (parse-long day))]
           (str full-year "-" padded-month "-" padded-day))
         (catch Exception e
           (println "    -> Failed to convert date:" date-str)
           nil))))

(defn create-tasting-note-via-api
  "Create a tasting note via the backend API"
  [wine-id tasting-note & {:keys [auth-token]}]
  (let [url
        (str "http://localhost:3000/api/wines/by-id/" wine-id "/tasting-notes")
        ;; Transform keys to match API expectations
        api-data (-> tasting-note
                     (assoc :notes (:note tasting-note)) ; API expects :notes
                                                         ; not :note
                     (dissoc :note)
                     ;; Convert rating to int if present
                     (update :rating #(when % (int %)))
                     ;; Convert date to ISO format
                     (update :tasting_date convert-date))
        headers (cond-> {"Content-Type" "application/json"}
                  auth-token (assoc "Cookie" auth-token))]
    (println "  -> Creating tasting note for wine" wine-id)
    (println "    -> POST" url)
    (println "    -> Body:" (json/write-value-as-string api-data))
    (try (let [response @(http/post url
                                    {:body (json/write-value-as-string api-data)
                                     :headers headers
                                     :as :text})]
           (if (= 201 (:status response))
             (println "    ✅ Created tasting note")
             (println "    ❌ Failed to create tasting note:"
                      (:status response)
                      (:body response))))
         (catch Exception e
           (println "    ❌ Error creating tasting note:" (.getMessage e))))))

(defn valid-wine?
  "Check if wine has minimum required data for import"
  [wine-data]
  (and ;; Must have either producer or name (or both)
   (or (not (str/blank? (:producer wine-data)))
       (not (str/blank? (:name wine-data))))
   ;; Must have basic wine metadata
   (not (str/blank? (:country wine-data)))
   (not (str/blank? (:region wine-data)))
   (not (str/blank? (:style wine-data)))))

(defn import-processed-wines
  "Import wines from processed EDN file to database via API"
  [edn-file & {:keys [auth-token]}]
  (let [all-wines (read-string (slurp edn-file))
        valid-wines (filter valid-wine? all-wines)
        skipped-count (- (count all-wines) (count valid-wines))]
    (when (> skipped-count 0)
      (println "Skipping" skipped-count "invalid/empty wines"))
    (println "Importing" (count valid-wines) "valid wines from" edn-file)
    (doseq [wine-data valid-wines]
      (when-let [created-wine
                 (create-wine-via-api wine-data :auth-token auth-token)]
        ;; Create tasting notes if they exist
        (when-let [tasting-notes (:tasting-notes wine-data)]
          (doseq [note tasting-notes]
            (create-tasting-note-via-api (:id created-wine)
                                         note
                                         :auth-token
                                         auth-token)))))
    (println "Import completed!")))

(comment
  (count (filter #(or (not (str/blank? (:raw-personal-note %)))
                      (not (str/blank? (:raw-review %))))
                 (process-sample 300 0 "vivino_processed_sample.edn")))
  (def auth-token "YOUR_AUTH_TOKEN_HERE")
  ;; Import to database
  (import-processed-wines "vivino_processed_sample.edn" :auth-token auth-token)
  ;; Test personal note parsing
  (parse-personal-note "d6, $12.95")
  (parse-personal-note "$22, I6")
  (parse-personal-note "still holding up")
  (parse-personal-note
   "G6 $40 from last bottle cherry and leather, smooth finish 3/15/2025")
  (parse-personal-note
   "ate with burgers, delicious fruity wine with good tannins")
  (parse-personal-note "WE 95 complex nose with mint and spice, can drink now")
  (parse-personal-note
   "94 Decanter World Wine Awards A touch minty, with red and black cherry, pomegranate, and lovely floral elements")
  (parse-personal-note
   "Black tea, forest floor, clove and rhubarb keep it savory and earthy in the glass, while robust and refined tannin, a length of white pepper are there at its finish. (VB) WE 95")
  ;; Test location-only notes - should extract location but not create
  ;; tasting notes
  (parse-personal-note "B6")
  (parse-personal-note "A12")
  ;; Test Wine Enthusiast review parsing
  (parse-personal-note
   "A perennial standout in the producer's line-up of vineyard-designates, this wine is lovely, complex and textured. Black tea, forest floor, clove and rhubarb keep it savory and earthy in the glass, while robust and refined tannin, a length of white pepper are there at its finish. (VB) WE 95")
  ;; Test Vivino rating conversion
  (convert-vivino-rating 4.2) ; Should be ~87 points
  (convert-vivino-rating 3.8) ; Should be ~83 points
  (convert-vivino-rating "4.5") ; Should be 90 points
  ;; Check what data we have
  (let [{:keys [wines cellar prices]} (load-vivino-data)]
    (println "Sample wine data:")
    (pprint (first wines))
    (println "\nSample cellar data:")
    (pprint (first cellar))
    (println "\nSample price data:")
    (pprint (first prices))))
