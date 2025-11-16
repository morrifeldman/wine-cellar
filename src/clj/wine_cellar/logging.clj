(ns wine-cellar.logging)

(defonce verbose-logging-state (atom false))

(defn verbose-logging-enabled? [] @verbose-logging-state)

(defn set-verbose-logging! [flag] (reset! verbose-logging-state (boolean flag)))

(defn verbose-logging-status [] {:verbose? (verbose-logging-enabled?)})

(defn summarize-request
  "Returns a trimmed request map safe for tap logging."
  [request request-id verbose?]
  (let [params (:parameters request)
        path (:path params)
        query (:query params)
        base {:request-id request-id
              :request-method (:request-method request)
              :uri (:uri request)}]
    (if verbose?
      (cond-> base
        (:query-string request) (assoc :query-string (:query-string request))
        (seq path) (assoc :path-parameters path)
        (seq query) (assoc :query-parameters query)
        (get-in request [:user :email]) (assoc :user-email
                                               (get-in request [:user :email])))
      base)))

(defn summarize-response
  "Returns a trimmed request map safe for tap logging."
  [response uri request-id duration-ms verbose?]
  (let [high-level {:uri uri
                    :request-id request-id
                    :duration-ms duration-ms
                    :status (:status response)}]
    (if verbose? (merge high-level response) high-level)))
