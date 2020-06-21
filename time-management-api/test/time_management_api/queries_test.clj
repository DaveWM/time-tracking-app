(ns time-management-api.queries-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [time-management-api.test-utils :as u]
            [time-management-api.queries :as sut])
  (:import (java.util Date)))



(deftest get-user-by-email-test
  (let [db (-> (d/db u/mock-conn)
               (d/with
                [[:db/add "user" :user/email "email"]
                 [:db/add "user" :user/password "password"]
                 [:db/add "user" :user/role "role"]
                 [:db/add "role" :db/ident :role/user]])
               :db-after)
        {:keys [user/email user/password user/role]} (sut/get-user-by-email db "email")]
    (is (= "email" email))
    (is (= "password" password))
    (is (= [:role/user] role))

    (is (nil? (sut/get-user-by-email db "not existent email")))))

(deftest get-user-by-id-test
  (let [{db :db-after
         tempids :tempids}
        (-> (d/db u/mock-conn)
            (d/with
             [[:db/add "user" :user/email "email"]
              [:db/add "user" :user/password "password"]
              [:db/add "user" :user/role "role"]
              [:db/add "role" :db/ident :role/user]]))

        user-id (get tempids "user")

        {:keys [user/email user/password user/role]} (sut/get-user-by-id db user-id)]
    (is (= "email" email))
    (is (= "password" password))
    (is (= [:role/user] role))

    (is (nil? (sut/get-user-by-id db 12345)))))

(deftest get-timesheet-entries-test
  (let [start-date (Date.)
        db (-> (d/db u/mock-conn)
               (d/with
                [[:db/add "entry" :entry/description "description"]
                 [:db/add "entry" :entry/start start-date]
                 [:db/add "entry" :entry/duration 12345]])
               :db-after)
        entries (sut/get-timesheet-entries db)
        {:keys [entry/description entry/start entry/duration]} (first entries)]
    (is (= 1 (count entries)))
    (is (= "description" description))
    (is (= start-date start))
    (is (= 12345 duration))))

(deftest get-timesheet-entry-test
  (let [start-date (Date.)
        {db :db-after
         tempids :tempids}
        (-> (d/db u/mock-conn)
            (d/with
             [[:db/add "entry" :entry/description "description"]
              [:db/add "entry" :entry/start start-date]
              [:db/add "entry" :entry/duration 12345]]))

        entry-id (get tempids "entry")

        {:keys [db/id entry/description entry/start entry/duration]}
        (sut/get-timesheet-entry db entry-id)]
    (is (= entry-id id))
    (is (= "description" description))
    (is (= start-date start))
    (is (= 12345 duration))))

(deftest get-settings-test
  (let [db (-> (d/db u/mock-conn)
               (d/with [[:db/add "user" :user/email "email"]
                        [:db/add "user" :settings/preferred-working-hours 5]])
               :db-after)
        result (sut/get-settings db)]
    (is (= 5 (:settings/preferred-working-hours result)))))

(deftest get-all-users-test
  (let [db (-> (d/db u/mock-conn)
               (d/with [[:db/add "user" :user/email "email"]
                        [:db/add "user" :user/role :role/user]])
               :db-after)
        results (sut/get-all-users db)
        {:keys [user/email user/role]} (first results)]
    (is (= 1 (count results)))
    (is (= "email" email))
    (is (= [:role/user] role))))


