(ns wine-cellar.ai.prompts
  "Shared prompt helpers for AI providers."
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]
            [wine-cellar.summary :as summary]))

(defn format-wine-summary
  "Creates a formatted summary of a single wine including tasting notes and varieties.
   Optional flags mirror the original Anthropic helper so both providers stay in sync."
  [wine & {:keys [include-quantity? bullet-prefix include-drinking-window?
                  include-ai-summary?]
           :or {include-quantity? true
                bullet-prefix "- "
                include-drinking-window? true
                include-ai-summary? true}}]
  (tap> ["format-wine-summary" wine])
  (let [basic-info (str bullet-prefix
                        (:producer wine)
                        (when (:name wine) (str " " (:name wine)))
                        (when (:vintage wine) (str " " (:vintage wine)))
                        " (" (:style wine)
                        ", " (:country wine)
                        ", " (:region wine)
                        ")" (when (and include-quantity? (:quantity wine))
                              (str " - " (:quantity wine)
                                   " bottles" (when (:original_quantity wine)
                                                (str " (originally "
                                                     (:original_quantity wine)
                                                     ")")))))
        classification-info
        (str/join
         ""
         [(when (:aoc wine) (str "\n  AOC/AVA: " (:aoc wine)))
          (when (:vineyard wine) (str "\n  Vineyard: " (:vineyard wine)))
          (when (:classification wine)
            (str "\n  Classification: " (:classification wine)))
          (when (:level wine) (str "\n  Level: " (:level wine)))
          (when (:closure_type wine)
            (str "\n  Closure: " (:closure_type wine)))
          (when (:disgorgement_year wine)
            (str "\n  Disgorgement Year: " (:disgorgement_year wine)))
          (when (:alcohol_percentage wine)
            (str "\n  Alcohol: " (:alcohol_percentage wine) "%"))
          (when (:price wine) (str "\n  Price: $" (:price wine)))
          (when (:purveyor wine) (str "\n  Shop: " (:purveyor wine)))
          (when (:purchase_date wine) (str "\n  Purchase Date: " (:purchase_date wine)))
          (when (and include-drinking-window?
                     (or (:drink_from_year wine) (:drink_until_year wine)))
            (str "\n  Drinking Window: " (or (:drink_from_year wine) "?")
                 " - " (or (:drink_until_year wine) "?")))
          (when (and include-drinking-window? (:tasting_window_commentary wine))
            (str "\n  Drinking Notes: " (:tasting_window_commentary wine)))])
        varieties (:varieties wine)
        varieties-summary
        (when (seq varieties)
          (str "\n  Varieties: "
               (str/join ", "
                         (map (fn [v]
                                (if (:percentage v)
                                  (str (:name v) " (" (:percentage v) "%)")
                                  (:name v)))
                              varieties))))
        tasting-notes (:tasting_notes wine)
        notes-summary
        (when (seq tasting-notes)
          (str
           "\n  Tasting notes: "
           (str/join
            "; "
            (map
             (fn [note]
               (let [basic-note (str (when (:rating note)
                                       (str (:rating note) "/100 - "))
                                     (:notes note))
                     wset-data (:wset_data note)
                     wset-summary
                     (when wset-data
                       (let [appearance (:appearance wset-data)
                             nose (:nose wset-data)
                             palate (:palate wset-data)
                             conclusions (:conclusions wset-data)]
                         (str/join
                          " | "
                          (filter
                           seq
                           [(when appearance
                              (str "Appearance: "
                                   (str/join ", "
                                             (filter identity
                                                     [(:clarity appearance)
                                                      (:intensity appearance)
                                                      (:colour appearance)]))))
                            (when nose
                              (str "Nose: "
                                   (str/join ", "
                                             (filter identity
                                                     [(:condition nose)
                                                      (:intensity nose)
                                                      (:development nose)]))))
                            (when palate
                              (str "Palate: "
                                   (str/join ", "
                                             (filter identity
                                                     [(:sweetness palate)
                                                      (:acidity palate)
                                                      (:tannin palate)
                                                      (:body palate)
                                                      (:finish palate)]))))
                            (when conclusions
                              (str "Quality: "
                                   (:quality-level conclusions)
                                   (when (:readiness conclusions)
                                     (str ", Readiness: "
                                          (:readiness conclusions)))))]))))]
                 (if wset-summary
                   (str basic-note " [WSET: " wset-summary "]")
                   basic-note)))
             (take 3 tasting-notes)))))
        ai-summary (when (and include-ai-summary? (:ai_summary wine))
                     (str "\n  Previous AI Summary: " (:ai_summary wine)))]
    (str basic-info
         classification-info
         varieties-summary
         notes-summary
         ai-summary)))

(def max-wines 50)


