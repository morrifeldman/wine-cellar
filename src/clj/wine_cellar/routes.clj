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
            [wine-cellar.config-utils :as config-utils]))

;; Specs for individual fields
(s/def ::producer string?)
(s/def ::country string?)
(s/def ::region string?)
(s/def ::aoc string?)
(s/def ::communal_aoc string?)
(s/def ::classification string?)
(s/def ::vineyard string?)
(s/def ::name string?)
(s/def ::vintage int?)
(s/def ::style (set common/wine-styles))
(s/def ::level (s/nilable (set common/wine-levels)))
(s/def ::levels (s/coll-of (set common/wine-levels)))
(s/def ::location (s/and string? #(common/valid-location? %)))
(s/def ::quantity int?)
(s/def ::price number?)
(s/def ::tasting_date (s/nilable string?)) ;; Will be parsed to a date
(s/def ::notes string?)
(s/def ::rating (s/int-in 1 101)) ;; Ratings from 1-100
(s/def ::drink_from_year int?)
(s/def ::drink_until_year int?)
(s/def ::purveyor string?)
(s/def ::is_external boolean?)
(s/def ::source string?)
(s/def ::full_image string?)
(s/def ::thumbnail string?)
(s/def ::image-data
  (s/nilable (s/keys :req-un [::full_image ::thumbnail])))

(def wine-schema
  (s/keys :req-un [(or ::name ::producer)
                   ::country
                   ::region
                   ::vintage
                   ::style
                   ::quantity
                   ::price]
          :opt-un [::aoc
                   ::communal_aoc
                   ::classification
                   ::vineyard
                   ::location
                   ::level
                   ::purveyor
                   ::image-data
                   ::drink_from_year
                   ::drink_until_year]))

(def wine-update-schema
  (s/keys :req-un [(or
                    ::producer
                    ::country
                    ::region
                    ::aoc
                    ::communal_aoc
                    ::classification
                    ::vineyard
                    ::name
                    ::vintage
                    ::style
                    ::level
                    ::location
                    ::quantity
                    ::price
                    ::purveyor
                    ::image-data
                    ::drink_from_year
                    ::drink_until_year)]
          :opt-un [::producer
                   ::country
                   ::region
                   ::aoc
                   ::communal_aoc
                   ::classification
                   ::vineyard
                   ::name
                   ::vintage
                   ::style
                   ::level
                   ::location
                   ::quantity
                   ::price
                   ::purveyor
                   ::image-data
                   ::drink_from_year
                   ::drink_until_year]))

(def classification-schema
  (s/keys :req-un [::country
                   ::region]
          :opt-un [::aoc
                   ::communal_aoc
                   ::classification
                   ::vineyard
                   ::levels]))

(def tasting-note-schema
  (s/keys :req-un [::notes
                   ::rating]
          :opt-un [::tasting_date
                   ::is_external
                   ::source]))

(def cors-middleware
  (if-not (config-utils/production?)
    {:name ::cors
     :wrap (fn [handler]
             (wrap-cors handler
                        :access-control-allow-origin [#"http://localhost:8080"]
                        :access-control-allow-methods [:get :put :post :delete :options]
                        :access-control-allow-headers ["Content-Type" "Accept" "Authorization"]
                        :access-control-allow-credentials true))}
    {:name ::cors
     :wrap identity}))  ;; No-op middleware in production

(def wine-routes
  [["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "Wine Cellar API"
                            :description "API for managing your wine collection"}}
           :handler (swagger/create-swagger-handler)}}]

   ["/api-docs/*"
    {:get {:no-doc true
           :handler (swagger-ui/create-swagger-ui-handler)}}]

   ;; Health check endpoint
   ["/health"
    {:get {:summary "Health check endpoint"
           :handler handlers/health-check}}]

   ;; Authentication routes
   ["/login"
    {:get {:summary "Login page"
           :handler (fn [_] (response/resource-response "index.html" {:root "public"}))}}]

   ["/auth/google"
    {:get {:summary "Redirect to Google for authentication"
           :handler (fn [request] (auth/redirect-to-google request))}}]

   ["/auth/google/callback"
    {:get {:summary "Handle Google OAuth callback"
           :handler auth/handle-google-callback}}]

   ["/auth/logout"
    {:get {:summary "Logout user"
           :handler (fn [_]
                      (-> (response/redirect "/")
                          (assoc-in [:cookies "auth-token"] {:value ""
                                                             :max-age 0
                                                             :http-only true
                                                             :same-site :lax
                                                             :path "/"})))}}]
   
   ["/api"
    {:middleware [auth/require-authentication]}
   ;; Wine Classification Routes
    ["/classifications"
     {:get {:summary "Get all wine classifications"
            :responses {200 {:body vector?}
                        500 {:body map?}}
            :handler handlers/get-classifications}
      :post {:summary "Create a new wine classification"
             :parameters {:body classification-schema}
             :responses {201 {:body map?}
                         400 {:body map?}
                         500 {:body map?}}
             :handler handlers/create-classification}}]

    ["/classifications/regions/:country"
     {:parameters {:path {:country string?}}
      :get {:summary "Get regions for a country"
            :responses {200 {:body vector?}
                        500 {:body map?}}
            :handler handlers/get-regions-by-country}}]

    ["/classifications/aocs/:country/:region"
     {:parameters {:path {:country string?
                          :region string?}}
      :get {:summary "Get AOCs for a region"
            :responses {200 {:body vector?}
                        500 {:body map?}}
            :handler handlers/get-aocs-by-region}}]

   ;; Wine Routes
    ["/wines"
     {:get {:summary "Get all wines"
            :responses {200 {:body vector?}
                        500 {:body map?}}
            :handler handlers/get-all-wines-with-ratings}
      :post {:summary "Create a new wine"
             :parameters {:body wine-schema}
             :responses {201 {:body map?}
                         400 {:body map?}
                         500 {:body map?}}
             :handler handlers/create-wine}}]

    ["/wines/:id"
     {:parameters {:path {:id int?}}
      :get {:summary "Get wine by ID"
            :responses {200 {:body map?}
                        404 {:body map?}
                        500 {:body map?}}
            :handler handlers/get-wine}
      :put {:summary "Update wine"
            :parameters {:body wine-update-schema}
            :responses {200 {:body map?}
                        404 {:body map?}
                        500 {:body map?}}
            :handler handlers/update-wine}
      :delete {:summary "Delete wine"
               :responses {204 {:body nil?}
                           404 {:body map?}
                           500 {:body map?}}
               :handler handlers/delete-wine}}]

    ["/wines/:id/adjust-quantity"
     {:parameters {:path {:id int?}}
      :post {:summary "Adjust wine quantity"
             :parameters {:body {:adjustment int?}}
             :responses {200 {:body map?}
                         404 {:body map?}
                         500 {:body map?}}
             :handler handlers/adjust-quantity}}]

    ["/wines/:id/image"
     {:parameters {:path {:id int?}}
      :put {:summary "Upload wine label image"
            :parameters {:body ::image-data}
            :responses {200 {:body map?}
                        404 {:body map?}
                        500 {:body map?}}
            :handler handlers/upload-wine-image}}]

   ;; Tasting Notes Routes
    ["/wines/:id/tasting-notes"
     {:parameters {:path {:id int?}}
      :get {:summary "Get all tasting notes for a wine"
            :responses {200 {:body vector?}
                        404 {:body map?}
                        500 {:body map?}}
            :handler handlers/get-tasting-notes-by-wine}
      :post {:summary "Create a tasting note for a wine"
             :parameters {:body tasting-note-schema}
             :responses {201 {:body map?}
                         404 {:body map?}
                         400 {:body map?}
                         500 {:body map?}}
             :handler handlers/create-tasting-note}}]

    ["/wines/:id/tasting-notes/:note-id"
     {:parameters {:path {:id int?
                          :note-id int?}}
      :get {:summary "Get tasting note by ID"
            :responses {200 {:body map?}
                        404 {:body map?}
                        500 {:body map?}}
            :handler handlers/get-tasting-note}
      :put {:summary "Update tasting note"
            :parameters {:body tasting-note-schema}
            :responses {200 {:body map?}
                        404 {:body map?}
                        400 {:body map?}
                        500 {:body map?}}
            :handler handlers/update-tasting-note}
      :delete {:summary "Delete tasting note"
               :responses {204 {:body nil?}
                           404 {:body map?}
                           500 {:body map?}}
               :handler handlers/delete-tasting-note}}]]])

