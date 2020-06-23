(ns time-management-api.core-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [mount.core]
            [time-management-api.core :as sut]
            [datomic.api :as d]
            [time-management-api.test-utils :as u]
            [time-management-api.datomic :as datomic]
            [time-management-api.config :refer [config]]
            [time-management-api.auth :as auth]
            [datomock.core :as dm]
            [clj-time.coerce :as tc])
  (:import (java.util Date)))

(defn with-auth-header
  ([request user-id]
   (with-auth-header request user-id #{:role/user :role/manager :role/admin}))
  ([request user-id roles]
   (let [token (auth/create-token {:db/id user-id
                                   :user/email "testing"
                                   :user/role roles})]
     (mock/header request "Authorization" (str "Token " token)))))

(def admin-user-id 123)
(def admin-user-txs
  [[:db/add admin-user-id :user/email "test@gmail.com"]
   [:db/add admin-user-id :user/password (buddy.hashers/derive "password123")]
   [:db/add admin-user-id :user/role :role/user]
   [:db/add admin-user-id :user/role :role/manager]
   [:db/add admin-user-id :user/role :role/admin]])

(def normal-user-id 234)
(def normal-user-txs
  [[:db/add normal-user-id :user/email "normal@gmail.com"]
   [:db/add normal-user-id :user/password (buddy.hashers/derive "password1234")]
   [:db/add normal-user-id :user/role :role/user]])


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
      (testing "valid credentials"
        (let [response (sut/app (-> (mock/request :post "/register")
                                    (mock/json-body {:email "test@gmail.com"
                                                     :password "password123"})))]
          (is (= 200 (:status response)))
          (is (= 1 (->> (d/q '[:find ?u
                               :in $ ?email
                               :where [?u :user/email ?email]
                               [?u :user/role :role/user]]
                             (d/db mock-conn) "test@gmail.com")
                        count)))
          ))

      (testing "invalid credentials"
        (let [bad-response (sut/app (-> (mock/request :post "/register")
                                        (mock/json-body {:email "invalid"
                                                         :password "invalid"})))]
          (is (= 400 (:status bad-response))))))))

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

