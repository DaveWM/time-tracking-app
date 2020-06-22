(ns time-management-client.subs-test
  (:require [clojure.test :refer-macros [deftest is]]
            [day8.re-frame.test :refer-macros [run-test-sync]]
            [re-frame.core :as re-frame]
            [re-frame.db :refer [app-db]]
            [cljs-time.coerce :as tc]
            [time-management-client.subs :as sut]))

(defn run-sub [db [id :as sub]]
  (let [handler (re-frame.registrar/get-handler :sub id)]
    @(handler db sub)))

(deftest page-sub-test
  (let [sub (re-frame/subscribe [::sut/page])]
    (reset! app-db {:page :home :route-params {:id 123}})
    (is (= {:page :home :params {:id 123}} @sub))))

(deftest time-sheet-entries-sub-test
  (let [nil-user-sub (re-frame/subscribe [::sut/time-sheet-entries nil])
        with-user-sub (re-frame/subscribe [::sut/time-sheet-entries 123])]
    (reset! app-db {:time-sheet-entries {nil {1 {:entry/start "2020-01-01"}}
                                         123 {2 {:entry/start "2019-01-01"}
                                              3 {:entry/start "2021-01-01"}}}
                    :filters {:end-date (tc/from-string "2020-02-02")}})
    (is (= [{:entry/start "2020-01-01"}] @nil-user-sub))
    (is (= [{:entry/start "2019-01-01"}] @with-user-sub))))

(deftest time-entry-sub-test
  (let [existing-sub (re-frame/subscribe [::sut/time-entry nil 1])
        non-existing-sub (re-frame/subscribe [::sut/time-entry nil 2])]
    (reset! app-db {:time-sheet-entries {nil {1 {:entry/start "2020-01-01"}}}})
    (is (= {:entry/start "2020-01-01"} @existing-sub))
    (is (nil? @non-existing-sub))))

(deftest all-users-sub-test
  (let [sub (re-frame/subscribe [::sut/all-users])]
    (reset! app-db {:users {1 {:user/email "test@gmail.com"}}})
    (is (= [{:user/email "test@gmail.com"}] @sub))))

(deftest user-sub-test
  (let [existing-user-sub (re-frame/subscribe [::sut/user 1])
        non-existent-user-sub (re-frame/subscribe [::sut/user 2])]
    (reset! app-db {:users {1 {:user/email "test@gmail.com"}}})
    (is (= {:user/email "test@gmail.com"} @existing-user-sub))
    (is (nil? @non-existent-user-sub))))
