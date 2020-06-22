(ns time-management-client.coeffects-test
  (:require [clojure.test :refer-macros [deftest is]]
            [re-frame.registrar :refer [get-handler]]
            [time-management-client.config :as config]
            [time-management-client.coeffects :as sut]))


(deftest auth-token-test
  (let [coeffect (get-handler :cofx ::sut/auth-token)
        _ (js/localStorage.setItem config/auth-token-key "token")]
    (is (= {:auth-token "token"} (coeffect)))))
