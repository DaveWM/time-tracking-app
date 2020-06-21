(ns time-management-api.test-utils
  (:require [datomic.api :as d]
            [time-management-api.datomic :as datomic]))

(def mock-conn
  (let [uri "datomic:mem://mock"]
    (d/create-database uri)
    (doto (d/connect uri)
      (d/transact datomic/schema))))