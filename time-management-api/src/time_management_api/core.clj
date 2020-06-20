(ns time-management-api.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.coercions :refer [as-int]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer [ok bad-request unauthorized not-found]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.logger :refer [wrap-with-logger]]
            [mount.core :refer [defstate]]
            [clojure.spec.alpha :as s]
            [buddy.auth]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.hashers]
            [datomic.api :as d]
            [clj-time.coerce :as tc]
            [time-management-api.specs :as specs]
            [time-management-api.auth :as auth]
            [time-management-api.config :refer [config]]
            [time-management-api.datomic :as datomic]
            [time-management-api.queries :as queries]
            [time-management-api.middleware :as mw]
            [time-management-api.utils :as u]
            [time-management-api.validation :as validation]))

(defn create-user! [{:keys [email password roles] :as body}]
  (if (s/valid? :request/create-user body)
    (if (nil? (queries/get-user-by-email (d/db datomic/conn) email))
      (let [result @(d/transact datomic/conn (datomic/->transactions {:db/id "new"
                                                                      :user/email email
                                                                      :user/password (buddy.hashers/derive password)
                                                                      :user/role (->> roles (map #(-> {:db/ident (keyword %)})))}))]
        (ok {:email email
             :token (auth/create-token (queries/get-user-by-email (:db-after result) email))}))
      (bad-request {:error (str "There is already a user with email " email)}))
    (bad-request {:error (phrase.alpha/phrase-first {} :request/create-user body)})))

(defn user-routes [override-user-id]
  (routes
   (context "/time-sheet" []
     (GET "/" {{:keys [user-id]} :identity}
       (let [db (datomic/user-db (or override-user-id user-id))]
         (ok {:time-sheet-entries (queries/get-timesheet-entries db)})))

     (POST "/" {{:keys [description start duration] :as body} :body
                {:keys [user-id]} :identity}
       (u/with-spec
        body :request/create-time-sheet-entry
         (let [entry {:db/id "new"
                      :entry/description description
                      :entry/start (tc/to-date (tc/from-string start))
                      :entry/duration duration
                      :user/id (or override-user-id user-id)}
               result @(d/transact datomic/conn (datomic/->transactions entry))]
           (ok (-> entry
                   (update :db/id (:tempids result))
                   (dissoc :user/id))))))

     (PUT "/:id" [id :<< as-int :as {{:keys [description start duration] :as body} :body
                                     {:keys [user-id]} :identity}]
       (u/with-spec body :request/update-time-sheet-entry
         (let [db (datomic/user-db user-id)
               entry {:db/id id
                      :entry/description description
                      :entry/start (tc/to-date (tc/from-string start))
                      :entry/duration duration
                      :user/id (or override-user-id user-id)}
               existing-entity (queries/get-timesheet-entry db id)]
           (if (some? existing-entity)
             (let [txs (datomic/->transactions entry existing-entity)]
               (if (validation/no-day-more-than-24-hours? (:db-after (d/with db txs)))
                 (do @(d/transact datomic/conn txs)
                     (ok (dissoc entry :user/id)))
                 (bad-request {:error "You have tried to log more than 24 hours for a single day."})))
             (not-found {:error (str "No time sheet entry with id " id)})))))

     (DELETE "/:id" [id :<< as-int :as {{:keys [user-id]} :identity}]
       (let [db (datomic/user-db (or override-user-id user-id))]
         (if (some? (queries/get-timesheet-entry db id))
           (do @(d/transact datomic/conn [[:db.fn/retractEntity id]])
               (ok {:db/id id}))
           (not-found {:error (str "No time sheet entry with id " id)})))))))

(defroutes user-manager-only-routes
  (context "/users" []
    (GET "/" []
      (let [db (d/db datomic/conn)]
        (ok {:users (queries/get-all-users db)})))
    (DELETE "/:id" [id :<< as-int]
      (let [db (d/db datomic/conn)
            existing-user (queries/get-user-by-id db id)]
        (if (some? existing-user)
          (do
            @(d/transact datomic/conn [[:db.fn/retractEntity id]])
            (ok {:db/id id}))
          (not-found {:error (str "No user found with id " id)}))))
    (POST "/" {body :body}
      (create-user! body))
    (PUT "/:id" [id :<< as-int :as {body :body}]
      (u/with-spec body :request/update-user
        (let [db (d/db datomic/conn)
              existing-user (d/pull db '[* {:user/role [:db/ident]}] id)
              updated-user {:db/id id
                            :user/email (:email body)
                            :user/password (buddy.hashers/derive (:password body))
                            :user/role (mapv #(-> {:db/ident (keyword %)}) (:roles body))}]
          (if (some? (:user/email existing-user))
            (do @(d/transact datomic/conn (datomic/->transactions
                                           updated-user
                                           existing-user))
                (ok updated-user))
            (not-found {:error (str "No user found with id " id)})))))))

(defroutes admin-only-routes
  (context "/users/:user-id" [user-id :<< as-int]
    (user-routes user-id)))

(defroutes authenticated-only-routes
  (context "/" []
    (user-routes nil)

    (context "/settings" []
      (GET "/" {{:keys [user-id]} :identity}
        (let [db (datomic/user-db user-id)]
          (ok (into {} (queries/get-settings db)))))

      (PUT "/" {{:keys [user-id]} :identity
                {:keys [preferred-working-hours] :as body} :body}
        (u/with-spec body :request/update-settings
          @(d/transact datomic/conn [[:db/add user-id :settings/preferred-working-hours preferred-working-hours]])
          (ok {:settings/preferred-working-hours preferred-working-hours}))))

    (wrap-routes user-manager-only-routes
                 #(-> %
                      (mw/wrap-validate-role #{:role/manager})))
    (wrap-routes admin-only-routes
                 #(-> %
                      (mw/wrap-validate-role #{:role/admin})))))

(defroutes app-routes
  (GET "/health-check" [] (ok {:healthy true}))

  (POST "/register" {body :body}
    (create-user!
     ;; remove roles field in case someone tries to set roles through this endpoint
     (dissoc body :roles)))

  (POST "/login" request
    (if (s/valid? :request/login (:body request))
      (let [{:keys [email password]} (:body request)
            existing-user (queries/get-user-by-email (d/db datomic/conn) email)]
        (if (and (some? existing-user) (buddy.hashers/check password (:user/password existing-user)))
          (ok {:email email
               :roles (:user/role existing-user)
               :token (auth/create-token existing-user)})
          ;; return a 404 if password isn't correct - don't want to give away that the user exists
          (not-found {:error "Email doesn't exist, or password isn't correct"})))
      (bad-request {:error (phrase.alpha/phrase-first {} :request/create-user (:body request))})))

  (wrap-routes authenticated-only-routes
               #(-> %
                    mw/wrap-auth-check
                    (wrap-authorization auth/auth-backend)
                    (wrap-authentication auth/auth-backend)))

  (route/not-found {:error "Route not found"}))

(def app
  (-> app-routes
      (mw/wrap-exception-handling)
      (wrap-defaults api-defaults)
      (wrap-json-response)
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :put :post :delete])
      (wrap-with-logger)))

(defstate server
          :start (run-jetty app {:join? false :port (:port config)})
          :stop (.stop server))

(defn -main [& args]
  (println "Starting...")
  (mount.core/start))
