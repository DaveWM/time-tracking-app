(ns time-management-client.subs
  (:require
   [re-frame.core :as re-frame]
   [time-management-client.db :as db]))

(re-frame/reg-sub
 ::page
 (fn [db _]
   {:page (:page db)
    :params (:route-params db)}))

(re-frame/reg-sub
  ::error
  (fn [db _]
    (:error db)))

(re-frame/reg-sub
  ::time-sheet-entries
  (fn [db _]
    (db/filtered-time-entries db)))

(re-frame/reg-sub
  ::loading
  (fn [db _]
    (:loading db)))

(re-frame/reg-sub
 ::time-entry
 (fn [db [_ id]]
   (->> (:time-sheet-entries db)
        (filter #(= id (:db/id %)))
        first)))

(re-frame/reg-sub
 ::filters
 (fn [db _]
   (:filters db)))

(re-frame/reg-sub
 ::settings
 (fn [db _]
   (:settings db)))
