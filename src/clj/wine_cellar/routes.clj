(ns wine-cellar.routes
  (:require [wine-cellar.handlers :as handlers]
            [wine-cellar.common :as common]
            [clojure.spec.alpha :as s]
            [reitit.ring :as ring]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.cors :refer [wrap-cors]]
            [muuntaja.core :as m]))


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
(s/def ::styles (s/coll-of (set common/wine-styles)))
(s/def ::level (s/nilable (set common/wine-levels)))
(s/def ::location string?)
(s/def ::quantity int?)
(s/def ::price number?)

(def wine-schema
 (s/keys :req-un [::producer
                  ::country
                  ::region
                  ::vintage
                  ::styles
                  ::quantity
                  ::price]
         :opt-un [::aoc
                  ::communal_aoc
                  ::classification
                  ::vineyard
                  ::name
                  ::location
                  ::level]))

(def cors-middleware
  {:name ::cors
   :wrap (fn [handler]
           (wrap-cors handler
                     :access-control-allow-origin #".*"
                     :access-control-allow-methods
                     [:get :put :post :delete :options]
                     :access-control-allow-headers ["Content-Type" "Accept"]
                     :access-control-allow-credentials "false"))})

(def wine-routes
  [["/swagger.json"
    {:get {:no-doc true
           :swagger {:info {:title "Wine Cellar API"
                           :description "API for managing your wine collection"}}
           :handler (swagger/create-swagger-handler)}}]
   ; Redirect root to api-docs
   ["/"
    {:get {:no-doc true
           :handler (fn [_]
                     {:status 302
                      :headers {"Location" "/api-docs/"}
                      :body ""})}}]
   ["/api-docs/*"
    {:get {:no-doc true
           :handler (swagger-ui/create-swagger-ui-handler)}}]
   
   ;; Wine Classification Routes
   ["/api/classifications"
    {:get {:summary "Get all wine classifications"
           :responses {200 {:body vector?}
                      500 {:body map?}}
           :handler handlers/get-classifications}}]
   
   ["/api/classifications/regions/:country"
    {:parameters {:path {:country string?}}
     :get {:summary "Get regions for a country"
           :responses {200 {:body vector?}
                      500 {:body map?}}
           :handler handlers/get-regions-by-country}}]
   
   ["/api/classifications/aocs/:country/:region"
    {:parameters {:path {:country string?
                        :region string?}}
     :get {:summary "Get AOCs for a region"
           :responses {200 {:body vector?}
                      500 {:body map?}}
           :handler handlers/get-aocs-by-region}}]
   
   ;; Wine Routes
   ["/api/wines"
    {:get {:summary "Get all wines"
           :responses {200 {:body vector?}
                      500 {:body map?}}
           :handler handlers/get-all-wines}
     :post {:summary "Create a new wine"
            :parameters {:body wine-schema}
            :responses {201 {:body map?}
                       400 {:body map?}
                       500 {:body map?}}
            :handler handlers/create-wine}}]
   
   ["/api/wines/:id"
    {:parameters {:path {:id int?}}
     :get {:summary "Get wine by ID"
           :responses {200 {:body map?}
                      404 {:body map?}
                      500 {:body map?}}
           :handler handlers/get-wine}
     :put {:summary "Update wine"
           :parameters {:body wine-schema}
           :responses {200 {:body map?}
                      404 {:body map?}
                      500 {:body map?}}
           :handler handlers/update-wine}
     :delete {:summary "Delete wine"
              :responses {204 {:body nil?}
                         404 {:body map?}
                         500 {:body map?}}
              :handler handlers/delete-wine}}]
   
   ["/api/wines/:id/adjust-quantity"
    {:parameters {:path {:id int?}}
     :post {:summary "Adjust wine quantity"
            :parameters {:body {:adjustment int?}}
            :responses {200 {:body map?}
                       404 {:body map?}
                       500 {:body map?}}
            :handler handlers/adjust-quantity}}]])

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
            :middleware [cors-middleware
                        parameters/parameters-middleware
                        muuntaja/format-negotiate-middleware
                        muuntaja/format-response-middleware
                        muuntaja/format-request-middleware
                        coercion/coerce-response-middleware
                        coercion/coerce-request-middleware
                        swagger/swagger-feature]}})
   (ring/create-default-handler)))
