(ns wine-cellar.api.wine-thumbnail-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(defn- listed
  [id]
  (some #(when (= id (:id %)) %) (:body (ts/request :get "/api/wines/list"))))

(deftest the-list-links-thumbnails-instead-of-inlining-them
  (let [{wine :body} (ts/request :post
                                 "/api/wines"
                                 {:producer "ZZ Thumb"
                                  :country "France"
                                  :region "Rhone"
                                  :style "Red"
                                  :quantity 1
                                  :label_image "data:image/jpeg;base64,AAEC"
                                  :label_thumbnail
                                  "data:image/jpeg;base64,AAEC"})
        id (:id wine)
        version (:label_thumbnail_version wine)]
    (testing "the list carries the thumbnail's hash, not its bytes"
      (is (string? version))
      (testing "and the database's hash matches the one made on write"
        (is (= version (:label_thumbnail_version (listed id)))))
      (is (not (contains? (listed id) :label_thumbnail))))
    (testing "the thumbnail is served as a cacheable JPEG"
      (let [{:keys [status headers]} (ts/request :get
                                                 (str "/api/wines/by-id/" id
                                                      "/thumbnail?v=" version))]
        (is (= 200 status))
        (is (= "image/jpeg" (get headers "Content-Type")))
        (is (re-find #"immutable" (get headers "Cache-Control")))))
    (testing "removing the label clears the version, so cards drop it"
      (let [{updated :body} (ts/request :put
                                        (str "/api/wines/by-id/" id "/image")
                                        {:label_image nil
                                         :label_thumbnail nil})]
        (is (contains? updated :label_thumbnail_version))
        (is (nil? (:label_thumbnail_version updated))))
      (is (nil? (:label_thumbnail_version (listed id))))
      (is (= 404
             (:status
              (ts/request :get (str "/api/wines/by-id/" id "/thumbnail"))))))))
