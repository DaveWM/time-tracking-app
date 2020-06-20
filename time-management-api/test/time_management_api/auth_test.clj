(ns time-management-api.auth-test
  (:require [clojure.test :refer :all]
            [buddy.sign.jwt :as jwt]
            [time-management-api.auth :as sut]))

(deftest create-token-test
  (with-redefs [time-management-api.config/config {:auth-secret "secret"}]
    (let [token (sut/create-token 12345 "test@gmail.com")
          decoded-token (jwt/unsign token "secret" sut/token-options)]
      (is (some? token))
      (is (= 12345 (:user-id decoded-token)))
      (is (= "test@gmail.com" (:email decoded-token)))
      (is (some? (:exp decoded-token))))))