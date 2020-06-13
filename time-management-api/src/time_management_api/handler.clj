(ns time-management-api.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer [ok bad-request unauthorized not-found]]
            [ring.middleware.cors :refer [wrap-cors]]
            [mount.core :refer [defstate]]
            [clojure.spec.alpha :as s]
            [buddy.auth]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.hashers]
            [datomic.api :as d]
            [time-management-api.specs :as specs]
            [time-management-api.auth :as auth]
            [time-management-api.config :refer [config]]
            [time-management-api.datomic :as datomic]
            [time-management-api.queries :as queries]))

(defroutes authenticated-only-routes
  (GET "/time-sheet" {{:keys [user]} :identity}
    (let [db (datomic/user-db user)]
      (ok {:time-sheet-entries (queries/get-timesheet-entries db)}))))

(defroutes app-routes
  (GET "/health-check" [] (ok {:healthy true}))

  (POST "/users" {{:keys [email password] :as body} :body}
    (if (s/valid? :request/create-user body)
      (if (nil? (queries/get-user-by-email (d/db datomic/conn) email))
        (do @(d/transact datomic/conn (datomic/->transactions {:db/id "new"
                                                               :user/email email
                                                               :user/password (buddy.hashers/derive password)
                                                               :user/role :role/user}))
            (ok {:email email
                 :token (auth/create-token email)}))
        (bad-request {:error (str "There is already a user with email " email)}))
      (bad-request {:error (phrase.alpha/phrase-first {} :request/create-user body)})))

  (POST "/login" request
    (if (s/valid? :request/login (:body request))
      (let [{:keys [email password]} (:body request)
            existing-user (queries/get-user-by-email (d/db datomic/conn) email)]
        (if (and (some? existing-user) (buddy.hashers/check password (:user/password existing-user)))
          (ok {:email email
               :roles (:user/role existing-user)
               :token (auth/create-token email)})
          ;; return a 404 if password isn't correct - don't want to give away that the user exists
          (not-found {:error "Email doesn't exist, or password isn't correct"})))
      (bad-request {:error (phrase.alpha/phrase-first {} :request/create-user (:body request))})))

  (wrap-routes authenticated-only-routes
               #(-> %
                    auth/wrap-auth-check
                    (wrap-authorization auth/auth-backend)
                    (wrap-authentication auth/auth-backend)))

  (route/not-found {:error "Route not found"}))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      (wrap-json-response)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete])))

(defstate server
          :start (run-jetty app {:join? false :port (:port config)})
          :stop (.stop server))

(defn -main [& args]
  (println "Starting...")
  (mount.core/start))
