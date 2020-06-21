(ns time-management-api.test-utils
  (:require [datomic.api :as d]
            [datomock.core :as dm]
            [time-management-api.datomic :as datomic]))

(def in-mem-conn
  (let [uri "datomic:mem://mock"]
    (d/create-database uri)
    (doto (d/connect uri)
      (d/transact datomic/schema))))

(defn mock-conn []
  (dm/mock-conn (d/db in-mem-conn)))