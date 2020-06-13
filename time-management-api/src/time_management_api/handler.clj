(ns time-management-api.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer [ok]]
            [mount.core :refer [defstate]]))

(defroutes app-routes
           (GET "/" [] (ok {:test true}))
           (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      (wrap-json-response)
      (wrap-json-body {:keywords? true :bigdecimals? true})))

(defstate server
          :start (run-jetty app {:join? false :port 8080})
          :stop (.stop server))

(defn -main [& args]
  (println "Starting...")
  (mount.core/start))
