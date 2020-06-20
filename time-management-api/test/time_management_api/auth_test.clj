(ns time-management-api.auth-test
  (:require [clojure.test :refer :all]
            [buddy.sign.jwt :as jwt]
            [time-management-api.auth :as sut]))

(deftest create-token-test
  (with-redefs [time-management-api.config/config {:auth-secret "secret"}]
    (let [user {:db/id 12345
                :user/role #{:role/user :role/admin}
                :user/email "test@gmail.com"}
          token (sut/create-token user)
          decoded-token (jwt/unsign token "secret" sut/token-options)]
      (is (some? token))
      (is (= 12345 (:user-id decoded-token)))
      (is (= "test@gmail.com" (:email decoded-token)))
      (is (= ["role/user" "role/admin"] (:roles decoded-token)))
      (is (some? (:exp decoded-token))))))