(defn summary->text-lines
  "Convert condensed summary data into text lines for prompt consumption."
  [summary-data]
  (let [{:keys [totals countries styles regions vintages varieties price-bands drinking-window]}
        (or summary-data (summary/condensed-summary []))
        {:keys [wines bottles]} (or totals {:wines 0 :bottles 0})
        format-entry (fn [entry]
                       (when-let [{:keys [label bottles bottle-share]} entry]
                         (let [bottle-count (or bottles 0)
                               pct (when bottle-share (Math/round (* 100 bottle-share)))]
                           (when (pos? bottle-count)
                             (cond-> (str label " - " bottle-count " bottles")
                               (and pct (pos? pct)) (str " (" pct "%)"))))))
        format-group (fn [prefix {:keys [top other]}]
                       (let [parts (->> top (map format-entry) (remove str/blank?) vec)
                             other-part (format-entry other)
                             all-parts (cond-> parts other-part (conj other-part))]
                         (when (seq all-parts)
                           (str prefix (str/join ", " all-parts)))))
        country-line (format-group "Top countries: " countries)
        style-line (format-group "Styles: " styles)
        region-line (format-group "Regions: " regions)
        variety-line (format-group "Top varieties: " varieties)
        price-line (format-group "Price bands: " price-bands)
        vintage-line (let [{:keys [bands non-vintage]} vintages
                           band-parts (->> bands (map format-entry) (remove str/blank?))
                           non-v (format-entry non-vintage)
                           all (cond-> (vec band-parts) non-v (conj non-v))]
                       (when (seq all)
                         (str "Vintages: " (str/join ", " all))))
        ready-line (let [ready (get drinking-window :ready)
                         style-parts (->> (:styles ready) (map format-entry) (remove str/blank?))
                         ready-year (:year ready)]
                     (when (seq style-parts)
                       (str "Ready to drink now"
                            (when ready-year (str " (" ready-year ")"))
                            ": " (str/join ", " style-parts))))]
    (cond-> [(str "Cellar snapshot (in stock): " wines " wines / " bottles " bottles.")]
      country-line (conj country-line)
      style-line (conj style-line)
      region-line (conj region-line)
      variety-line (conj variety-line)
      price-line (conj price-line)
      vintage-line (conj vintage-line)
      ready-line (conj ready-line))))

(defn- condensed-summary-text
  [summary-data]
  (str/join "\n" (summary->text-lines summary-data)))

(defn- selected-wines-context
  [wines]
  (when (seq wines)
    (let [wine-count (count wines)
          wine-summaries (take max-wines (map format-wine-summary wines))
          header (if (= wine-count 1)
                   "Currently selected wine:"
                   (str "Currently selected wines (" wine-count "):"))
          body (str/join "\n" wine-summaries)
          truncated? (> wine-count max-wines)
          suffix (when truncated? "\n... and more wines")]
      (str header "\n" body suffix))))

(defn wine-collection-context
  "Wraps the condensed cellar snapshot with optional selected wines details."
  [{:keys [summary selected-wines]}]
  (let [summary-text (condensed-summary-text summary)
        selection-text (selected-wines-context selected-wines)
        base (str "Here is information about the user's wine collection:\n\n"
                  summary-text)]
    (if selection-text
      (str base "\n\n" selection-text)
      base)))

(defn wine-system-instructions
  "Baseline system instructions shared across AI providers for chat."
  []
  (let [current-year (.getValue (java.time.Year/now))]
    (str
     "You are a knowledgeable wine expert and sommelier helping someone with their wine collection. "
     "Please respond in a conversational, friendly tone. You can discuss wine recommendations, "
     "food pairings, optimal drinking windows, storage advice, or any wine-related questions. "
     "When images are provided, analyze wine labels, wine lists, or other wine-related images to help with "
     "identification, recommendations, or general wine advice. "
     "The current year is "
     current-year
     ". "
     "CRITICAL FORMATTING REQUIREMENT: You MUST respond in plain text only. Do NOT use any markdown formatting whatsoever. This means:\n"
     "- NO headers starting with #\n"
     "- NO bold text with **\n"
     "- NO italic text with _ or *\n"
     "- NO code blocks with ```\n"
     "- NO bullet points with -\n"
     "- NO numbered lists\n"
     "Write your response as simple, natural conversational text with normal punctuation only.")))

