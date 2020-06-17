(ns time-management-client.events
  (:require
    [re-frame.core :as re-frame]
    [day8.re-frame.tracing :refer-macros [fn-traced]]
    [ajax.core :as ajax]
    [cljs-time.coerce :as tc]
    [cljs-time.format :as tf]
    [time-management-client.db :as db]
    [time-management-client.effects :as effects]
    [time-management-client.coeffects :as coeffects]
    [time-management-client.config :as config]))

(defn auth-header [token]
  [:Authorization (str "Token " token)])

(defn effects-on-page-load [page db]
  (let [load-time-sheet-effect {:method :get
                                :uri (str config/api-url "/time-sheet")
                                :response-format (ajax/json-response-format {:keywords? true})
                                :headers (auth-header (:auth-token db))
                                :on-success [::received-time-sheet]
                                :on-failure [::request-failed]}]
    (case page
      :home {:db (assoc db :loading true)
             :http-xhrio load-time-sheet-effect}
      :edit-entry {:db (assoc db :loading true)
                   :http-xhrio load-time-sheet-effect}
      nil)))

(re-frame/reg-event-fx
  ::initialize-db
  [(re-frame/inject-cofx ::coeffects/auth-token)]
  (fn-traced [{:keys [auth-token]} _]
    {:db (assoc db/default-db :auth-token auth-token)}))

(re-frame/reg-event-fx
 ::set-page
 (fn-traced [{:keys [db]} [_ page route-params]]
   (let [non-auth-pages #{:login :register}]
     (if (or (some? (:auth-token db))
             (contains? non-auth-pages page))
       (-> (merge {:db db}
                  (effects-on-page-load page db))
           (assoc-in [:db :page] page)
           (assoc-in [:db :route-params] route-params))
       {:db db
        ::effects/navigate-to "/login"}))))


(re-frame/reg-event-fx
  ::register
  (fn-traced [{:keys [db]} [_ form-data]]
    {:db (assoc db :loading true)
     :http-xhrio {:method :post
                  :uri (str config/api-url "/register")
                  :params form-data
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::login-success]
                  :on-failure [::request-failed]}}))

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
                  :on-failure [::request-failed]}}))


(re-frame/reg-event-fx
  ::login-success
  (fn-traced [{:keys [db]} [_ response]]
    {:db (-> db
             (assoc :loading false
                    :error nil
                    :auth-token (:token response)))
     ::effects/set-token (:token response)
     ::effects/navigate-to "/"}))

(re-frame/reg-event-fx
  ::request-failed
  (fn-traced [{:keys [db]} [_ {:keys [response status]}]]
    (merge
      {:db (-> db
               (assoc :loading false)
               (assoc :error (:error response)))}
      (when (#{401 403} status)
        {::effects/navigate-to "/login"}))))

(re-frame/reg-event-db
  ::received-time-sheet
  (fn-traced [db [_ {:keys [time-sheet-entries]}]]
    (assoc db :time-sheet-entries time-sheet-entries
              :loading false)))

(re-frame/reg-event-fx
  ::logout
  (fn-traced [{:keys [db]} _]
    {:db (assoc db :auth-token nil)
     ::effects/set-token nil
     ::effects/navigate-to "/login"}))


(re-frame/reg-event-fx
 ::create-entry
 (fn-traced [{:keys [db]} [_ {:keys [description start-string duration-hours duration-mins]}]]
   (let [form-data {:description description
                    :duration (+ (* duration-mins 1000 60) (* duration-hours 1000 60 60))
                    :start (-> (tf/parse (tf/formatter "dd/MM/yy HH:mm") start-string)
                               (tc/to-string))}]
     {:db (assoc db :loading true)
      :http-xhrio {:method :post
                   :uri (str config/api-url "/time-sheet")
                   :params form-data
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :headers (auth-header (:auth-token db))
                   :on-success [::entry-created]
                   :on-failure [::request-failed]}})))

(re-frame/reg-event-fx
 ::entry-created
 (fn-traced [{:keys [db]} _]
   {:db db
    ::effects/navigate-to "/"}))


(re-frame/reg-event-fx
 ::update-entry
 (fn-traced [{:keys [db]} [_ {:keys [id description start-string duration-hours duration-mins]}]]
   (let [form-data {:description description
                    :duration (+ (* duration-mins 1000 60) (* duration-hours 1000 60 60))
                    :start (-> (tf/parse (tf/formatter "dd/MM/yy HH:mm") start-string)
                               (tc/to-string))}]
     {:db (assoc db :loading true)
      :http-xhrio {:method :put
                   :uri (str config/api-url "/time-sheet/" id)
                   :params form-data
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :headers (auth-header (:auth-token db))
                   :on-success [::entry-updated]
                   :on-failure [::request-failed]}})))

(re-frame/reg-event-fx
 ::entry-updated
 (fn-traced [{:keys [db]} [_ updated-entry]]
   {:db (update db :time-sheet-entries
                (partial map (fn [entry]
                               (if (= (:db/id entry) (:db/id updated-entry))
                                 updated-entry
                                 entry))))
    ::effects/navigate-to "/"}))

(re-frame/reg-event-fx
 ::delete-entry
 (fn-traced [{:keys [db]} [_ id]]
   {:http-xhrio {:method :delete
                 :uri (str config/api-url "/time-sheet/" id)
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :headers (auth-header (:auth-token db))
                 :on-success [::entry-deleted]
                 :on-failure [::request-failed]}}))

(re-frame/reg-event-db
 ::entry-deleted
 (fn-traced [db [_ {:keys [db/id]}]]
   (update db :time-sheet-entries
           (partial remove (fn [entry]
                             (= id (:db/id entry)))))))

