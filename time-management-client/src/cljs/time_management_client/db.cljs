(ns time-management-client.db
  (:require [cljs-time.core :as t]
            [cljs-time.coerce :as tc]))

(def default-db
  {:auth-token nil
   :loading false
   :entries nil
   :filters {:start-date nil
             :end-date nil}})

(defn filtered-time-entries [db]
  (let [{:keys [start-date end-date]} (:filters db)]
    (->> (:time-sheet-entries db)
         (remove #(when start-date
                    (t/before? (tc/from-string (:entry/start %)) start-date)))
         (remove #(when end-date
                    (t/after? (tc/from-string (:entry/start %)) end-date))))))
