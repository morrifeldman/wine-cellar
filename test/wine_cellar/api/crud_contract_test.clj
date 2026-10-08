(ns wine-cellar.api.crud-contract-test
  "Every CRUD resource answers the same way: 201 on create, 200 on read and
   update, 204 on delete, 404 for a missing id, 400 for a body that fails
   its spec."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [wine-cellar.db.api :as db]
            [wine-cellar.test-support :as ts]))

(use-fixtures :once ts/with-system)

(def missing-id 999999)

(defn- resources
  []
  (let [wine-id (:id (db/create-wine {:producer "ZZ CRUD"
                                      :country "France"
                                      :region "Rhone"
                                      :style "Red"
                                      :quantity 3}))]
    [{:base "/api/classifications"
      :create {:country "ZZ Country" :region "ZZ Region"}
      :update {:country "ZZ Country" :region "ZZ Region 2"}
      :invalid {:country 5}}
     {:base "/api/grape-varieties"
      :create {:variety_name "ZZ Grape"}
      :update {:variety_name "ZZ Grape 2"}
      :invalid {:variety_name 5}}
     {:base "/api/spirits"
      :create {:name "ZZ Gin" :category "gin"}
      :update {:name "ZZ Gin 2"}
      :invalid {:name 5}}
     {:base "/api/bar-inventory"
      :create {:name "ZZ Lime" :category "juice"}
      :update {:name "ZZ Lime 2"}
      :invalid {:name 5}
      :no-get? true}
     {:base "/api/cocktail-recipes"
      :create {:name "ZZ Sour" :ingredients []}
      :update {:name "ZZ Sour 2"}
      :invalid {:name 5}}
     {:base (str "/api/wines/by-id/" wine-id "/tasting-notes")
      :create {:notes "ZZ note" :rating 90}
      :update {:notes "ZZ note 2"}
      :invalid {:notes 5}}]))

(defn- status-of [& args] (:status (apply ts/request args)))

(deftest crud-resources-share-one-contract
  (doseq [{:keys [base create update invalid no-get?]} (resources)]
    (testing base
      (let [{:keys [status body]} (ts/request :post base create)
            url (str base "/" (:id body))
            missing (str base "/" missing-id)]
        (is (= 201 status) "create")
        (is (= 400 (status-of :post base invalid)) "invalid create")
        (when-not no-get?
          (is (= 200 (status-of :get url)) "read")
          (is (= 404 (status-of :get missing)) "read missing"))
        (is (= 200 (status-of :put url update)) "update")
        (is (= 400 (status-of :put url invalid)) "invalid update")
        (is (= 404 (status-of :put missing update)) "update missing")
        (is (= 204 (status-of :delete url)) "delete")
        (is (= 404 (status-of :delete missing)) "delete missing")))))

(deftest wines-share-the-contract
  (let [missing (str "/api/wines/by-id/" missing-id)]
    (is (= 400 (status-of :post "/api/wines" {:producer "no region"})))
    (is (= 404 (status-of :get missing)))
    (is (= 404 (status-of :put missing {:name "x"})))
    (is (= 404 (status-of :delete missing)))))

(deftest wine-updates-need-a-known-field
  (let [{{:keys [id]} :body} (ts/request :post
                                         "/api/wines"
                                         {:producer "ZZ Update"
                                          :country "France"
                                          :region "Loire"
                                          :style "White"
                                          :quantity 1})
        url (str "/api/wines/by-id/" id)]
    (is (= 200 (status-of :put url {:location "A1"})))
    (is (= 400 (status-of :put url {})))
    (is (= 400 (status-of :put url {:not_a_column 1})))
    (is (= 400 (status-of :put url {:vintage "old"})))
    (testing "creating still requires the core fields"
      (is (= 201
             (status-of :post
                        "/api/wines"
                        {:name "ZZ Named"
                         :country "Italy"
                         :region "Etna"
                         :style "Red"
                         :quantity 2}))))))
