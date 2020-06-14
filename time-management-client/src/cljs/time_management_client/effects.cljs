(ns time-management-client.effects
  (:require [re-frame.core :refer [reg-fx]]
            [time-management-client.config :as config]
            [day8.re-frame.http-fx]
            [time-management-client.routes :as routes]))

(reg-fx
  ::set-token
  (fn [token-value]
    (js/localStorage.setItem config/auth-token-key token-value)))


(reg-fx
  ::navigate-to
  (fn [url]
    (routes/navigate-to! url)))