(deftest settings-tests
  (let [mock-conn (u/mock-conn)]
    @(d/transact mock-conn (concat
                            admin-user-txs
                            [[:db/add admin-user-id :settings/preferred-working-hours 5]]))
    (with-redefs [datomic/conn mock-conn]
      (testing "GET"
        (let [response (-> (sut/app (-> (mock/request :get "/settings")
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))]
          (is (= 200 (:status response)))
          (is (= 5 (:settings/preferred-working-hours (:body response))))))
      (testing "PUT"
        (let [response (-> (sut/app (-> (mock/request :put "/settings")
                                        (mock/json-body {:preferred-working-hours 6})
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))]
          (is (= 6 (d/q '[:find ?pwh .
                          :in $ ?u
                          :where [?u :settings/preferred-working-hours ?pwh]]
                        (d/db mock-conn) admin-user-id))))))))

(deftest users-tests
  (let [mock-conn (u/mock-conn)]
    @(d/transact mock-conn admin-user-txs)
    (testing "GET"
      (let [forked-conn (dm/fork-conn mock-conn)]
        (with-redefs [datomic/conn forked-conn]
          (let [response (-> (sut/app (-> (mock/request :get "/users")
                                          (with-auth-header admin-user-id)))
                             (update :body cheshire.core/parse-string keyword))
                users (get-in response [:body :users])]
            (is (= 1 (count users)))
            (is (= admin-user-id (:db/id (first users)))))

          (testing "not accessible without manager role"
            @(d/transact forked-conn [[:db/retract admin-user-id :user/role :role/manager]])
            (let [response (sut/app (-> (mock/request :get "/users")
                                        (with-auth-header admin-user-id #{:role/user :role/admin})))]
              (is (= 403 (:status response))))))))

    (testing "DELETE"
      (let [forked-conn (dm/fork-conn mock-conn)]
        (with-redefs [datomic/conn forked-conn]
          (let [response (-> (sut/app (-> (mock/request :delete (str "/users/" admin-user-id))
                                          (with-auth-header admin-user-id)))
                             (update :body cheshire.core/parse-string keyword))]
            (is (zero? (count (d/q '[:find ?u
                                     :where [?u :user/email]]
                                   (d/db forked-conn)))))))))

    (testing "POST"
      (let [forked-conn (dm/fork-conn mock-conn)]
        (with-redefs [datomic/conn forked-conn]
          (let [email "second-user@gmail.com"
                password "password123"
                response (-> (sut/app (-> (mock/request :post "/users")
                                          (mock/json-body {:email email
                                                           :password password
                                                           :roles #{:role/user}})
                                          (with-auth-header admin-user-id)))
                             (update :body cheshire.core/parse-string keyword))]
            (is (= 2 (count (d/q '[:find ?u
                                   :where [?u :user/email]]
                                 (d/db forked-conn)))))
            (let [new-user (d/q '[:find (pull ?u [* {:user/role [:db/ident]}]) .
                                  :in $ ?email
                                  :where [?u :user/email ?email]]
                                (d/db forked-conn) email)]
              (is (some? new-user))
              (is (buddy.hashers/check password (:user/password new-user)))
              (is (= #{{:db/ident :role/user}} (set (:user/role new-user)))))))))

    (testing "PUT"
      (let [forked-conn (dm/fork-conn mock-conn)]
        (with-redefs [datomic/conn forked-conn]
          (let [email "new-email@gmail.com"
                password "password12345"
                response (-> (sut/app (-> (mock/request :put (str "/users/" admin-user-id))
                                          (mock/json-body {:email email
                                                           :password password
                                                           :roles #{:role/user}})
                                          (with-auth-header admin-user-id)))
                             (update :body cheshire.core/parse-string keyword))]
            (is (= 1 (count (d/q '[:find ?u
                                   :where [?u :user/email]]
                                 (d/db forked-conn)))))
            (let [admin-user (d/q '[:find (pull ?u [* {:user/role [:db/ident]}]) .
                                    :in $ ?email
                                    :where [?u :user/email ?email]]
                                  (d/db forked-conn) email)]
              (is (some? admin-user))
              (is (buddy.hashers/check password (:user/password admin-user)))
              (is (= #{{:db/ident :role/user}} (set (:user/role admin-user))))))

          (testing "if a password isn't supplied, the password shouldn't be updated"
            (let [response (-> (sut/app (-> (mock/request :put (str "/users/" admin-user-id))
                                            (mock/json-body {:email "new-email@gmail.com"
                                                             :roles #{:role/user}})
                                            (with-auth-header admin-user-id))))
                  admin-user (d/pull (d/db forked-conn) '[* {:user/role [:db/ident]}] admin-user-id)]
              (is (= "new-email@gmail.com" (:user/email admin-user)))
              (is (buddy.hashers/check "password12345" (:user/password admin-user)))))

          (testing "a non-existent user should return a 404"
            (let [response (-> (sut/app (-> (mock/request :put "/users/1234")
                                            (mock/json-body {:email "test@test.com"
                                                             :password "testing123"
                                                             :roles #{:role/user}})
                                            (with-auth-header admin-user-id))))]
              (is (= 404 (:status response))))))))))


(deftest time-sheet-tests
  (let [mock-conn (u/mock-conn)
        start (Date. 120 5 6)
        description "some description"
        duration 12345
        entry-id (-> @(d/transact mock-conn (concat
                                             admin-user-txs
                                             [[:db/add "entry" :entry/description description]
                                              [:db/add "entry" :entry/start start]
                                              [:db/add "entry" :entry/duration duration]
                                              [:db/add "entry" :user/id admin-user-id]]))
                     (get-in [:tempids "entry"]))]
    (testing "GET"
      (with-redefs [datomic/conn mock-conn]
        (let [response (-> (sut/app (-> (mock/request :get "/time-sheet")
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))
              entries (:time-sheet-entries (:body response))]
          (is (= 1 (count entries)))
          (is (= entry-id (:db/id (first entries))))
          (is (= start (tc/to-date (:entry/start (first entries)))))
          (is (= description (:entry/description (first entries))))
          (is (= duration (:entry/duration (first entries))))))

      (testing "not accessible without auth token"
        (let [response (sut/app (mock/request :get "/time-sheet"))]
          (is (= 401 (:status response))))))

    (testing "POST"
      (with-redefs [datomic/conn mock-conn]
        (let [desc "another entry"
              duration 123456
              start (Date. 120 1 1)
              response (-> (sut/app (-> (mock/request :post "/time-sheet")
                                        (mock/json-body {:description desc
                                                         :duration duration
                                                         :start (tc/to-string start)})
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))
              entry (d/q '[:find (pull ?e [*]) .
                           :in $ ?desc
                           :where [?e :entry/description ?desc]]
                         (d/db mock-conn) desc)]
          (is (= 200 (:status response)))
          (is (not= entry-id (:db/id entry)))
          (is (= desc (:entry/description entry)))
          (is (= duration (:entry/duration entry)))
          (is (= start (:entry/start entry)))
          (is (= admin-user-id (:db/id (:user/id entry)))))

        (testing "should not allow a start date in the future"
          (let [response (sut/app (-> (mock/request :post "/time-sheet")
                                      (mock/json-body {:description description
                                                       :duration duration
                                                       :start (tc/to-string "2030-01-01")})
                                      (with-auth-header admin-user-id)))]
            (is (= 400 (:status response)))))

        (testing "should not allow an entry of more than 24 hours"
          (let [response (sut/app (-> (mock/request :post "/time-sheet")
                                      (mock/json-body {:description description
                                                       :duration (* 25 1000 60 60)
                                                       :start (tc/to-string start)})
                                      (with-auth-header admin-user-id)))]
            (is (= 400 (:status response)))))))

    (testing "PUT"
      (with-redefs [datomic/conn mock-conn]
        (let [desc "different description"
              duration 56789
              start (Date. 120 2 3)
              response (-> (sut/app (-> (mock/request :put (str "/time-sheet/" entry-id))
                                        (mock/json-body {:description desc
                                                         :duration duration
                                                         :start (tc/to-string start)})
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))
              entry (d/pull (d/db mock-conn) '[*] entry-id)]
          (is (= 200 (:status response)))
          (is (= desc (:entry/description entry)))
          (is (= duration (:entry/duration entry)))
          (is (= start (:entry/start entry))))

        (testing "should not allow a start date in the future"
          (let [response (sut/app (-> (mock/request :put (str "/time-sheet/" entry-id))
                                      (mock/json-body {:description description
                                                       :duration duration
                                                       :start (tc/to-string "2030-01-01")})
                                      (with-auth-header admin-user-id)))]
            (is (= 400 (:status response)))))

        (testing "should not allow an entry of more than 24 hours"
          (let [response (sut/app (-> (mock/request :put (str "/time-sheet/" entry-id))
                                      (mock/json-body {:description description
                                                       :duration (* 25 1000 60 60)
                                                       :start (tc/to-string start)})
                                      (with-auth-header admin-user-id)))]
            (is (= 400 (:status response)))))))

    (testing "DELETE"
      (with-redefs [datomic/conn mock-conn]
        (let [response (-> (sut/app (-> (mock/request :delete (str "/time-sheet/" entry-id))
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))
              entry (d/q '[:find ?e .
                           :in $ ?e
                           :where [?e :entry/description]]
                         (d/db mock-conn) entry-id)]
          (is (= 200 (:status response)))
          (is (nil? entry)))))))


(deftest user-time-sheet-tests
  (let [mock-conn (u/mock-conn)
        start (Date. 120 5 6)
        description "some description"
        duration 12345
        entry-id (-> @(d/transact mock-conn (concat
                                             admin-user-txs
                                             normal-user-txs
                                             [[:db/add "entry" :entry/description description]
                                              [:db/add "entry" :entry/start start]
                                              [:db/add "entry" :entry/duration duration]
                                              [:db/add "entry" :user/id normal-user-id]]))
                     (get-in [:tempids "entry"]))]
    (testing "GET"
      (with-redefs [datomic/conn mock-conn]
        (let [response (-> (sut/app (-> (mock/request :get (str "/users/" normal-user-id "/time-sheet"))
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))
              entries (:time-sheet-entries (:body response))]
          (is (= 200 (:status response)))
          (is (= 1 (count entries)))
          (is (= entry-id (:db/id (first entries))))
          (is (= start (tc/to-date (:entry/start (first entries)))))
          (is (= description (:entry/description (first entries))))
          (is (= duration (:entry/duration (first entries)))))

        (testing "a non-existent user should return a 404"
          (let [response (-> (sut/app (-> (mock/request :get (str "/users/123456/time-sheet"))
                                          (with-auth-header admin-user-id))))]
            (is (= 404 (:status response)))))

        (testing "not accessible without auth token"
          (let [response (sut/app (mock/request :get (str "/users/" normal-user-id "/time-sheet")))]
            (is (= 401 (:status response)))))

        (testing "not without admin role"
          (let [response (sut/app (-> (mock/request :get (str "/users/" normal-user-id "/time-sheet"))
                                      (with-auth-header normal-user-id #{:role/user})))]
            (is (= 403 (:status response)))))))

    (testing "POST"
      (with-redefs [datomic/conn mock-conn]
        (let [desc "another entry"
              duration 123456
              start (Date. 120 1 1)
              response (-> (sut/app (-> (mock/request :post (str "/users/" normal-user-id "/time-sheet"))
                                        (mock/json-body {:description desc
                                                         :duration duration
                                                         :start (tc/to-string start)})
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))
              entry (d/q '[:find (pull ?e [*]) .
                           :in $ ?desc
                           :where [?e :entry/description ?desc]]
                         (d/db mock-conn) desc)]
          (is (= 200 (:status response)))
          (is (not= entry-id (:db/id entry)))
          (is (= desc (:entry/description entry)))
          (is (= duration (:entry/duration entry)))
          (is (= start (:entry/start entry))))

        (testing "a non-existent user should return a 404"
          (let [response (-> (sut/app (-> (mock/request :post (str "/users/1234/time-sheet"))
                                          (mock/json-body {:description "desc"
                                                           :duration 12345
                                                           :start "2020-01-01"})
                                          (with-auth-header admin-user-id))))]
            (is (= 404 (:status response)))))))

    (testing "PUT"
      (with-redefs [datomic/conn mock-conn]
        (let [desc "different description"
              duration 56789
              start (Date. 120 2 3)
              response (-> (sut/app (-> (mock/request :put (str "/users/" normal-user-id "/time-sheet/" entry-id))
                                        (mock/json-body {:description desc
                                                         :duration duration
                                                         :start (tc/to-string start)})
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))
              entry (d/pull (d/db mock-conn) '[*] entry-id)]
          (is (= 200 (:status response)))
          (is (= desc (:entry/description entry)))
          (is (= duration (:entry/duration entry)))
          (is (= start (:entry/start entry)))
          (is (= normal-user-id (:db/id (:user/id entry)))))

        (testing "if the entry exists for a different user, you should get a 404"
          (let [response (-> (sut/app (-> (mock/request :put (str "/users/" admin-user-id "/time-sheet" entry-id))
                                          (mock/json-body {:description "desc"
                                                           :duration 12345
                                                           :start "2020-01-01"})
                                          (with-auth-header admin-user-id))))]
            (is (= 404 (:status response)))))

        (testing "a non-existent user should return a 404"
          (let [response (-> (sut/app (-> (mock/request :put (str "/users/1234/time-sheet/" entry-id))
                                          (mock/json-body {:description "desc"
                                                           :duration 12345
                                                           :start "2020-01-01"})
                                          (with-auth-header admin-user-id))))]
            (is (= 404 (:status response)))))))

    (testing "DELETE"
      (with-redefs [datomic/conn mock-conn]
        (let [response (-> (sut/app (-> (mock/request :delete (str "/users/" normal-user-id "/time-sheet/" entry-id))
                                        (with-auth-header admin-user-id)))
                           (update :body cheshire.core/parse-string keyword))
              entry (d/q '[:find ?e .
                           :in $ ?e
                           :where [?e :entry/description]]
                         (d/db mock-conn) entry-id)]
          (is (= 200 (:status response)))
          (is (nil? entry)))

        (testing "if the entry exists for a different user, you should get a 404"
          (let [response (-> (sut/app (-> (mock/request :delete (str "/users/" admin-user-id "/time-sheet" entry-id))
                                          (with-auth-header admin-user-id))))]
            (is (= 404 (:status response)))))

        (testing "a non-existent user should return a 404"
          (let [response (-> (sut/app (-> (mock/request :delete (str "/users/1234/time-sheet/" entry-id))
                                          (with-auth-header admin-user-id))))]
            (is (= 404 (:status response)))))))))
