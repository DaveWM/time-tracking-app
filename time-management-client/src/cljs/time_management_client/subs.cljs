(ns time-management-client.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::page
 (fn [db _]
   {:page (:page db)
    :route-params (:route-params db)}))

(re-frame/reg-sub
  ::error
  (fn [db _]
    (:error db)))

(re-frame/reg-sub
  ::time-sheet-entries
  (fn [db _]
    (:time-sheet-entries db)))

(re-frame/reg-sub
  ::loading
  (fn [db _]
    (:loading db)))
