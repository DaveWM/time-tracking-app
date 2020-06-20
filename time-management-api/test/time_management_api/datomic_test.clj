(ns time-management-api.datomic-test
  (:require [clojure.test :refer :all]
            [time-management-api.datomic :as sut]
            [datomic.api :as d]))

(def mock-conn
  (let [uri "datomic:mem://mock"]
    (d/create-database uri)
    (d/connect uri)))

(deftest schema-test
  ;; will fail if an exception is thrown
  (is @(d/transact mock-conn sut/schema)))

(deftest ->transactions-test
  (let [eid 0]
    (is (= [[:db/add eid :x 1]] (sut/->transactions {:db/id eid
                                                     :x 1})))
    (is (= [[:db/add eid :x 1]
            [:db/add eid :y 2]])
        (sut/->transactions {:db/id eid
                             :x 1
                             :y 2}))
    (is (= [[:db/add eid :y 2]]
           (sut/->transactions {:db/id eid
                                :x 1
                                :y 2}
                               {:db/id eid
                                :x 1})))
    (is (= [[:db/retract eid :x 1]]
           (sut/->transactions {:db/id eid
                                :x nil}
                               {:db/id eid
                                :x 1})))
    (is (= [[:db/add eid :x :y]]
           (sut/->transactions {:db/id eid
                                :x [{:db/ident :x} {:db/ident :y}]}
                               {:db/id eid
                                :x [{:db/ident :x}]})))
    (is (= [[:db/add eid :x 234]]
           (sut/->transactions {:db/id eid
                                :x [{:db/id 123} {:db/ident 234}]}
                               {:db/id eid
                                :x [{:db/id 123}]})))
    (is (= [[:db/retract eid :x :y]]
           (sut/->transactions {:db/id eid
                                :x [{:db/ident :x}]}
                               {:db/id eid
                                :x [{:db/ident :x} {:db/ident :y}]})))
    (is (= [[:db/retract eid :x 234]]
           (sut/->transactions {:db/id eid
                                :x [{:db/id 123}]}
                               {:db/id eid
                                :x [{:db/id 123} {:db/ident 234}]})))))


(deftest user-db-test
  (let [{:keys [tempids db-after]} (-> (d/db mock-conn)
                                       (d/with [[:db/add "user" :user/email "email"]
                                                [:db/add "other-user" :user/email "another user"]
                                                [:db/add "entry" :user/id "user"]
                                                [:db/add "entry" :entry/description "test"]
                                                [:db/add "another-entry" :user/id "other-user"]
                                                [:db/add "another-entry" :entry/description "not visible"]]))
        user-id (get tempids "user")
        entry-id (get tempids "entry")]
    (with-redefs [d/db (constantly db-after)]
      (let [db (sut/user-db user-id)]
        (is (= [user-id] (d/q '[:find [?u ...]
                                :where [?u :user/email]]
                              db)))
        (is (= [entry-id] (d/q '[:find [?e ...]
                                 :where [?e :entry/description]]
                               db)))))))


