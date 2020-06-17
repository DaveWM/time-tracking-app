(ns time-management-client.subs
  (:require
   [re-frame.core :as re-frame]))

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
    (:time-sheet-entries db)))

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
