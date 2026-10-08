(ns wine-cellar.admin.bulk-operations
  "Background jobs that run an AI task over many wines, one at a time, with
   progress the admin page polls."
  (:require [clojure.string :as str]
            [wine-cellar.db.api :as db-api]
            [wine-cellar.ai.core :as ai]))

;; Job state management
(def active-jobs (atom {}))

(def ^:private finished-job-ttl-ms (* 60 60 1000))

(defn- now-ms [] (System/currentTimeMillis))

(defn- forget-old-jobs!
  "Drops finished jobs nobody has polled for an hour, so the map stays small."
  []
  (swap! active-jobs (fn [jobs]
                       (into {}
                             (remove (fn [[_ {:keys [status updated-at]}]]
                                       (and (#{:completed :failed} status)
                                            (< (+ updated-at
                                                  finished-job-ttl-ms)
                                               (now-ms)))))
                             jobs))))

(defn- update-job!
  "Merges `changes` into the job, so accumulated keys like :failed-wines and
   :job-type survive each progress tick."
  [job-id changes]
  (swap! active-jobs update job-id merge changes {:updated-at (now-ms)}))

(defn get-job-status [job-id] (get @active-jobs job-id))

(defn- process-wine!
  "Runs `process` on one wine. A failure is recorded on the job and doesn't
   stop the others."
  [job-id process wine]
  (try (process wine)
       (catch Exception e
         (tap> ["❌ Job" job-id "failed on wine" (:id wine) e])
         (swap! active-jobs update-in
           [job-id :failed-wines]
           (fnil conj [])
           {:wine-id (:id wine) :error (.getMessage e)}))))

(defn- start-wine-job!
  "Starts `process` over the wines in a background thread and returns the job
   id to poll."
  [job-type wine-ids process]
  (forget-old-jobs!)
  (let [job-id (str "job-" (random-uuid))
        total (count wine-ids)]
    (tap> ["Starting" job-type "job" job-id "for" total "wines"])
    (update-job! job-id
                 {:status :running :job-type job-type :progress 0 :total total})
    (future
     (try (let [wines (db-api/get-enriched-wines-by-ids wine-ids)]
            (if (empty? wines)
              (update-job! job-id {:status :failed :error "No wines found"})
              (do (doseq [[idx wine] (map-indexed vector wines)]
                    (process-wine! job-id process wine)
                    (update-job! job-id {:progress (inc idx)}))
                  (update-job! job-id {:status :completed :progress total})
                  (tap> ["Completed" job-type "job" job-id "failed wines:"
                         (count (:failed-wines (get-job-status job-id)))]))))
          (catch Exception e
            (tap> ["💥 Job" job-id "failed:" e])
            (update-job! job-id {:status :failed :error (.getMessage e)}))))
    job-id))

(defn start-drinking-window-job
  "Start async job to regenerate drinking windows for wine IDs"
  [{:keys [wine-ids provider]}]
  (start-wine-job! :drinking-window
                   wine-ids
                   (fn [wine]
                     (let [{:keys [drink_from_year drink_until_year reasoning]}
                           (ai/suggest-drinking-window (some-> provider
                                                               keyword)
                                                       wine)]
                       (db-api/update-wine! (:id wine)
                                            {:drink_from_year drink_from_year
                                             :drink_until_year drink_until_year
                                             :tasting_window_commentary
                                             reasoning})))))

(defn start-wine-summary-job
  "Start async job to regenerate AI wine summaries for wine IDs"
  [{:keys [wine-ids provider]}]
  (start-wine-job!
   :wine-summary
   wine-ids
   (fn [wine]
     (let [summary (some-> (ai/generate-wine-summary (some-> provider
                                                             keyword)
                                                     wine)
                           str/trim)]
       (when (str/blank? summary)
         (throw (ex-info "AI summary was blank" {:wine-id (:id wine)})))
       (db-api/update-wine! (:id wine) {:ai_summary summary})))))
