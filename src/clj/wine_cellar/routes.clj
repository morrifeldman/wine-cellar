(ns wine-cellar.routes
  (:require [wine-cellar.handlers :as handlers]
            [wine-cellar.http :as http]
            [wine-cellar.auth.core :as auth]
            [clojure.spec.alpha :as s]
            [wine-cellar.specs :as specs]
            [reitit.ring :as ring]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response]
            [muuntaja.core :as m]
            [wine-cellar.config-utils :as config-utils]
            [wine-cellar.logging :as logging]
            [mount.core :refer [defstate]]))

(defn- tap-middleware-wrap
  [handler]
  (fn [request]
    (let [log-details? (logging/verbose-logging-enabled?)
          request-id (str (random-uuid))
          start (System/nanoTime)
          uri (:uri request)
          request-summary
          (logging/summarize-request request request-id log-details?)]
      (tap> request-summary)
      (try (let [response (handler request)
                 duration-ms (/ (double (- (System/nanoTime) start)) 1e6)
                 response-summary (logging/summarize-response response
                                                              uri
                                                              request-id
                                                              duration-ms
                                                              log-details?)]
             (tap> response-summary)
             response)
           (catch Exception e
             (let [duration-ms (/ (double (- (System/nanoTime) start)) 1e6)
                   ex-info-e (ex-info "Http Request Error"
                                      {:request-id request-id
                                       :duration-ms duration-ms}
                                      e)]
               (tap> ex-info-e)
               ;; Rethrow the original: the exception middleware dispatches
               ;; on its ex-data :type (e.g. request coercion -> 400).
               (throw e)))))))

(def tap-middleware {:name ::tap :wrap tap-middleware-wrap})

(defstate cors-middleware
          :start
          {:name ::cors
           :wrap (fn [handler]
                   (if-not config-utils/production?
                     (wrap-cors handler
                                :access-control-allow-origin
                                config-utils/cors-origins
                                :access-control-allow-methods [:get :put :post
                                                               :delete :options]
                                :access-control-allow-headers
                                ["Content-Type" "Accept" "Authorization"]
                                :access-control-allow-credentials true)
                     handler))})

