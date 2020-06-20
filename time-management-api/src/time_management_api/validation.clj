(ns time-management-api.validation
  (:require [datomic.api :as d]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))


(defn no-day-more-than-24-hours? [db]
  (->> (d/q '[:find [(pull ?e [*]) ...]
              :where [?e :entry/description]]
            db)
       (group-by #(-> % :entry/start tc/from-date t/with-time-at-start-of-day))
       (every? (fn [[_ date-entries]]
                 (let [total-hours (->> date-entries
                                        (map :entry/duration)
                                        (map #(/ % (* 1000 60 60)))
                                        (reduce + 0))]
                   (>= 24 total-hours))))))