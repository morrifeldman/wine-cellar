(ns wine-cellar.logging)

(defonce tap-logging-enabled (atom false))

(defn tap-logging-enabled?
  []
  @tap-logging-enabled)

(defn set-tap-logging!
  [flag]
  (reset! tap-logging-enabled (boolean flag)))

(defn toggle-tap-logging!
  []
  (swap! tap-logging-enabled not))

(defn tap-logging-state
  []
  {:verbose? (tap-logging-enabled?)})

(defn summarize-request
  "Returns a trimmed request map safe for tap logging."
  [request request-id]
  (let [params (:parameters request)
        path (:path params)
        query (:query params)
        base {:request-id request-id
              :method (:request-method request)
              :uri (:uri request)}]
    (cond-> base
      (:query-string request) (assoc :query-string (:query-string request))
      (seq path) (assoc :path-params path)
      (seq query) (assoc :query-params query)
      (get-in request [:user :email]) (assoc :user-email (get-in request [:user :email])))))
