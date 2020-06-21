(require '[datomic.api :as d])

(println "running...")

(def db-uri "datomic:dev://localhost:4334/toptal")
(def conn (d/connect db-uri))

(println "updating db")

@(d/transact conn [[:db/add "admin" :user/email "admin@gmail.com"]
                   [:db/add "admin" :user/password "bcrypt+sha512$a02b1d495cb7b4138fd1243431ca40b5$12$c57e3a1a01019cc7f74d46ce05b3c3e4e1d7a9d2e832c6b5"] ;; password123
                   [:db/add "admin" :user/role :role/user]
                   [:db/add "admin" :user/role :role/manager]
                   [:db/add "admin" :user/role :role/admin]])

(println "updated!")