(defn- strip-image-data-url
  [image]
  (some-> image
          (str/replace #"^data:image/jpeg;base64," "")
          (str/replace #"^data:image/png;base64," "")))

(defn conversation-messages
  "Normalizes conversation history into provider-agnostic chat messages.
   Each message is a map with :role (\"user\" or \"assistant\") and :content, where
   :content is either a string or a vector containing {:type \"text\" ...} / {:type \"image\" ...}."
  ([conversation-history]
   (conversation-messages conversation-history nil))
  ([conversation-history image]
   (let [base (mapv (fn [msg]
                      (let [role (if (or (:is-user msg) (:is_user msg)) "user" "assistant")
                            content (or (:content msg) (:text msg) "")]
                        {:role role :content content}))
                    conversation-history)]
     (if (and image
              (seq base)
              (= "user" (:role (peek base))))
       (let [idx (dec (count base))
             last-msg (peek base)
             existing-content (:content last-msg)
             text-content (cond
                            (vector? existing-content)
                            (or (:text (first existing-content)) "")
                            (string? existing-content) existing-content
                            :else (str existing-content))
             image-content [{:type "text" :text text-content}
                            {:type "image"
                             :source {:type "base64"
                                      :media_type "image/jpeg"
                                      :data (strip-image-data-url image)}}]
             updated-last (assoc last-msg :content image-content)]
        (assoc base idx updated-last))
       base))))

(defn- infer-media-type
  [image]
  (cond
    (and image (str/starts-with? (str/lower-case image) "data:image/png")) "image/png"
    :else "image/jpeg"))

(defn- label-image-entry
  [image]
  (when-let [data (strip-image-data-url image)]
    {:type "image"
     :source {:type "base64"
              :media_type (infer-media-type image)
              :data data}}))

(defn label-analysis-system-prompt
  []
  (let [style-options (str/join ", " (sort common/wine-styles))
        level-options (str/join ", " (sort common/wine-levels))]
    (str
     "You are a wine expert tasked with extracting information from wine label images. "
     "Analyze the provided wine label images and extract the following information in JSON format:\n\n"
     "- producer: The wine producer/winery name\n"
     "- name: The specific name of the wine (if different from producer)\n"
     "- vintage: The year the wine was produced (numeric, or null for non-vintage)\n"
     "- country: The country of origin\n"
     "- region: The wine region within the country\n"
     "- aoc: The appellation or controlled designation of origin (if applicable)\n"
     "- vineyard: The specific vineyard name (if mentioned)\n"
     "- classification: Any classification or quality designation\n"
     "- style: The wine style. Must be one of: " style-options "\n"
     "- level: The wine level. Must be one of: " level-options "\n"
     "- alcohol_percentage: The percentage of alcohol if it is visible\n\n"
     "Return ONLY a valid parseable JSON object with these fields. If you cannot determine a value, use null for that field. "
     "Do not nest the result in a markdown code block. Do not include any explanatory text outside the JSON object.")))

(defn label-analysis-user-content
  [front-image back-image]
  (let [front-entry (label-image-entry front-image)
        back-entry (label-image-entry back-image)
        has-front (boolean front-entry)
        has-back (boolean back-entry)
        image-desc (cond
                     (and has-front has-back) "front and back label images"
                     has-front "front label image"
                     has-back "back label image"
                     :else "wine label image")
        base [{:type "text" :text (str "Please analyze these " image-desc ":")}]
        entries (cond-> base
                  front-entry (conj front-entry)
                  back-entry (conj back-entry))]
    entries))

(defn label-analysis-prompt
  [front-image back-image]
  {:system (label-analysis-system-prompt)
   :user-content (label-analysis-user-content front-image back-image)})

(defn drinking-window-system-prompt
  []
  (let [current-year (.getValue (java.time.Year/now))]
    (str
     "You are a wine expert tasked with suggesting the OPTIMAL drinking window for a wine. "
     "Focus on when this wine will be at its absolute peak quality and most enjoyable, "
     "not just when it's acceptable to drink. Based on wine characteristics "
     "including tasting notes and grape varieties, suggest the ideal timeframe when "
     "this wine will express its best characteristics.\n\n"
     "The current year is " current-year ".\n\n"
     "Return your response in JSON format with the following fields:\n"
     "- drink_from_year: (integer) The year when the wine will reach optimal drinking condition\n"
     "- drink_until_year: (integer) The year when the wine will still be at peak quality (not just drinkable)\n"
     "- confidence: (string) \"high\", \"medium\", or \"low\" based on how confident you are in this assessment\n"
     "- reasoning: (string) A brief explanation of your recommendation focusing on peak quality timing, and mention the broader window when the wine remains enjoyable to drink\n\n"
     "Only return a valid parseable JSON object without any additional text. "
     "Do not nest the response in a markdown code block.")))

(defn drinking-window-user-message
  [wine]
  (let [wine-summary (format-wine-summary wine
                                          :include-quantity? false
                                          :bullet-prefix ""
                                          :include-drinking-window? false
                                          :include-ai-summary? false)]
    (str "Wine details:\n" wine-summary)))

(defn wine-summary-system-prompt
  []
  (str
   "You are a wine expert tasked with creating a concise wine summary including taste profile and food pairing recommendations. "
   "Based on wine details provided, create an informative but brief summary that would be helpful to someone deciding whether to drink this wine or what to pair it with.\n\n"
   "Please provide a concise summary (2-3 paragraphs maximum) that includes:\n"
   "1. Overall style and key taste characteristics\n"
   "2. Top 3-4 food pairing suggestions\n"
   "3. Basic serving recommendations\n\n"
   "Write in a conversational, informative tone. Keep it concise and practical. "
   "Focus on the most important information for enjoyment and pairing decisions. "
   "Return only the summary text without any formatting or structure markers."))

(defn wine-summary-user-message
  [wine]
  (let [wine-details (format-wine-summary wine
                                          :include-quantity? false
                                          :bullet-prefix ""
                                          :include-ai-summary? false)]
    (str "Wine details:\n" wine-details)))
