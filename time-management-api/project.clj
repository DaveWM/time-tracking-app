(defproject time-management-api "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.0"]
                 [ring-cors "0.1.13"]
                 [ring-logger "1.0.1"]
                 [ring/ring-jetty-adapter "1.8.1"]
                 [mount "0.1.16"]
                 [metosin/ring-http-response "0.9.1"]
                 [phrase "0.3-alpha4"]
                 [buddy/buddy-auth "2.2.0"]
                 [buddy/buddy-hashers "1.4.0"]
                 [aero "1.1.6"]
                 [com.datomic/datomic-pro "0.9.5951"]
                 [clj-time "0.15.2"]]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username [:gpg :env/datomic_username]
                                   :password [:gpg :env/datomic_password]}}
  :main time-management-api.core
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]
                        [vvvvalvalval/datomock "0.2.2"]]}
   :uberjar {:aot :all}})
