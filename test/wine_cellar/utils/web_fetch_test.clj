(ns wine-cellar.utils.web-fetch-test
  (:require [clojure.test :refer [deftest is testing]]
            [org.httpkit.server :as server]
            [wine-cellar.utils.web-fetch :as web-fetch]))

(deftest private-and-local-targets-are-refused
  (doseq [url ["http://localhost/" "http://127.0.0.1/" "http://[::1]/"
               "http://0.0.0.0/" "http://10.1.2.3/" "http://172.20.0.1/"
               "http://192.168.1.10/" "http://169.254.169.254/latest/meta-data"
               "http://[fdaa::3]/" "http://100.64.0.1/" "file:///etc/passwd"
               "ftp://8.8.8.8/" "not a url"]]
    (is (not (web-fetch/safe-url? url)) url)))

(deftest public-addresses-are-allowed
  (is (web-fetch/safe-url? "https://8.8.8.8/"))
  (is (web-fetch/safe-url? "http://[2606:4700:4700::1111]/")))

(deftest redirects-are-checked-at-every-hop
  (let [stop (server/run-server
              (fn [{:keys [uri]}]
                (case uri
                  "/start" {:status 302 :headers {"Location" "/secret"}}
                  "/secret" {:status 200
                             :headers {"Content-Type" "text/plain"}
                             :body "internal"}))
              {:port 0 :legacy-return-value? false})
        port (server/server-port stop)
        start (str "http://127.0.0.1:" port "/start")
        http-get #'web-fetch/http-get]
    (try (testing "a redirect to a URL that fails the check is not followed"
           (is (= {:error
                   (str "URL not allowed: http://127.0.0.1:" port "/secret")}
                  (http-get start #(= start %)))))
         (testing "allowed hops are followed"
           (is (= "internal" (:body (http-get start (constantly true))))))
         (finally (server/server-stop! stop)))))
