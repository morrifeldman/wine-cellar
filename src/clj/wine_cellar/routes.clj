(ns wine-cellar.routes
  (:require [wine-cellar.handlers :as handlers]
            [wine-cellar.common :as common]
            [wine-cellar.auth.core :as auth]
            [clojure.spec.alpha :as s]
            [reitit.ring :as ring]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response]
            [muuntaja.core :as m]
            [expound.alpha :as expound]
            [wine-cellar.config-utils :as config-utils]
            [mount.core :refer [defstate]]))

;; Specs for individual fields
(s/def ::producer string?)
(s/def ::country string?)
(s/def ::region string?)
(s/def ::aoc (s/nilable string?))
(s/def ::classification (s/nilable string?))
(s/def ::vineyard (s/nilable string?))
(s/def ::name string?)
(s/def ::vintage (s/nilable int?))
(s/def ::style (set common/wine-styles))
(s/def ::level (s/nilable (set common/wine-levels)))
(s/def ::levels (s/coll-of (set common/wine-levels)))
(s/def ::location (s/and string? #(common/valid-location? %)))
(s/def ::quantity int?)
(s/def ::price number?)
(s/def ::purchase_date (s/nilable string?)) ;; Will be parsed to a date
(s/def ::tasting_date (s/nilable string?)) ;; Will be parsed to a date
(s/def ::notes string?)
(s/def ::rating (s/int-in 1 101)) ;; Ratings from 1-100
(s/def ::drink_from_year (s/nilable int?))
(s/def ::drink_until_year (s/nilable int?))
(s/def ::alcohol_percentage (s/nilable number?))
(s/def ::purveyor string?)
(s/def ::is_external boolean?)
(s/def ::source (s/nilable string?))
(s/def ::label_image string?)
(s/def ::label_thumbnail string?)
(s/def ::back_label_image string?)
(s/def ::variety_id int?)
(s/def ::variety_name string?)
(s/def ::percentage (s/nilable (s/and number? #(<= 0 % 100))))
(s/def ::wine_variety (s/keys :req-un [::variety_id] :opt-un [::percentage]))
(s/def ::wine_varieties (s/coll-of ::wine_variety))

(def grape-variety-schema (s/keys :req-un [::variety_name]))

(def wine-schema
  (s/keys :req-un [(or ::name ::producer) ::country ::region ::style ::quantity
                   ::price]
          :opt-un [::aoc ::classification ::vineyard ::location ::level
                   ::purveyor ::label_image ::label_thumbnail ::back_label_image
                   ::drink_from_year ::drink_until_year ::vintage
                   ::purchase_date ::alcohol_percentage ::wine_varieties]))

(def wine-update-schema
  (s/keys :req-un [(or ::producer
                       ::country ::region
                       ::aoc ::classification
                       ::vineyard ::name
                       ::vintage ::style
                       ::level ::location
                       ::quantity ::price
                       ::purveyor ::label_image
                       ::label_thumbnail ::back_label_image
                       ::drink_from_year ::drink_until_year
                       ::purchase_date ::alcohol_percentage)]
          :opt-un [::producer ::country ::region ::aoc ::classification
                   ::vineyard ::name ::vintage ::style ::level ::location
                   ::quantity ::price ::purveyor ::label_image ::label_thumbnail
                   ::back_label_image ::drink_from_year ::drink_until_year
                   ::purchase_date ::alcohol_percentage]))

(s/def ::nilable-label_image (s/nilable ::label_image))
(s/def ::nilable-label_thumbnail (s/nilable ::label_thumbnail))
(s/def ::nilable-back_label_image (s/nilable ::back_label_image))

(def image-update-schema
  (s/nilable (s/keys :opt-un
                     [::nilable-label_image ::nilable-label_thumbnail
                      ::nilable-back_label_image])))

(def classification-schema
  (s/keys :req-un [::country ::region]
          :opt-un [::aoc ::classification ::vineyard ::levels]))

(def tasting-note-schema
  (s/keys :req-un [::notes ::rating]
          :opt-un [::tasting_date ::is_external ::source]))

(defstate cors-middleware
          :start
          {:name ::cors
           :wrap (fn [handler]
                   (if-not config-utils/production?
                     (wrap-cors handler
                                :access-control-allow-origin
                                [(re-pattern config-utils/frontend)]
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
  ["/auth/google"
   {:get {:summary "Redirect to Google for authentication"
          :handler (fn [request] (auth/redirect-to-google request))}}]
  ["/auth/google/callback"
   {:get {:summary "Handle Google OAuth callback"
          :handler auth/handle-google-callback}}]
  ["/auth/logout" {:get {:summary "Logout user" :handler auth/logout}}]
  ;; Protected API routes - require authentication
  ["/api" {:middleware [auth/require-authentication]}
   ;; Admin Routes
   ["/admin" {:middleware [auth/require-admin]}
    ["/schema"
     {:post
      {:summary "Reset database schema while preserving data"
       :description
       "This endpoint exports all data, drops all tables, recreates the schema, and imports the data back. Use this to apply schema changes without migrations."
       :tags ["Admin"]
       :responses {200 {:body map?} 500 {:body map?}}
       :handler handlers/reset-schema}}]]
   ;; Grape Varieties Routes
   ["/grape-varieties"
    {:get {:summary "Get all grape varieties"
           :responses {200 {:body vector?} 500 {:body map?}}
           :handler handlers/get-grape-varieties}
     :post {:summary "Create a new grape variety"
            :parameters {:body grape-variety-schema}
            :responses {201 {:body map?} 400 {:body map?} 500 {:body map?}}
            :handler handlers/create-grape-variety}}]
   ["/grape-varieties/:id"
    {:parameters {:path {:id int?}}
     :get {:summary "Get grape variety by ID"
           :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
           :handler handlers/get-grape-variety}
     :put {:summary "Update grape variety"
           :parameters {:body grape-variety-schema}
           :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
           :handler handlers/update-grape-variety}
     :delete {:summary "Delete grape variety"
              :responses {204 {:body nil?} 404 {:body map?} 500 {:body map?}}
              :handler handlers/delete-grape-variety}}]
   ;; Wine Classification Routes
   ["/classifications"
    {:get {:summary "Get all wine classifications"
           :responses {200 {:body vector?} 500 {:body map?}}
           :handler handlers/get-classifications}
     :post {:summary "Create a new wine classification"
            :parameters {:body classification-schema}
            :responses {201 {:body map?} 400 {:body map?} 500 {:body map?}}
            :handler handlers/create-classification}}]
   ["/classifications/:id"
    {:parameters {:path {:id int?}}
     :get {:summary "Get classification by ID"
           :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
           :handler handlers/get-classification}
     :put {:summary "Update classification"
           :parameters {:body classification-schema}
           :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
           :handler handlers/update-classification}
     :delete {:summary "Delete classification"
              :responses {204 {:body nil?} 404 {:body map?} 500 {:body map?}}
              :handler handlers/delete-classification}}]
   ["/classifications/regions/:country"
    {:parameters {:path {:country string?}}
     :get {:summary "Get regions for a country"
           :responses {200 {:body vector?} 500 {:body map?}}
           :handler handlers/get-regions-by-country}}]
   ["/classifications/aocs/:country/:region"
    {:parameters {:path {:country string? :region string?}}
     :get {:summary "Get AOCs for a region"
           :responses {200 {:body vector?} 500 {:body map?}}
           :handler handlers/get-aocs-by-region}}]
   ;; Wine Routes
   ["/wines"
    {:get {:summary "Get all wines"
           :responses {200 {:body vector?} 500 {:body map?}}
           :handler handlers/get-all-wines-with-ratings}
     :post {:summary "Create a new wine"
            :parameters {:body wine-schema}
            :responses {201 {:body map?} 400 {:body map?} 500 {:body map?}}
            :handler handlers/create-wine}}]
   ["/wines/analyze-label"
    {:post {:summary "Analyze wine label images with AI"
            :parameters {:body (s/keys :req-un [::label_image]
                                       :opt-un [::back_label_image])}
            :responses {200 {:body map?} 400 {:body map?} 500 {:body map?}}
            :handler handlers/analyze-wine-label}}]
   ["/wines/suggest-drinking-window"
    {:post {:summary "Suggest optimal drinking window for a wine using AI"
            :parameters {:body {:wine map?}}
            :responses {200 {:body map?} 400 {:body map?} 500 {:body map?}}
            :handler handlers/suggest-drinking-window}}]
   ["/wines/by-id"
    ["/:id"
     {:parameters {:path {:id int?}}
      :get {:summary "Get wine by ID"
            :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
            :handler handlers/get-wine}
      :put {:summary "Update wine"
            :parameters {:body wine-update-schema}
            :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
            :handler handlers/update-wine}
      :delete {:summary "Delete wine"
               :responses {204 {:body nil?} 404 {:body map?} 500 {:body map?}}
               :handler handlers/delete-wine}}]
    ["/:id/varieties"
     {:parameters {:path {:id int?}}
      :get {:summary "Get grape varieties for a wine"
            :responses {200 {:body vector?} 404 {:body map?} 500 {:body map?}}
            :handler handlers/get-wine-varieties}
      :post
      {:summary "Add grape variety to wine"
       :parameters {:body ::wine_variety}
       :responses
       {201 {:body map?} 400 {:body map?} 404 {:body map?} 500 {:body map?}}
       :handler handlers/add-variety-to-wine}}]
    ["/:id/varieties/:variety-id"
     {:parameters {:path {:id int? :variety-id int?}}
      :put
      {:summary "Update grape variety percentage for wine"
       :parameters {:body {:percentage ::percentage}}
       :responses
       {200 {:body map?} 400 {:body map?} 404 {:body map?} 500 {:body map?}}
       :handler handlers/update-wine-variety-percentage}
      :delete {:summary "Remove grape variety from wine"
               :responses {204 {:body nil?} 404 {:body map?} 500 {:body map?}}
               :handler handlers/remove-variety-from-wine}}]
    ["/:id/adjust-quantity"
     {:parameters {:path {:id int?}}
      :post {:summary "Adjust wine quantity"
             :parameters {:body {:adjustment int?}}
             :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
             :handler handlers/adjust-quantity}}]
    ["/:id/image"
     {:parameters {:path {:id int?}}
      :put {:summary "Upload wine label image"
            :parameters {:body image-update-schema}
            :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
            :handler handlers/update-wine}}]
    ;; Tasting Notes Routes
    ["/:id/tasting-notes"
     {:parameters {:path {:id int?}}
      :get {:summary "Get all tasting notes for a wine"
            :responses {200 {:body vector?} 404 {:body map?} 500 {:body map?}}
            :handler handlers/get-tasting-notes-by-wine}
      :post
      {:summary "Create a tasting note for a wine"
       :parameters {:body tasting-note-schema}
       :responses
       {201 {:body map?} 404 {:body map?} 400 {:body map?} 500 {:body map?}}
       :handler handlers/create-tasting-note}}]
    ["/:id/tasting-notes/:note-id"
     {:parameters {:path {:id int? :note-id int?}}
      :get {:summary "Get tasting note by ID"
            :responses {200 {:body map?} 404 {:body map?} 500 {:body map?}}
            :handler handlers/get-tasting-note}
      :put
      {:summary "Update tasting note"
       :parameters {:body tasting-note-schema}
       :responses
       {200 {:body map?} 404 {:body map?} 400 {:body map?} 500 {:body map?}}
       :handler handlers/update-tasting-note}
      :delete {:summary "Delete tasting note"
               :responses {204 {:body nil?} 404 {:body map?} 500 {:body map?}}
               :handler handlers/delete-tasting-note}}]]]])

(defn coercion-error-handler
  [status]
  (fn [exception _]
    (let [data (ex-data exception)
          ;; Generate the human-readable error message with expound
          human-readable-error (expound/expound-str (:spec data) (:value data))]
      ;; Print to server logs
      (println "Validation error:")
      (println human-readable-error)
      ;; Return the expound output directly to the client
      {:status status :body human-readable-error})))

(defstate
 app
 :start
 (ring/ring-handler
  (ring/router
   wine-routes
   {:data
    {:coercion spec-coercion/coercion
     :muuntaja m/instance
     :swagger {:ui "/api-docs"
               :spec "/swagger.json"
               :data {:info {:title "Wine Cellar API"
                             :description
                             "API for managing your wine collection"}}}
     :middleware
     [cors-middleware ;; Move CORS middleware to be
                      ;; first in the chain
      (exception/create-exception-middleware
       (merge exception/default-handlers
              {:reitit.coercion/request-coercion (coercion-error-handler 400)
               :reitit.coercion/response-coercion (coercion-error-handler
                                                   500)}))
      parameters/parameters-middleware muuntaja/format-negotiate-middleware
      muuntaja/format-response-middleware muuntaja/format-request-middleware
      coercion/coerce-request-middleware coercion/coerce-response-middleware
      swagger/swagger-feature auth/wrap-auth]}})
  ; https://github.com/metosin/reitit/blob/master/doc/ring/static.md
  (ring/routes (ring/create-file-handler {:path "/"})
               (ring/create-default-handler))))
