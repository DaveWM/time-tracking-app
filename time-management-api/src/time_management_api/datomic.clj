(ns time-management-api.datomic
  (:require [mount.core :refer [defstate]]
            [datomic.api :as d]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [time-management-api.config :refer [config]]
            [time-management-api.queries :as queries]))

(def schema
  [{:db/ident :user/id
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :user/email
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}
   {:db/ident :user/password
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :user/role
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}
   {:db/ident :role/user}
   {:db/ident :role/manager}
   {:db/ident :role/admin}

   {:db/ident :entry/description
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :entry/start
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}
   {:db/ident :entry/duration
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "The duration of the entry in milliseconds"}

   {:db/ident :settings/preferred-working-hours
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}])


(defstate conn
          :start (let [db-uri (:datomic/db-uri config)]
                   (d/create-database db-uri)
                   (let [connection (d/connect db-uri)]
                     @(d/transact connection schema)
                     connection))
          :stop (.release conn))


(defn user-db [user-id]
  (d/filter (d/db conn)
            (fn [db datom]
              (let [entity-user-id (-> (d/entity db (:e datom))
                                       :user/id
                                       :db/id)]
                (or (= entity-user-id user-id)
                    (= (:e datom) user-id))))))


(defn ->transactions
  "Converts an entity map to a seq of transaction statements. See https://clojure-conundrums.co.uk/posts/datomic-how-to-update-cardinality-many-attribute/"
  ([entity] (->transactions entity nil))
  ([entity prev-entity]
   (let [id (:db/id entity)]
     (->> (dissoc entity :db/id)
          (mapcat (fn [[k v]]
                    (cond
                      (= v (get prev-entity k)) nil
                      (sequential? v) (let [[added removed] (clojure.data/diff (->> v
                                                                                    (map #(or (:db/id %)
                                                                                              (:db/ident %)))
                                                                                    (set))
                                                                               (->> (get prev-entity k)
                                                                                    (map #(or (:db/id %)
                                                                                              (:db/ident %)))
                                                                                    (set)))]
                                        (concat (->> added
                                                     (map #(-> [:db/add id k %])))
                                                (->> removed
                                                     (map #(-> [:db/retract id k %])))))
                      (map? v) [[:db/add id k (:db/ident v)]]
                      (nil? v) [[:db/retract id k (get prev-entity k)]]
                      true [[:db/add id k v]])))))))


(comment
 (require 'buddy.hashers)
 ;; Insert some example data
 @(d/transact conn [[:db/add "new" :user/email "admin@gmail.com"]
                    [:db/add "new" :user/password (buddy.hashers/derive "password123")]
                    [:db/add "new" :user/role :role/user]
                    [:db/add "new" :user/role :role/manager]
                    [:db/add "new" :user/role :role/admin]])

 @(d/transact conn [[:db/add "entry" :entry/description "First entry"]
                    [:db/add "entry" :entry/start (java.util.Date.)]
                    [:db/add "entry" :entry/duration 3600000]
                    [:db/add "entry" :user/id [:user/email "mail@davemartin.me"]]])

 ;; update email and run this to upgrade a user to admin
 (let [email "mail@davemartin.me"
       user-id (d/q '[:find ?e .
                      :in $ ?email
                      :where [?e :user/email ?email]]
                    (d/db conn) email)]
   @(d/transact conn [[:db/add user-id :user/role :role/manager]
                      [:db/add user-id :user/role :role/admin]]))
 )