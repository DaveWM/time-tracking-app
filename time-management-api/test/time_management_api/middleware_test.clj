(ns time-management-api.middleware-test
  (:require [clojure.test :refer :all]
            [time-management-api.middleware :as sut]))


(deftest wrap-auth-check-test
  (let [handler identity
        valid-request {:identity {:token "token"}}]
    (is (= valid-request
           ((sut/wrap-auth-check handler) valid-request)))
    (is (= 401
           (:status ((sut/wrap-auth-check handler) {}))))))


(deftest wrap-exception-handling
  (let [good-handler identity]
    (is (= {} ((sut/wrap-exception-handling good-handler) {}))))
  (let [bad-handler #(throw (Exception. "testing..."))
        {:keys [status body]} ((sut/wrap-exception-handling bad-handler) {})]
    (is (= 500 status))
    (is (= #{:error :exception} (set (keys body))))))

(deftest validate-role-handling
  (let [handler identity
        valid-request {:identity {:roles ["role/user" "role/admin"]}}]
    (is (= valid-request ((sut/wrap-validate-role handler #{:role/admin}) valid-request)))
    (is (= 403 (:status ((sut/wrap-validate-role handler #{:role/admin}) {:identity {:roles #{:role/user}}}))))))