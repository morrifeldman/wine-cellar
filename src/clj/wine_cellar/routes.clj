(ns wine-cellar.routes
  (:require [wine-cellar.handlers :as handlers]
            [reitit.ring :as ring]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.cors :refer [wrap-cors]]
            [muuntaja.core :as m]))

(def cors-middleware
  {:name ::cors
   :wrap (fn [handler]
           (wrap-cors handler
                     :access-control-allow-origin [#"http://localhost:8080"]
                     :access-control-allow-methods [:get :put :post :delete :options]  ;; Added :options
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
   ["/api/wines"
    {:get {:summary "Get all wines"
           :responses {200 {:body vector?}
                       500 {:body map?}}
           :handler handlers/get-all-wines}
     :post {:summary "Create a new wine"
            :parameters {:body {:name string?
                                :vintage int?
                                :type string?
                                :location string?
                                :quantity int?
                                :price (fn [x] (number? x))}}  ;; Accept any number type
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
           :parameters {:body {:name string?
                             :vintage int?
                             :type string?
                             :location string?
                             :quantity int?
                             :price double?}}
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
