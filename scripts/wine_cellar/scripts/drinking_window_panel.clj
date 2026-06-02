(ns wine-cellar.scripts.drinking-window-panel
  "Ad-hoc: run a fixed wine panel through multiple AI providers/models and
   tabulate the suggested drinking windows. Captures the exact rendered prompt
   per wine so re-runs are apples-to-apples. Load + run in the backend REPL:
     (require 'wine-cellar.scripts.drinking-window-panel :reload)
     (wine-cellar.scripts.drinking-window-panel/run!)"
  (:require [clojure.string :as str]
            [wine-cellar.ai.prompts :as prompts]
            [wine-cellar.ai.anthropic :as anthropic]
            [wine-cellar.ai.openai :as openai]
            [wine-cellar.ai.gemini :as gemini]))

;; Snapshot of the panel as dumped from production (ids 208/90/174/221/31/226).
(def panel
  [{:id 208
    :producer "Status Quo Winery"
    :name "Status Quo Sonoma Coast Chardonnay"
    :vintage 2024
    :style "White"
    :country "United States"
    :region "Sonoma County"
    :appellation "Sonoma Coast"
    :appellation_tier "AVA"
    :classification nil
    :designation nil
    :vineyard nil
    :closure_type "Agglomerated cork"
    :bottle_format "Standard (750ml)"
    :alcohol_percentage 13.5
    :price 19
    :purveyor "First Bottle"
    :purchase_date "2025-08-22"
    :metadata nil
    :varieties [{:name "Chardonnay" :percentage nil}]
    :tasting_notes
    [{:rating 93
      :wset_data nil
      :tasting_date nil
      :notes
      "The 2024 Status Quo Chardonnay from the Sonoma Coast is medium-bodied and lush, featuring baked orchard fruit with a squeeze of Meyer lemon and lime zest. Chalky minerality adds complexity, while the lengthy finish is driven by pressed wildflowers and crushed Marcona almonds drizzled with honey."}]}
   {:id 90
    :producer "Château Guiraud"
    :name ""
    :vintage 2003
    :style "Dessert"
    :country "France"
    :region "Bordeaux"
    :appellation "Sauternes"
    :appellation_tier "AOC"
    :classification "Premier Cru Classé"
    :designation nil
    :vineyard nil
    :closure_type "Cork (unspecified)"
    :bottle_format "Half Bottle (375ml)"
    :alcohol_percentage 13.5
    :price 30
    :purveyor "Last Bottle"
    :purchase_date "2021-10-28"
    :metadata nil
    :varieties [{:name "Sauvignon Blanc" :percentage 35}
                {:name "Sémillon" :percentage 65}]
    :tasting_notes nil}
   {:id 174
    :producer "Mi Sueño"
    :name "El Llano Red"
    :vintage 2014
    :style "Red"
    :country "United States"
    :region "Napa Valley"
    :appellation "Napa Valley"
    :appellation_tier "AVA"
    :classification nil
    :designation nil
    :vineyard nil
    :closure_type "Cork (unspecified)"
    :bottle_format "Standard (750ml)"
    :alcohol_percentage 14.5
    :price 50
    :purveyor nil
    :purchase_date nil
    :metadata nil
    :varieties [{:name "Cabernet Sauvignon" :percentage nil}
                {:name "Syrah" :percentage nil}]
    :tasting_notes nil}
   {:id 221
    :producer "Romuald Valot"
    :name ""
    :vintage 2020
    :style "Red"
    :country "France"
    :region "Burgundy"
    :appellation "Gevrey-Chambertin"
    :appellation_tier "AOP"
    :classification "Premier Cru"
    :designation nil
    :vineyard "Champonnet"
    :closure_type "Cork (unspecified)"
    :bottle_format "Standard (750ml)"
    :alcohol_percentage 13.5
    :price 99
    :purveyor "Last Bottle"
    :purchase_date "2025-08-21"
    :metadata nil
    :varieties [{:name "Pinot noir" :percentage 100}]
    :tasting_notes
    [{:rating 94
      :wset_data nil
      :tasting_date nil
      :notes
      "The senses are captivated by heady, crushed flowers sprinkled atop pressed strawberries. Fresh rose, cherry, pomegranate and black pepper on the palate give way to its brisk refreshing acidity, adding finesse and accentuating lingering minerality. "}]}
   {:id 31
    :producer "Tenuta Carretta"
    :name "Cuvée San Rocco"
    :vintage 2021
    :style "Rosé Sparkling"
    :country "Italy"
    :region "Piedmont"
    :appellation "Nebbiolo d'Alba"
    :appellation_tier "DOC"
    :classification nil
    :designation nil
    :vineyard "San Rocco"
    :closure_type "Cork (unspecified)"
    :bottle_format "Standard (750ml)"
    :alcohol_percentage 12.5
    :price 26
    :purveyor "Last Bubbles"
    :purchase_date "2024-12-11"
    :metadata nil
    :varieties [{:name "Nebbiolo" :percentage 100}]
    :tasting_notes nil}
   {:id 226
    :producer "Louis Latour"
    :name ""
    :vintage 2019
    :style "Red"
    :country "France"
    :region "Burgundy"
    :appellation "Corton-Perrières"
    :appellation_tier "AOC"
    :classification "Grand Cru"
    :designation nil
    :vineyard nil
    :closure_type "Cork (unspecified)"
    :bottle_format "Standard (750ml)"
    :alcohol_percentage 14
    :price 129
    :purveyor "Last Bottle"
    :purchase_date "2025-08-21"
    :metadata nil
    :varieties [{:name "Pinot noir" :percentage 100}]
    :tasting_notes
    [{:rating 93
      :wset_data nil
      :tasting_date nil
      :notes
      "A lush yet intense red, with silky texture and a fleshy profile setting the stage for cherry, strawberry, earth, tobacco and mineral flavors. This evolves on the palate, with dense, civilized tannins emerging in the process and providing overall equilibrium. Lively and long. Best from 2025."}]}])

(defn gemini-with-model
  "Call Gemini's drinking-window path with an explicit model override."
  [model {:keys [system user]}]
  (#'gemini/call-gemini-api
   {:system system
    :messages [{:role "user" :content user}]
    :response-schema gemini/drinking-window-schema
    :max-tokens 10000}
   :model-override
   model
   :parse-json?
   true))

;; label -> fn that takes prompt-data {:system :user} and returns the parsed
;; map
(def models
  [["anthropic/opus-4-7" #(anthropic/suggest-drinking-window %)]
   ["openai/gpt-5.5" #(openai/suggest-drinking-window %)]
   ["gemini-3.1-pro" #(gemini/suggest-drinking-window %)]
   ["gemini-3-pro" #(gemini-with-model "gemini-3-pro-preview" %)]])

(defn- safe-call
  [f prompt-data]
  (try (f prompt-data) (catch Exception e {:error (.getMessage e)})))

(defn run-panel!
  "Runs the panel through every model, prints a comparison table, returns rows."
  []
  (let [system (prompts/drinking-window-system-prompt)
        rows (for [wine panel]
               (let [user (prompts/drinking-window-user-message wine)
                     pd {:system system :user user}]
                 {:wine wine
                  :prompt user
                  :results (into {}
                                 (for [[label f] models]
                                   [label (safe-call f pd)]))}))]
    ;; Capture the exact rendered prompts for reproducibility.
    (spit "/tmp/panel_prompts.txt"
          (str/join "\n\n========================================\n\n"
                    (for [{:keys [wine prompt]} rows]
                      (str "WINE " (:id wine)
                           " — " (:producer wine)
                           " " (:vintage wine)
                           "\n\nSYSTEM (shared):\n" system
                           "\n\nUSER:\n" prompt))))
    (doseq [{:keys [wine results]} rows]
      (println (str "\n=== "
                    (:id wine)
                    "  "
                    (:producer wine)
                    " "
                    (when (seq (:name wine)) (str (:name wine) " "))
                    (:vintage wine)
                    "  ["
                    (:style wine)
                    ", "
                    (:bottle_format wine)
                    "] ==="))
      (doseq [[label r] (sort-by key results)]
        (println (format "  %-18s %s"
                         label
                         (if (:error r)
                           (str "ERROR: " (:error r))
                           (let [s (str (:reasoning r))]
                             (str (:drink_from_year r)
                                  "–"
                                  (:drink_until_year r)
                                  "  ("
                                  (:confidence r)
                                  ")  "
                                  (subs s 0 (min 160 (count s)))
                                  (when (> (count s) 160) "..."))))))))
    rows))
