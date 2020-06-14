(ns time-management-client.events
  (:require
    [re-frame.core :as re-frame]
    [day8.re-frame.tracing :refer-macros [fn-traced]]
    [ajax.core :as ajax]
    [time-management-client.db :as db]
    [time-management-client.effects :as effects]
    [time-management-client.coeffects :as coeffects]
    [time-management-client.config :as config]))

(re-frame/reg-event-fx
  ::initialize-db
  [(re-frame/inject-cofx ::coeffects/auth-token)]
  (fn-traced [{:keys [auth-token]} _]
    {:db (assoc db/default-db :auth-token auth-token)}))

(re-frame/reg-event-db
  ::set-page
  (fn-traced [db [_ page]]
    (let [non-auth-pages #{:login :register}]
      (if (or (some? (:auth-token db))
              (contains? non-auth-pages page))
        (assoc db :page page)
        (assoc db :page :login)))))


(re-frame/reg-event-fx
  ::register
  (fn-traced [{:keys [db]} [_ form-data]]
    {:db (assoc db :loading true)
     :http-xhrio {:method :post
                  :uri (str config/api-url "/users")
                  :params form-data
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::login-success]
                  :on-failure [::login-failed]}}))

(re-frame/reg-event-fx
  ::login
  (fn-traced [{:keys [db]} [_ form-data]]
    {:db (assoc db :loading true)
     :http-xhrio {:method :post
                  :uri (str config/api-url "/login")
                  :params form-data
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::login-success]
                  :on-failure [::login-failed]}}))


(re-frame/reg-event-fx
  ::login-success
  (fn-traced [{:keys [db]} [_ response]]
    {:db (-> db
             (assoc :loading false
                    :error nil
                    :auth-token (:token response)))
     ::effects/set-token (:token response)}))

(re-frame/reg-event-db
  ::login-failed
  (fn-traced [db [_ {:keys [response]}]]
    (-> db
        (assoc :loading false)
        (assoc :error (:error response)))))
