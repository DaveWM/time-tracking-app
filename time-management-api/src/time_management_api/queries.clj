(ns time-management-api.queries
  (:require [datomic.api :as d]
            [time-management-api.utils :as u]))


(defn get-user-by-email [db email]
  (-> (d/q '[:find (pull ?e [:user/email :user/password {:user/role [:db/ident]}]) .
             :in $ ?email
             :where
             [?e :user/email ?email]]
           db email)
      (u/update-when :user/role (partial map :db/ident))))


(defn get-timesheet-entries [db]
  (d/q '[:find [(pull ?e [:entry/description :entry/start :entry/duration]) ...]
         :where [?e :entry/description]]
       db))