(ns time-management-client.effects
  (:require [re-frame.core :refer [reg-fx]]
            [day8.re-frame.http-fx]
            ["file-saver" :as file-saver]
            [time-management-client.config :as config]
            [time-management-client.routes :as routes]))

(reg-fx
  ::set-token
  (fn [token-value]
    (js/localStorage.setItem config/auth-token-key token-value)))

(reg-fx
  ::navigate-to
  (fn [url]
    (routes/navigate-to! url)))

(reg-fx
 ::save-file
 (fn [{:keys [content type name]}]
   (-> (js/Blob. #js [content] #js {:type (str type ";charset=utf-8")})
       (file-saver/saveAs name))))