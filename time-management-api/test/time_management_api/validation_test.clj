(ns time-management-api.validation-test
  (:require [clojure.test :refer :all]
            [time-management-api.validation :as sut]
            [time-management-api.test-utils :as u]
            [datomic.api :as d]))

(deftest no-day-more-than-24-hours?-test
  (let [db (d/db (u/mock-conn))
        valid-db (:db-after
                  (d/with db
                          [[:db/add "entry" :entry/description "valid"]
                           [:db/add "entry" :entry/start (java.util.Date.)]
                           [:db/add "entry" :entry/duration (* 5 1000 60 60)]]))
        invalid-db (:db-after
                    (d/with db
                            [[:db/add "entry" :entry/description "invalid"]
                             [:db/add "entry" :entry/start (java.util.Date.)]
                             [:db/add "entry" :entry/duration (* 25 1000 60 60)]]))]
    (is (sut/no-day-more-than-24-hours? valid-db))
    (is (not (sut/no-day-more-than-24-hours? invalid-db)))))
