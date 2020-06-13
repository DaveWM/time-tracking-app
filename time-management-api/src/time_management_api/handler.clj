(ns time-management-api.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      (wrap-json-response)
      (wrap-json-body {:keywords? true :bigdecimals? true})))
