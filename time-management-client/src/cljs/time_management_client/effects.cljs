(ns time-management-client.effects
  (:require [re-frame.core :refer [reg-fx]]
            [time-management-client.config :as config]
            [day8.re-frame.http-fx]))

(reg-fx
  ::set-token
  (fn [token-value]
    (js/localStorage.setItem config/auth-token-key token-value)))



