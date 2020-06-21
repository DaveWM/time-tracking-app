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
  (fn [db [_ user-id]]
    (db/filtered-time-entries db user-id)))

(re-frame/reg-sub
  ::loading
  (fn [db _]
    (:loading db)))

(re-frame/reg-sub
 ::time-entry
 (fn [db [_ user-id id]]
   (get-in db [:time-sheet-entries user-id id])))

(re-frame/reg-sub
 ::filters
 (fn [db _]
   (:filters db)))

(re-frame/reg-sub
 ::settings
 (fn [db _]
   (:settings db)))

(re-frame/reg-sub
 ::all-users
 (fn [db _]
   (:users db)))

(re-frame/reg-sub
 ::user
 (fn [db [_ id]]
   (->> (:users db)
        (filter #(= id (:db/id %)))
        first)))
