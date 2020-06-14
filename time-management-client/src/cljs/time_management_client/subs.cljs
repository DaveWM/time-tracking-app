(ns time-management-client.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::page
 (fn [db _]
   (:page db)))

(re-frame/reg-sub
  ::error
  (fn [db _]
    (:error db)))
