(ns time-management-api.utils-test
  (:require [clojure.test :refer :all]
            [time-management-api.utils :as sut]))

(deftest update-when-test
  (is (= {} (sut/update-when {} :a inc)))
  (is (= {:a 2} (sut/update-when {:a 1} :a inc)))
  (is (= {:a 3} (sut/update-when {:a 1} :a + 2))))


(deftest with-spec-test
  (is (= :result (sut/with-spec 1 int?
                   :result)))
  (is (= 400 (:status (sut/with-spec "nope" int?
                        :result)))))