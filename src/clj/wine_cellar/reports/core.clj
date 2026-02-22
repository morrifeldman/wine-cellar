(ns wine-cellar.reports.core
  (:require [honey.sql :as sql]
            [jsonista.core :as json]
            [next.jdbc :as jdbc]
            [wine-cellar.ai.core :as ai]
            [wine-cellar.db.connection :refer [ds db-opts]])
  (:import [java.time LocalDate]))

(defn- get-date-range
  []
  (let [today (LocalDate/now)
        ;; For "periodic" reports, we can still use the week start to
        ;; debounce generation, or just use today's date if we want
        ;; on-demand. Let's stick to "week-start" logic for the database
        ;; key to avoid spamming reports daily, but call it "report-date".
        report-date (.minusDays today (.getValue (.getDayOfWeek today)))
        last-week (.minusWeeks today 1)]
    {:today today
     :report-date report-date
     :last-week last-week
     :current-year (.getYear today)}))

(defn- fetch-drink-now-wines
  [tx current-year]
  (jdbc/execute! tx
                 (sql/format
                  {:select [:id :producer :name :vintage :region :quantity]
                   :from :wines
                   :where [:and [:> :quantity 0]
                           [:<= :drink_from_year current-year]
                           [:>= :drink_until_year current-year]]
                   :order-by [[:drink_until_year :asc] [:quantity :desc]]
                   :limit 50})
                 db-opts))

(defn- fetch-expiring-wines
  [tx current-year]
  (jdbc/execute! tx
                 (sql/format {:select [:id :producer :name :vintage :region
                                       :quantity :drink_until_year]
                              :from :wines
                              :where [:and [:> :quantity 0]
                                      [:<= :drink_until_year (+ current-year 1)]
                                      [:>= :drink_until_year current-year]]
                              :order-by [[:drink_until_year :asc]]})
                 db-opts))

(defn- fetch-recent-activity
  [tx last-week-date]
  (jdbc/execute! tx
                 (sql/format {:select [:ih.* :w.producer :w.name :w.vintage]
                              :from [[:inventory_history :ih]]
                              :join [[:wines :w] [:= :ih.wine_id :w.id]]
                              :where [:>= :ih.created_at last-week-date]
                              :order-by [[:ih.created_at :desc]]})
                 db-opts))

(defn- fetch-past-prime-wines
  [tx current-year]
  (jdbc/execute! tx
                 (sql/format {:select [:id :producer :name :vintage :region
                                       :quantity :drink_until_year]
                              :from :wines
                              :where [:and [:> :quantity 0]
                                      [:< :drink_until_year current-year]]
                              :order-by [[:drink_until_year :asc]]})
                 db-opts))

(defn- fetch-recently-added-wines
  [tx last-week-date]
  (jdbc/execute! tx
                 (sql/format {:select [:id :producer :name :vintage :region
                                       :quantity :created_at]
                              :from :wines
                              :where [:>= :created_at last-week-date]
                              :order-by [[:created_at :desc]]})
                 db-opts))

(defn- select-highlight-wine
  [_tx drink-now-wines]
  (when (seq drink-now-wines) (rand-nth (take 20 drink-now-wines))))

(defn- generate-report-data
  [tx]
  (let [{:keys [today current-year last-week]} (get-date-range)
        drink-now (fetch-drink-now-wines tx current-year)
        expiring (fetch-expiring-wines tx current-year)
        past-prime (fetch-past-prime-wines tx current-year)
        recent-added (fetch-recently-added-wines tx last-week)
        recent-activity (fetch-recent-activity tx last-week)
        highlight (select-highlight-wine tx drink-now)]
    {:generated-at (str today)
     :period-start (str last-week)
     :period-end (str today)
     :drink-now-count (count drink-now)
     :expiring-count (count expiring)
     :past-prime-count (count past-prime)
     :recently-added-count (count recent-added)
     :recent-activity-count (count recent-activity)
     :highlight-wine highlight
     :drink-now-sample (take 5 drink-now)
     :expiring-sample (take 5 expiring)
     :past-prime-sample (take 5 past-prime)
     :recently-added-sample (take 5 recent-added)
     :recent-sample (take 5 recent-activity)
     ;; Full ID lists for interaction
     :drink-now-ids (mapv :id drink-now)
     :expiring-ids (mapv :id expiring)
     :past-prime-ids (mapv :id past-prime)
     :recently-added-ids (mapv :id recent-added)
     :recent-activity-ids (mapv :wine_id recent-activity)}))

(defn- save-report!
  [tx report-date data-json ai-text highlight-id]
  (jdbc/execute-one! tx
                     (sql/format
                      {:insert-into :cellar_reports
                       :values [{:report_date report-date
                                 :summary_data [:cast data-json :jsonb]
                                 :ai_commentary ai-text
                                 :highlight_wine_id highlight-id}]
                       :on-conflict [:report_date]
                       :do-update-set {:summary_data [:cast data-json :jsonb]
                                       :ai_commentary ai-text
                                       :highlight_wine_id highlight-id
                                       :updated_at [:now]}})
                     db-opts))

(defn list-reports
  []
  (jdbc/execute! ds
                 (sql/format {:select [:id :report_date]
                              :from :cellar_reports
                              :order-by [[:report_date :desc]]})
                 db-opts))

(defn get-report-by-id
  [id]
  (jdbc/execute-one! ds
                     (sql/format
                      {:select [:*] :from :cellar_reports :where [:= :id id]})
                     db-opts))

(defn generate-report!
  "Generates and saves a cellar report. Returns the report record."
  ([] (generate-report! {}))
  ([{:keys [force? provider]}]
   (jdbc/with-transaction
    [tx ds]
    (let [{:keys [report-date]} (get-date-range)
          _ (tap> ["Fetching cellar report for date:" report-date])
          existing (jdbc/execute-one! tx
                                      (sql/format {:select [:*]
                                                   :from :cellar_reports
                                                   :where [:= :report_date
                                                           report-date]})
                                      db-opts)
          ;; Check if the existing report has the new ID fields (stale
          ;; check)
          summary-data (:summary_data existing)
          has-ids? (boolean (and summary-data
                                 (:drink-now-ids summary-data)
                                 (:past-prime-ids summary-data)
                                 (:recently-added-ids summary-data)))
          stale? (and existing (not has-ids?))
          should-generate? (or (nil? existing) stale? force?)
          selected-provider (or provider :gemini)]
      (if (not should-generate?)
        (do (tap> "Returning existing report from database") existing)
        (let
          [_ (tap> [(cond
                      force? "Forcing regeneration..."
                      stale?
                      "Existing report is stale (missing IDs). Regenerating..."
                      :else "No existing report found. Generating...")
                    "with provider:" selected-provider])
           data (generate-report-data tx)
           _ (tap> ["Generated data keys:" (keys data)])
           model-name (get-in (ai/get-model-info) [:models selected-provider])
           data (assoc data
                       :ai-provider (name selected-provider)
                       :ai-model model-name)
           data-json (json/write-value-as-string data)
           ai-response
           (try
             (ai/generate-report-commentary selected-provider data-json)
             (catch Exception e
               (tap> ["AI Report Generation Failed:" (.getMessage e)])
               "The sommelier is currently unavailable to provide commentary, but your cellar statistics have been updated."))
           highlight-id (get-in data [:highlight-wine :id])]
          (save-report! tx report-date data-json ai-response highlight-id)
          (jdbc/execute-one! tx
                             (sql/format {:select [:*]
                                          :from :cellar_reports
                                          :where [:= :report_date report-date]})
                             db-opts)))))))
