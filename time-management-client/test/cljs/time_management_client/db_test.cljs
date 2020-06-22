(ns time-management-client.db-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [cljs-time.coerce :as tc]
            [time-management-client.db :as sut]))

(deftest filtered-time-entries-test
  (is (empty? (sut/filtered-time-entries {:time-sheet-entries nil} nil)))
  (is (= [{:db/id 1}] (sut/filtered-time-entries {:time-sheet-entries {nil {1 {:db/id 1}}}} nil)))
  (is (= [{:db/id 1}] (sut/filtered-time-entries {:time-sheet-entries {nil {1 {:db/id 1}}}} nil)))
  (is (= [{:db/id 1}] (sut/filtered-time-entries {:time-sheet-entries {10 {1 {:db/id 1}}}} 10)))
  (is (empty? (sut/filtered-time-entries {:time-sheet-entries {10 {1 {:db/id 1}}}} nil)))
  (let [within-filters-entry {:db/id 2
                              :entry/start "2020-01-01"}]
    (is (= [within-filters-entry] (sut/filtered-time-entries
                                   {:time-sheet-entries {nil {1 {:db/id 1
                                                                 :entry/start "2019-01-01"}
                                                              2 within-filters-entry}}
                                    :filters {:start-date (tc/from-string "2020-01-01")}}
                                   nil)))
    (is (= [within-filters-entry] (sut/filtered-time-entries
                                   {:time-sheet-entries {nil {1 {:db/id 1
                                                                 :entry/start "2021-01-01"}
                                                              2 within-filters-entry}}
                                    :filters {:end-date (tc/from-string "2020-02-01")}}
                                   nil)))
    (is (= [within-filters-entry] (sut/filtered-time-entries
                                   {:time-sheet-entries {nil {1 {:db/id 1
                                                                 :entry/start "2019-01-01"}
                                                              2 within-filters-entry
                                                              3 {:db/id 3
                                                                 :entry/start "2021-01-01"}}}
                                    :filters {:start-date (tc/from-string "2020-01-01")
                                              :end-date (tc/from-string "2020-12-31")}}
                                   nil)))))
