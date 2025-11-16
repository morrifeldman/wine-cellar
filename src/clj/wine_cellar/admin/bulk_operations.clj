(ns wine-cellar.admin.bulk-operations
  (:require [clojure.string :as str]
            [wine-cellar.db.api :as db-api]
            [wine-cellar.ai.core :as ai]))

;; Job state management
(def active-jobs (atom {}))

(defn generate-job-id [] (str "job-" (System/currentTimeMillis)))

(defn update-job-status!
  [job-id status & [progress total error]]
  (swap! active-jobs assoc
    job-id
    (merge {:status status :updated-at (System/currentTimeMillis)}
           (when progress {:progress progress})
           (when total {:total total})
           (when error {:error error}))))

(defn add-failed-wine!
  [job-id wine-id error-msg]
  (swap! active-jobs update-in
    [job-id :failed-wines]
    (fn [failed-wines]
      (conj (or failed-wines []) {:wine-id wine-id :error error-msg}))))

(defn get-job-status [job-id] (get @active-jobs job-id))

(defn start-drinking-window-job
  "Start async job to regenerate drinking windows for wine IDs"
  [{:keys [wine-ids provider]}]
  (let [job-id (generate-job-id)
        total-count (count wine-ids)
        provider-key (some-> provider
                             keyword)]
    (tap> ["🍷 Starting async drinking window job" job-id "for" total-count
           "wines"])
    (swap! active-jobs assoc
      job-id
      {:status :running
       :job-type :drinking-window
       :progress 0
       :total total-count
       :updated-at (System/currentTimeMillis)})
    (update-job-status! job-id :running 0 total-count)
    ;; Start background processing
    (future
     (try
       (tap> ["🔍 Getting enriched wines for IDs:" wine-ids])
       (let [wines (db-api/get-enriched-wines-by-ids wine-ids)]
         (tap> ["📋 Retrieved" (count wines) "wines for processing"])
         (if (empty? wines)
           (do (tap> ["⚠️ No wines found for processing"])
               (update-job-status! job-id :failed nil nil "No wines found"))
           (do
             (doall
              (map-indexed
               (fn [idx wine]
                 (try (tap> ["🍷 Processing wine" (inc idx) "of" total-count
                             "- ID:" (:id wine) "Producer:" (:producer wine)])
                      (let [ai-response (ai/suggest-drinking-window provider-key
                                                                    wine)]
                        (tap> ["🤖 AI response received for wine" (:id wine) ":"
                               ai-response])
                        (let [updates
                              {:drink_from_year (:drink_from_year ai-response)
                               :drink_until_year (:drink_until_year ai-response)
                               :tasting_window_commentary (:reasoning
                                                           ai-response)}]
                          (tap> ["💾 Updating wine" (:id wine) "with:" updates])
                          (db-api/update-wine! (:id wine) updates)
                          (update-job-status! job-id
                                              :running
                                              (inc idx)
                                              total-count)
                          (tap> ["✅ Successfully updated wine ID" (:id wine)])))
                      (catch Exception e
                        (tap> ["❌ Failed to process wine ID" (:id wine) ":"
                               (.getMessage e)])
                        (tap> ["🔍 Exception details:" e])
                        ;; Track the failed wine
                        (add-failed-wine! job-id (:id wine) (.getMessage e))
                        ;; Continue processing other wines even if one
                        ;; fails
                        (update-job-status! job-id
                                            :running
                                            (inc idx)
                                            total-count))))
               wines))
             (let [final-status (get-job-status job-id)
                   failed-count (count (:failed-wines final-status))
                   success-count (- total-count failed-count)]
               (update-job-status! job-id :completed total-count total-count)
               (tap> ["🎉 Completed drinking window job" job-id "- success:"
                      success-count "failed:" failed-count "total:"
                      total-count])
               (when (> failed-count 0)
                 (tap> ["⚠️ Failed wines:" (:failed-wines final-status)]))))))
       (catch Exception e
         (tap> ["💥 Job" job-id "failed with top-level error:" (.getMessage e)])
         (tap> ["🔍 Top-level exception details:" e])
         (update-job-status! job-id :failed nil nil (.getMessage e)))))
    job-id))

(defn start-wine-summary-job
  "Start async job to regenerate AI wine summaries for wine IDs"
  [{:keys [wine-ids provider]}]
  (let [job-id (generate-job-id)
        total-count (count wine-ids)
        provider-key (some-> provider
                             keyword)]
    (tap> ["📝 Starting async wine summary job" job-id "for" total-count
           "wines"])
    (swap! active-jobs assoc
      job-id
      {:status :running
       :job-type :wine-summary
       :progress 0
       :total total-count
       :updated-at (System/currentTimeMillis)})
    (update-job-status! job-id :running 0 total-count)
    (future
     (try
       (tap> ["🔍 Fetching wines for summary regeneration" wine-ids])
       (let [wines (db-api/get-enriched-wines-by-ids wine-ids)]
         (tap> ["📋 Retrieved" (count wines) "wines for summary generation"])
         (if (empty? wines)
           (do (tap> ["⚠️ No wines found for summary regeneration"])
               (update-job-status! job-id :failed nil nil "No wines found"))
           (do
             (doall
              (map-indexed
               (fn [idx wine]
                 (try
                   (tap> ["📝 Generating summary for wine" (inc idx) "of"
                          total-count "- ID:" (:id wine) "Producer:"
                          (:producer wine)])
                   (let [summary-text
                         (some-> (ai/generate-wine-summary provider-key wine)
                                 (str/trim))]
                     (when (str/blank? summary-text)
                       (throw (ex-info "AI summary was blank"
                                       {:wine-id (:id wine)})))
                     (tap> ["💾 Updating wine" (:id wine)
                            "with AI summary text"])
                     (db-api/update-wine! (:id wine) {:ai_summary summary-text})
                     (update-job-status! job-id :running (inc idx) total-count)
                     (tap> ["✅ Summary updated for wine" (:id wine)]))
                   (catch Exception e
                     (tap> ["❌ Failed to regenerate summary for wine ID"
                            (:id wine) ":" (.getMessage e)])
                     (tap> ["🔍 Summary exception details:" e])
                     (add-failed-wine! job-id (:id wine) (.getMessage e))
                     (update-job-status! job-id
                                         :running
                                         (inc idx)
                                         total-count))))
               wines))
             (let [final-status (get-job-status job-id)
                   failed-count (count (:failed-wines final-status))
                   success-count (- total-count failed-count)]
               (update-job-status! job-id :completed total-count total-count)
               (tap> ["🎉 Completed wine summary job" job-id "- success:"
                      success-count "failed:" failed-count "total:"
                      total-count])
               (when (> failed-count 0)
                 (tap> ["⚠️ Failed wines:" (:failed-wines final-status)]))))))
       (catch Exception e
         (tap> ["💥 Summary job" job-id "failed with top-level error:"
                (.getMessage e)])
         (tap> ["🔍 Summary job top-level exception:" e])
         (update-job-status! job-id :failed nil nil (.getMessage e)))))
    job-id))