(defstate
 wine-routes
 :start
 [;; Public routes - no authentication required
  ["/swagger.json"
   {:get {:no-doc true
          :swagger {:info {:title "Wine Cellar API"
                           :description
                           "API for managing your wine collection"}}
          :handler (swagger/create-swagger-handler)}}]
  ["/api-docs/*"
   {:get {:no-doc true :handler (swagger-ui/create-swagger-ui-handler)}}]
  ;; Health check endpoint
  ["/health"
   {:get {:summary "Health check endpoint" :handler handlers/health-check}}]
  ;; Authentication routes
  ["/login"
   {:get {:summary "Login page"
          :handler
          (fn [_] (response/resource-response "index.html" {:root "public"}))}}]
  ["/auth"
   ["/google"
    {:get {:summary "Redirect to Google for authentication"
           :handler (fn [request] (auth/redirect-to-google request))}}]
   ["/google/callback"
    {:get {:summary "Handle Google OAuth callback"
           :handler auth/handle-google-callback}}]
   ["/logout" {:get {:summary "Logout user" :handler auth/logout}}]]
  ;; Device provisioning (unauthenticated)
  ["/api/device-claim"
   {:post {:summary "Submit a device claim code to register"
           :parameters {:body ::specs/device-claim}
           :responses {202 {:body map?}}
           :handler handlers/claim-device}}]
  ["/api/device-claim/poll"
   {:post {:summary "Poll for device approval and obtain initial tokens"
           :parameters {:body ::specs/device-claim}
           :responses {200 {:body map?}}
           :handler handlers/poll-device-claim}}]
  ["/api/device-token"
   {:post {:summary "Rotate device JWT using refresh token"
           :parameters {:body ::specs/device-token-request}
           :responses {200 {:body map?}}
           :handler handlers/refresh-device-token}}]
  ;; Protected API routes - require authentication
  ["/api" {:middleware [auth/require-authentication]}
   ["/sensor-readings"
    {:post {:summary "Record sensor reading"
            :parameters {:body ::specs/sensor-reading}
            :responses {201 {:body map?}}
            :handler handlers/ingest-sensor-reading}
     :get {:summary "List sensor readings"
           :parameters {:query ::specs/sensor-reading-query}
           :responses {200 {:body vector?}}
           :handler handlers/list-sensor-readings}}]
   ["/sensor-readings/latest"
    {:get {:summary "Most recent reading per device (or for one device)"
           :parameters {:query (s/keys :opt-un [::specs/device_id])}
           :responses {200 {:body vector?}}
           :handler handlers/latest-sensor-readings}}]
   ["/sensor-readings/series"
    {:get {:summary "Aggregated sensor readings bucketed over time"
           :parameters {:query ::specs/series-query}
           :responses {200 {:body vector?}}
           :handler handlers/sensor-reading-series}}]
   ["/reports"
    {:get {:summary "List all cellar insight reports"
           :responses {200 {:body vector?}}
           :handler handlers/list-reports}}]
   ["/reports/latest"
    {:get {:summary "Get the latest cellar insight report"
           :responses {200 {:body map?}}
           :handler handlers/get-latest-report}}]
   ["/reports/by-id/:id"
    {:parameters {:path {:id int?}}
     :get {:summary "Get a cellar insight report by ID"
           :responses {200 {:body map?}}
           :handler handlers/get-report}}]
   ["/cocktail-recipe-extract"
    {:post {:summary "Extract recipe from text or image using AI"
            :parameters {:body (s/keys :opt-un
                                       [::specs/message-text ::specs/image])}
            :responses {200 {:body map?}}
            :handler handlers/extract-cocktail-recipe}}]
   ["/chat"
    {:post {:summary "Chat with AI about your wine collection"
            :parameters
            {:body (s/keys :req-un [::specs/provider]
                           :opt-un [::specs/message ::specs/conversation-history
                                    ::specs/image ::specs/include-bar?
                                    ::specs/effort])}
            :responses {200 {:body string?}}
            :handler handlers/chat-with-ai}}]
   ["/conversations"
    {:get {:summary "List AI conversations for the authenticated user"
           :parameters {:query (s/keys :opt-un
                                       [::specs/search-text ::specs/chat_type])}
           :responses {200 {:body vector?}}
           :handler handlers/list-conversations}
     :post {:summary "Create a new AI conversation"
            :parameters {:body ::specs/conversation-create}
            :responses {201 {:body map?}}
            :handler handlers/create-conversation}}]
   ["/conversations/:id"
    {:parameters {:path {:id int?}}
     :put {:summary "Update an AI conversation"
           :parameters {:body ::specs/conversation-update}
           :responses {200 {:body map?}}
           :handler handlers/update-conversation}
     :delete {:summary "Delete an AI conversation"
              :responses {204 {:body nil?}}
              :handler handlers/delete-conversation}}]
   ["/conversations/:id/messages"
    {:parameters {:path {:id int?}}
     :get {:summary "List messages for a conversation"
           :responses {200 {:body vector?}}
           :handler handlers/list-conversation-messages}
     :post {:summary "Append a new message to a conversation"
            :parameters {:body ::specs/conversation-message}
            :responses {201 {:body map?}}
            :handler handlers/append-conversation-message}}]
   ["/conversations/:id/fork"
    {:parameters {:path {:id int?}}
     :post {:summary
            "Copy a conversation up to and including one of its messages"
            :parameters {:body (s/keys :req-un [::specs/message_count])}
            :responses {201 {:body map?}}
            :handler handlers/fork-conversation}}]
   ["/conversations/:id/messages/:message-id"
    {:parameters {:path {:id int? :message-id int?}}
     :put {:summary "Update a conversation message"
           :parameters {:body ::specs/conversation-message-update}
           :responses {200 {:body map?}}
           :handler handlers/update-conversation-message}}]
   ["/tasting-note-sources"
    {:get {:summary "Get unique tasting note sources for suggestions"
           :responses {200 {:body ::specs/tasting-sources}}
           :handler handlers/get-tasting-note-sources}}]
   ["/blind-tastings"
    {:get {:summary "Get all blind tasting notes (linked and unlinked)"
           :responses {200 {:body vector?}}
           :handler handlers/get-blind-tastings}
     :post {:summary "Create a blind tasting note (no wine attached)"
            :parameters {:body specs/tasting-note-schema}
            :responses {201 {:body map?}}
            :handler handlers/create-blind-tasting}}]
   ["/blind-tastings/:id/link"
    {:parameters {:path {:id int?}}
     :put {:summary "Link a blind tasting note to a wine"
           :parameters {:body (s/keys :req-un [::specs/wine_id])}
           :responses {200 {:body map?}}
           :handler handlers/link-blind-tasting}}]
   ;; Bar routes
   ["/spirits"
    {:get {:summary "List all spirits"
           :responses {200 {:body vector?}}
           :handler handlers/get-spirits}
     :post {:summary "Create a new spirit"
            :parameters {:body specs/spirit-schema}
            :responses {201 {:body map?}}
            :handler handlers/create-spirit}}]
   ["/spirits/analyze-label"
    {:post {:summary "Analyze spirit label image"
            :parameters {:body (s/keys :req-un
                                       [::specs/label_image ::specs/provider])}
            :responses {200 {:body map?}}
            :handler handlers/analyze-spirit-label}}]
   ["/spirits/:id"
    {:parameters {:path {:id int?}}
     :get {:summary "Get spirit by ID"
           :responses {200 {:body map?}}
           :handler handlers/get-spirit}
     :put {:summary "Update spirit"
           :parameters {:body specs/spirit-update-schema}
           :responses {200 {:body map?}}
           :handler handlers/update-spirit}
     :delete {:summary "Delete spirit"
              :responses {204 {:body nil?}}
              :handler handlers/delete-spirit}}]
   ["/bar-inventory"
    {:get {:summary "List all bar inventory items"
           :responses {200 {:body vector?}}
           :handler handlers/get-bar-inventory}
     :post {:summary "Add a custom bar inventory item"
            :parameters {:body specs/bar-inventory-item-schema}
            :responses {201 {:body map?}}
            :handler handlers/create-bar-inventory-item}}]
   ["/bar-inventory/:id"
    {:parameters {:path {:id int?}}
     :put {:summary "Update bar inventory item (e.g. toggle have_it)"
           :parameters {:body (s/keys :opt-un
                                      [::specs/have_it ::specs/name
                                       ::specs/sort_order])}
           :responses {200 {:body map?}}
           :handler handlers/update-bar-inventory-item}
     :delete {:summary "Delete bar inventory item"
              :responses {204 {:body nil?}}
              :handler handlers/delete-bar-inventory-item}}]
   ["/cocktail-recipes"
    {:get {:summary "List all cocktail recipes"
           :responses {200 {:body vector?}}
           :handler handlers/get-cocktail-recipes}
     :post {:summary "Create a new cocktail recipe"
            :parameters {:body specs/cocktail-recipe-schema}
            :responses {201 {:body map?}}
            :handler handlers/create-cocktail-recipe}}]
   ["/cocktail-recipes/:id"
    {:parameters {:path {:id int?}}
     :get {:summary "Get cocktail recipe by ID"
           :responses {200 {:body map?}}
           :handler handlers/get-cocktail-recipe}
     :put {:summary "Update cocktail recipe"
           :parameters {:body specs/cocktail-recipe-update-schema}
           :responses {200 {:body map?}}
           :handler handlers/update-cocktail-recipe}
     :delete {:summary "Delete cocktail recipe"
              :responses {204 {:body nil?}}
              :handler handlers/delete-cocktail-recipe}}]
   ["/cocktail-recipes/:id/refresh-links"
    {:parameters {:path {:id int?}}
     :post {:summary
            "Re-resolve a recipe's spirit/ingredient links to current inventory"
            :responses {200 {:body map?}}
            :handler handlers/refresh-recipe-links}}]
   ["/admin"
    ["/model-info"
     {:get {:summary "Get current AI model configuration"
            :responses {200 {:body map?}}
            :handler handlers/get-model-info}}]
    ["/schema"
     {:get {:summary "Admin: Get database schema"
            :responses {200 {:body vector?}}
            :handler handlers/get-db-schema}}]
    ["/sql"
     {:post {:summary "Admin: Execute raw SQL query"
             :parameters {:body (s/keys :req-un [::specs/query])}
             :responses {200 {:body vector?}}
             :handler handlers/execute-sql-query}}]
    ["/reset-database"
     {:post {:summary "Admin: Drop and recreate all database tables"
             :responses {200 {:body map?}}
             :handler handlers/reset-database}}]
    ["/mark-all-unverified"
     {:post {:summary
             "Admin: Mark all wines as unverified for inventory verification"
             :responses {200 {:body map?}}
             :handler handlers/mark-all-wines-unverified}}]
    ["/reextract-recipe-timers"
     {:post {:summary "Admin: Re-read every recipe's timed steps with AI"
             :responses {200 {:body map?}}
             :handler handlers/reextract-recipe-timers}}]
    ["/devices"
     {:get {:summary "Admin: List provisioned devices"
            :responses {200 {:body vector?}}
            :handler handlers/list-devices-admin}}]
    ["/devices/:device_id/approve"
     {:parameters {:path {:device_id ::specs/device_id}
                   :body (s/keys :req-un [::specs/claim_code])}
      :post {:summary "Admin: Approve device claim (requires claim_code)"
             :responses {200 {:body map?}}
             :handler handlers/approve-device}}]
    ["/devices/:device_id/block"
     {:parameters {:path {:device_id ::specs/device_id}}
      :post {:summary "Admin: Block a device and clear tokens"
             :responses {200 {:body map?}}
             :handler handlers/block-device}}]
    ["/devices/:device_id/unblock"
     {:parameters {:path {:device_id ::specs/device_id}}
      :post {:summary "Admin: Unblock a device (sets pending, clears tokens)"
             :responses {200 {:body map?}}
             :handler handlers/unblock-device}}]
    ["/devices/:device_id/delete"
     {:parameters {:path {:device_id ::specs/device_id}}
      :delete {:summary "Admin: Delete a device"
               :responses {204 {:body nil?}}
               :handler handlers/delete-device}}]
    ["/devices/:device_id/sensor-config"
     {:parameters {:path {:device_id ::specs/device_id}}
      :put {:summary "Admin: Update sensor labels for a device"
            :parameters {:body ::specs/sensor_config}
            :responses {200 {:body map?}}
            :handler handlers/update-device-sensor-config}}]
    ["/start-drinking-window-job"
     {:post {:summary "Start async job to regenerate drinking windows"
             :parameters {:body (s/keys :req-un
                                        [::specs/wine-ids ::specs/provider])}
             :responses {200 {:body map?}}
             :handler handlers/start-drinking-window-job}}]
    ["/start-wine-summary-job"
     {:post {:summary "Start async job to regenerate wine summaries"
             :parameters {:body (s/keys :req-un
                                        [::specs/wine-ids ::specs/provider])}
             :responses {200 {:body map?}}
             :handler handlers/start-wine-summary-job}}]
    ["/verbose-logging"
     {:get {:summary "Get HTTP verbose logging state"
            :responses {200 {:body map?}}
            :handler handlers/get-verbose-logging-state}
      :post {:summary "Set HTTP tap logging state"
             :parameters {:body (s/keys :req-un [::specs/enabled?])}
             :responses {200 {:body map?}}
             :handler handlers/set-verbose-logging-state}}]
    ["/job-status/:job-id"
     {:get {:summary "Get status of async job"
            :parameters {:path {:job-id string?}}
            :responses {200 {:body map?}}
            :handler handlers/get-job-status}}]]
   ["/grape-varieties"
    {:get {:summary "Get all grape varieties"
           :responses {200 {:body vector?}}
           :handler handlers/get-grape-varieties}
     :post {:summary "Create a new grape variety"
            :parameters {:body specs/grape-variety-schema}
            :responses {201 {:body map?}}
            :handler handlers/create-grape-variety}}]
   ["/grape-varieties/:id"
    {:parameters {:path {:id int?}}
     :get {:summary "Get grape variety by ID"
           :responses {200 {:body map?}}
           :handler handlers/get-grape-variety}
     :put {:summary "Update grape variety"
           :parameters {:body specs/grape-variety-schema}
           :responses {200 {:body map?}}
           :handler handlers/update-grape-variety}
     :delete {:summary "Delete grape variety"
              :responses {204 {:body nil?}}
              :handler handlers/delete-grape-variety}}]
   ["/classifications"
    [""
     {:get {:summary "Get all wine classifications"
            :responses {200 {:body vector?}}
            :handler handlers/get-classifications}
      :post {:summary "Create a new wine classification"
             :parameters {:body specs/classification-schema}
             :responses {201 {:body map?}}
             :handler handlers/create-classification}}]
    ["/regions/:country"
     {:parameters {:path {:country string?}}
      :get {:summary "Get regions for a country"
            :responses {200 {:body vector?}}
            :handler handlers/get-regions-by-country}}]
    ["/appellations/:country/:region"
     {:parameters {:path {:country string? :region string?}}
      :get {:summary "Get appellations (AOCs/AVAs) for a region"
            :responses {200 {:body vector?}}
            :handler handlers/get-appellations-by-region}}]
    ["/:id"
     {:parameters {:path {:id int?}}
      :get {:summary "Get classification by ID"
            :responses {200 {:body map?}}
            :handler handlers/get-classification}
      :put {:summary "Update classification"
            :parameters {:body specs/classification-schema}
            :responses {200 {:body map?}}
            :handler handlers/update-classification}
      :delete {:summary "Delete classification"
               :responses {204 {:body nil?}}
               :handler handlers/delete-classification}}]]
   ["/wines"
    [""
     {:post {:summary "Create a new wine"
             :parameters {:body specs/wine-schema}
             :responses {201 {:body map?}}
             :handler handlers/create-wine}}]
    ["/list"
     {:get {:summary "Get all wines for list view"
            :responses {200 {:body vector?}}
            :handler handlers/get-wines-for-list}}]
    ["/technical-data-keys"
     {:get {:summary "Get all unique technical data keys used in the collection"
            :responses {200 {:body vector?}}
            :handler handlers/get-technical-data-keys}}]
    ["/analyze-label"
     {:post {:summary "Analyze wine label images with AI"
             :parameters {:body (s/keys :req-un [::specs/label_image
                                                 ::specs/provider]
                                        :opt-un [::specs/back_label_image])}
             :responses {200 {:body map?}}
             :handler handlers/analyze-wine-label}}]
    ["/suggest-drinking-window"
     {:post {:summary "Suggest optimal drinking window for a wine using AI"
             :parameters {:body (s/keys :req-un
                                        [::specs/wine ::specs/provider])}
             :responses {200 {:body map?}}
             :handler handlers/suggest-drinking-window}}]
    ["/generate-summary"
     {:post
      {:summary
       "Generate comprehensive wine summary with taste profile and food pairings using AI"
       :parameters {:body (s/keys :req-un [::specs/wine ::specs/provider])}
       :responses {200 {:body string?}}
       :handler handlers/generate-wine-summary}}]
    ["/history/:history-id"
     {:parameters {:path {:history-id int?}}
      :put {:summary "Update inventory history record"
            :parameters {:body (s/keys :opt-un
                                       [::specs/occurred_at ::specs/reason
                                        ::specs/notes ::specs/oz
                                        ::specs/change_amount])}
            :responses {200 {:body map?}}
            :handler handlers/update-inventory-history}
      :delete {:summary "Delete inventory history record"
               :responses {204 {:body nil?}}
               :handler handlers/delete-inventory-history}}]
    ["/by-id/:id" {:parameters {:path {:id int?}}}
     [""
      {:get
       {:summary "Get wine by ID"
        :description
        "Get wine by ID. Use query parameter ?include_images=true to include full-size images."
        :parameters {:query (s/keys :opt-un [::specs/include_images])}
        :responses {200 {:body map?}}
        :handler handlers/get-wine}
       :put {:summary "Update wine"
             :parameters {:body specs/wine-update-schema}
             :responses {200 {:body map?}}
             :handler handlers/update-wine}
       :delete {:summary "Delete wine"
                :responses {204 {:body nil?}}
                :handler handlers/delete-wine}}]
     ["/adjust-quantity"
      {:post {:summary "Adjust wine quantity"
              :parameters {:body (s/keys :req-un [::specs/adjustment]
                                         :opt-un [::specs/reason ::specs/notes
                                                  ::specs/occurred_at])}
              :responses {200 {:body map?}}
              :handler handlers/adjust-quantity}}]
     ["/coravin-pour"
      {:post {:summary "Record a Coravin pour from an open or new bottle"
              :parameters {:body (s/keys :req-un [::specs/oz]
                                         :opt-un [::specs/notes])}
              :responses {200 {:body map?}}
              :handler handlers/coravin-pour}}]
     ["/finish-open-bottle"
      {:post {:summary "Mark the currently open bottle as finished"
              :parameters {:body (s/keys :opt-un [::specs/notes])}
              :responses {200 {:body map?}}
              :handler handlers/finish-open-bottle}}]
     ["/history"
      {:get {:summary "Get inventory history for a wine"
             :responses {200 {:body vector?}}
             :handler handlers/get-inventory-history}}]
     ["/image"
      {:put {:summary "Upload wine label image"
             :parameters {:body specs/image-update-schema}
             :responses {200 {:body map?}}
             :handler handlers/update-wine}}]
     ["/varieties"
      {:get {:summary "Get grape varieties for a wine"
             :responses {200 {:body vector?}}
             :handler handlers/get-wine-varieties}
       :post {:summary "Add grape variety to wine"
              :parameters {:body ::specs/wine_variety}
              :responses {201 {:body map?}}
              :handler handlers/add-variety-to-wine}}]
     ["/varieties/:variety-id"
      {:parameters {:path {:variety-id int?}}
       :put {:summary "Update grape variety percentage for wine"
             :parameters {:body {:percentage ::specs/percentage}}
             :responses {200 {:body map?}}
             :handler handlers/update-wine-variety-percentage}
       :delete {:summary "Remove grape variety from wine"
                :responses {204 {:body nil?}}
                :handler handlers/remove-variety-from-wine}}]
     ["/tasting-notes"
      {:get {:summary "Get all tasting notes for a wine"
             :responses {200 {:body vector?}}
             :handler handlers/get-tasting-notes-by-wine}
       :post {:summary "Create a tasting note for a wine"
              :parameters {:body specs/tasting-note-schema}
              :responses {201 {:body map?}}
              :handler handlers/create-tasting-note}}]
     ["/tasting-notes/:note-id"
      {:parameters {:path {:note-id int?}}
       :get {:summary "Get tasting note by ID"
             :responses {200 {:body map?}}
             :handler handlers/get-tasting-note}
       :put {:summary "Update tasting note"
             :parameters {:body specs/tasting-note-schema}
             :responses {200 {:body map?}}
             :handler handlers/update-tasting-note}
       :delete {:summary "Delete tasting note"
                :responses {204 {:body nil?}}
                :handler handlers/delete-tasting-note}}]]]]])

