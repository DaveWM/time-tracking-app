(ns time-management-api.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer [ok bad-request unauthorized]]
            [mount.core :refer [defstate]]
            [clojure.spec.alpha :as s]
            [clj-time.core :as time]
            [buddy.sign.jwt :as jwt]
            [buddy.auth]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [time-management-api.specs :as specs]
            [time-management-api.auth :as auth]
            [time-management-api.config :refer [config]]))

(defroutes app-routes

  (GET "/health-check" [] (ok {:healthy true}))


  (POST "/users" {{:keys [email password] :as body} :body}
    (if (s/valid? :request/create-user body)
      (let [token (auth/create-token email)]
        (ok {:email email
             :token token}))
      (bad-request {:error (phrase.alpha/phrase-first {} :request/create-user body)})))

  (POST "/login" request
    (if (s/valid? :request/login (:body request))
      (let [{:keys [email password]} (:body request)
            token (auth/create-token email)]
        (ok {:email email
             :token token}))
      (bad-request {:error (phrase.alpha/phrase-first {} :request/create-user (:body request))})))

  (GET "/time-sheet" request
    (if-not (buddy.auth/authenticated? request)
      (buddy.auth/throw-unauthorized)
      (ok {:time-sheet []})))

  (route/not-found {:error "Route not found"}))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      (wrap-authorization auth/auth-backend)
      (wrap-authentication auth/auth-backend)
      (wrap-json-response)
      (wrap-json-body {:keywords? true :bigdecimals? true})))

(defstate server
          :start (run-jetty app {:join? false :port (:port config)})
          :stop (.stop server))

(defn -main [& args]
  (println "Starting...")
  (mount.core/start))