(defn coercion-error-handler [status]
  (fn [exception _]
    (let [data (ex-data exception)
          ;; Generate the human-readable error message with expound
          human-readable-error (expound/expound-str (:spec data) (:value data))]
      ;; Print to server logs
      (println "Validation error:")
      (println human-readable-error)

      ;; Return the expound output directly to the client
      {:status status
       :body human-readable-error})))

(def app
  (ring/ring-handler
   (ring/router
    wine-routes
    {:data {:coercion spec-coercion/coercion
            :muuntaja m/instance
            :swagger {:ui "/api-docs"
                      :spec "/swagger.json"
                      :data {:info {:title "Wine Cellar API"
                                    :description "API for managing your wine collection"}}}
            :middleware [cors-middleware  ;; Move CORS middleware to be first in the chain
                         auth/wrap-auth   ;; Add authentication middleware
                         (exception/create-exception-middleware
                          (merge
                           exception/default-handlers
                           {:reitit.coercion/request-coercion (coercion-error-handler 400)
                            :reitit.coercion/response-coercion (coercion-error-handler 500)}))
                         parameters/parameters-middleware
                         muuntaja/format-negotiate-middleware
                         muuntaja/format-response-middleware
                         muuntaja/format-request-middleware
                         coercion/coerce-request-middleware
                         coercion/coerce-response-middleware
                         swagger/swagger-feature]}})
; https://github.com/metosin/reitit/blob/master/doc/ring/static.md
   (ring/routes (ring/create-file-handler {:path "/"})
                (ring/create-default-handler))))