(defstate app
          :start
          (ring/ring-handler
           (ring/router
            wine-routes
            {:conflicts nil
             :data
             {:coercion spec-coercion/coercion
              :muuntaja m/instance
              :swagger {:ui "/api-docs"
                        :spec "/swagger.json"
                        :data {:info {:title "Wine Cellar API"
                                      :description
                                      "API for managing your wine collection"}}}
              :middleware [cors-middleware ;; First, so error responses
                                           ;; carry CORS headers too
                           parameters/parameters-middleware
                           muuntaja/format-negotiate-middleware
                           muuntaja/format-response-middleware
                           ;; Inside format-response, so error bodies are
                           ;; encoded like any other.
                           http/exception-middleware
                           muuntaja/format-request-middleware tap-middleware
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware
                           swagger/swagger-feature auth/wrap-auth]}})
           ; https://github.com/metosin/reitit/blob/master/doc/ring/static.md
           (ring/routes (ring/create-file-handler
                         ;; With no index files, / falls through to the
                         ;; index.html handler below. Otherwise reitit
                         ;; redirects / to /index.html, a path in the
                         ;; address bar that the app has no route for.
                         {:path "/" :root "public" :index-files []})
                        (fn [{:keys [request-method]}]
                          (when (= :get request-method)
                            (-> (response/file-response "public/index.html")
                                (response/content-type "text/html"))))
                        (ring/create-default-handler))))
