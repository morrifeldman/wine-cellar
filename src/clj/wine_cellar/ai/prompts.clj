(ns wine-cellar.ai.prompts
  "Shared prompt helpers for AI providers."
  (:require [clojure.string :as str]
            [wine-cellar.common :as common]))

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

(defn create-wine-context
  "Produces a multi-line summary of the wine collection, capped at 50 wines."
  [wines]
  (tap> ["create-wine-context-wines" wines])
  (if (empty? wines)
    "The user has no wines in their collection yet."
    (let [wine-count (count wines)
          wine-summaries (take 50 (map format-wine-summary wines))
          summary (str "The user has "
                       wine-count
                       " wines in their collection:\n"
                       (str/join "\n" wine-summaries)
                       (when (> wine-count 50) "\n... and more wines"))]
      (tap> ["wine cellar summary" summary])
      summary)))

(defn wine-collection-context
  "Wraps the wine context with an explanatory prefix for system prompts."
  [wines]
  (str "Here is information about the user's wine collection:\n\n"
       (create-wine-context wines)))

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
