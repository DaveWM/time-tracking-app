(ns time-management-api.queries
  (:require [datomic.api :as d]
            [time-management-api.utils :as u]))


(defn get-user-by-email [db email]
  (-> (d/q '[:find (pull ?e [:db/id :user/email :user/password {:user/role [:db/ident]}]) .
             :in $ ?email
             :where
             [?e :user/email ?email]]
           db email)
      (u/update-when :user/role (partial map :db/ident))))


(defn get-timesheet-entries [db]
  (d/q '[:find [(pull ?e [:db/id :entry/description :entry/start :entry/duration]) ...]
         :where [?e :entry/description]]
       db))

(defn get-timesheet-entry [db id]
  (d/q '[:find (pull ?e [:db/id :entry/description :entry/start :entry/duration]) .
         :in $ ?e
         :where [?e :entry/description]]
       db id))


(defn get-settings [db]
  (d/q '[:find (pull ?u [:settings/preferred-working-hours]) .
         :where [?u :user/email]]
       db))

(defn get-all-users [db]
  (->> (d/q '[:find [(pull ?e [:db/id :user/email {:user/role [:db/ident]}]) ...]
              :where [?e :user/email]]
            db)
       (map #(u/update-when % :user/role (partial map :db/ident)))))