(ns time-management-client.coeffects
  (:require [re-frame.core :refer [reg-cofx]]
            [time-management-client.config :as config]))

(reg-cofx
  ::auth-token
  (fn [coeffects _]
    (assoc coeffects :auth-token (js/localStorage.getItem config/auth-token-key))))