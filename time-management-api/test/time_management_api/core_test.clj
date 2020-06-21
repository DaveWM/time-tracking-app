(ns time-management-api.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [mount.core]
            [time-management-api.core :as sut]
            [datomic.api :as d]
            [time-management-api.test-utils :as u]
            [time-management-api.datomic :as datomic]
            [time-management-api.config :refer [config]]
            [time-management-api.auth :as auth]))

(deftest basic-tests
  (testing "main route"
    (let [response (sut/app (mock/request :get "/health-check"))]
      (is (= (:status response) 200))))

  (testing "not-found route"
    (let [response (sut/app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))


(deftest register-tests
  (let [mock-conn (u/mock-conn)]
    (with-redefs [datomic/conn mock-conn]
      (let [response (sut/app (-> (mock/request :post "/register")
                                  (mock/json-body {:email "test@gmail.com"
                                                   :password "password123"})))]
        (is (= 200 (:status response)))
        (is (= 1 (->> (d/q '[:find ?e
                             :where [?e :user/email]]
                           (d/db mock-conn))
                      count))))

      (let [bad-response (sut/app (-> (mock/request :post "/register")
                                      (mock/json-body {:email "invalid"
                                                       :password "invalid"})))]
        (is (= 400 (:status bad-response)))))))

(deftest login-tests
  (let [mock-conn (u/mock-conn)
        email "email@gmail.com"
        password "password123"]
    @(d/transact mock-conn [[:db/add "user" :user/email email]
                            [:db/add "user" :user/password (buddy.hashers/derive password)]
                            [:db/add "user" :user/role :role/admin]])
    (with-redefs [datomic/conn mock-conn]
      (let [response (-> (sut/app (-> (mock/request :post "/login")
                                      (mock/json-body {:email email
                                                       :password password})))
                         (update :body cheshire.core/parse-string keyword))
            token (:token (:body response))
            decoded-token (buddy.sign.jwt/unsign token (:auth-secret config) auth/token-options)]
        (is (= 200 (:status response)))
        (is (some? token))
        (is (int? (:user-id decoded-token)))
        (is (= ["role/admin"] (:roles decoded-token)))))))
