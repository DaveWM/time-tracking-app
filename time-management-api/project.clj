(defproject time-management-api "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.0"]
                 [mount "0.1.16"]
                 [metosin/ring-http-response "0.9.1"]
                 [phrase "0.3-alpha4"]
                 [buddy/buddy-auth "2.2.0"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler time-management-api.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}})
