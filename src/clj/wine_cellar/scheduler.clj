(ns wine-cellar.scheduler
  (:require [mount.core :refer [defstate]]
            [wine-cellar.reports.core :as reports])
  (:import [java.util.concurrent Executors TimeUnit ScheduledExecutorService]))

(defn generate-report-task
  []
  (try (tap>
        "⏰ Scheduler: Starting scheduled cellar report generation check...")
       (reports/generate-report!) ;; This function checks DB first and only
                                  ;; generates if needed
       (tap> "⏰ Scheduler: Cellar report check completed.")
       (catch Exception e
         (tap> ["❌ Scheduler: Failed to generate scheduled cellar report"
                (.getMessage e)]))))

(defn start-scheduler
  []
  (let [scheduler (Executors/newScheduledThreadPool 1)]
    ;; Schedule initial run after 1 minute (let server startup settle)
    ;; Then every 24 hours.
    (.scheduleAtFixedRate scheduler
                          generate-report-task
                          1
                          (* 24 60)
                          TimeUnit/MINUTES)
    (tap>
     "⏰ Scheduler started: Cellar report check scheduled (initial: 1min, repeat: 24hr)")
    scheduler))

(defn stop-scheduler
  [^ScheduledExecutorService scheduler]
  (when scheduler
    (.shutdown scheduler)
    (try (if (.awaitTermination scheduler 5 TimeUnit/SECONDS)
           (tap> "⏰ Scheduler stopped cleanly")
           (do
             (tap>
              "⏰ Scheduler timed out waiting for termination, forcing shutdown")
             (.shutdownNow scheduler)))
         (catch InterruptedException _ (.shutdownNow scheduler)))))

(defstate scheduler :start (start-scheduler) :stop (stop-scheduler scheduler))
