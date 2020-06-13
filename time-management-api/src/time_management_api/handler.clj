(ns time-management-api.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer [ok bad-request]]
            [mount.core :refer [defstate]]
            [clojure.spec.alpha :as s]
            [time-management-api.specs :as specs]
            [clj-time.core :as time]
            [buddy.sign.jwt :as jwt]))

(def secret "super secret secret")

(defroutes app-routes
  (POST "/users" {{:keys [email password] :as body} :body}
    (if (s/valid? :request/create-user body)
      (let [claims {:user email
                    :roles [:user]
                    :exp (time/plus (time/now) (time/seconds 3600))}
            token  (jwt/sign claims secret {:alg :hs512})]
        (ok {:email email
             :token token}))
      (bad-request {:error (phrase.alpha/phrase-first {} :request/create-user body)})))
  (route/not-found {:error "Route not found"}))

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
