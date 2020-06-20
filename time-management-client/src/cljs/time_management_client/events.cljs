(ns time-management-client.events
  (:require
   [re-frame.core :as re-frame]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [ajax.core :as ajax]
   [cljs-time.coerce :as tc]
   [cljs-time.format :as tf]
   [cljs-time.core :as t]
   [clojure.string :as s]
   [hiccups.runtime :as hiccup]
   ["jwt-decode" :as jwt-decode]
   [time-management-client.db :as db]
   [time-management-client.effects :as effects]
   [time-management-client.coeffects :as coeffects]
   [time-management-client.config :as config]
   [time-management-client.routes :as routes]
   [time-management-client.utils :as u]))

(defn auth-header [token]
  [:Authorization (str "Token " token)])


(defn effects-on-page-load [page route-params db]
  (let [load-time-sheet-effect {:method :get
                                :uri (str config/api-url "/time-sheet")
                                :response-format (ajax/json-response-format {:keywords? true})
                                :headers (auth-header (:auth-token db))
                                :on-success [::received-time-sheet nil]
                                :on-failure [::request-failed]}
        load-settings-effect {:method :get
                              :uri (str config/api-url "/settings")
                              :response-format (ajax/json-response-format {:keywords? true})
                              :headers (auth-header (:auth-token db))
                              :on-success [::received-settings]
                              :on-failure [::request-failed]}
        load-users-effect {:method :get
                           :uri (str config/api-url "/users")
                           :response-format (ajax/json-response-format {:keywords? true})
                           :headers (auth-header (:auth-token db))
                           :on-success [::received-users]
                           :on-failure [::request-failed]}]
    (case page
      :home {:db (assoc db :loading true)
             :http-xhrio [load-time-sheet-effect load-settings-effect]}
      :edit-entry {:db (assoc db :loading true)
                   :http-xhrio load-time-sheet-effect}
      :settings {:db (assoc db :loading true)
                 :http-xhrio load-settings-effect}
      :users {:db (assoc db :loading true)
              :http-xhrio load-users-effect}
      :edit-user {:db (assoc db :loading true)
                  :http-xhrio load-users-effect}
      :user-entries {:db (assoc db :loading true)
                     :http-xhrio {:method :get
                                  :uri (str config/api-url "/users/" (:id route-params) "/time-sheet")
                                  :response-format (ajax/json-response-format {:keywords? true})
                                  :headers (auth-header (:auth-token db))
                                  :on-success [::received-time-sheet (:id route-params)]
                                  :on-failure [::request-failed]}}
      {:db db})))

(re-frame/reg-event-fx
  ::initialize-db
  [(re-frame/inject-cofx ::coeffects/auth-token)]
  (fn-traced [{:keys [auth-token]} _]
    {:db (assoc db/default-db :auth-token auth-token)}))

(re-frame/reg-event-fx
 ::set-page
 (fn-traced [{:keys [db]} [_ page route-params]]
   (let [token (:auth-token db)
         decoded-token (when token (js->clj (jwt-decode token) :keywordize-keys true))
         user-roles (->> decoded-token :roles (map keyword) (set))
         route-roles (get routes/page->roles page)]
     (cond
       (clojure.set/subset? route-roles user-roles) (-> (effects-on-page-load page route-params db)
                                                        (assoc-in [:db :page] page)
                                                        (assoc-in [:db :route-params] route-params))
       (nil? token) {:db db
                     ::effects/navigate-to "/login"}
       :else {:db (assoc db :page :not-authorized)}))))


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
 (fn-traced [db [_ user-id {:keys [time-sheet-entries]}]]
   (-> db
       (assoc-in [:time-sheet-entries user-id] (->> time-sheet-entries
                                                    (map #(-> [(:db/id %) %]))
                                                    (into {})))
       (assoc :loading false))))

(re-frame/reg-event-fx
  ::logout
  (fn-traced [{:keys [db]} _]
    {:db (assoc db :auth-token nil)
     ::effects/set-token nil
     ::effects/navigate-to "/login"}))


(re-frame/reg-event-fx
 ::create-entry
 (fn-traced [{:keys [db]} [_ {:keys [description start duration-hours duration-mins]}]]
   (let [form-data {:description description
                    :duration (+ (* duration-mins 1000 60) (* duration-hours 1000 60 60))
                    :start (tc/to-string start)}]
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
 (fn-traced [{:keys [db]} [_ user-id {:keys [id description start duration-hours duration-mins]}]]
   (let [form-data {:description description
                    :duration (+ (* duration-mins 1000 60) (* duration-hours 1000 60 60))
                    :start (tc/to-string start)}]
     {:db (assoc db :loading true)
      :http-xhrio {:method :put
                   :uri (str config/api-url "/time-sheet/" id)
                   :params form-data
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :headers (auth-header (:auth-token db))
                   :on-success [::entry-updated user-id]
                   :on-failure [::request-failed]}})))

(re-frame/reg-event-fx
 ::entry-updated
 (fn-traced [{:keys [db]} [_ user-id updated-entry]]
   {:db (assoc-in db [:time-sheet-entries user-id (:db/id updated-entry)] updated-entry)
    ::effects/navigate-to "/"}))

(re-frame/reg-event-fx
 ::delete-entry
 (fn-traced [{:keys [db]} [_ user-id id]]
   {:http-xhrio {:method :delete
                 :uri (str config/api-url "/time-sheet/" id)
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :headers (auth-header (:auth-token db))
                 :on-success [::entry-deleted user-id]
                 :on-failure [::request-failed]}}))

(re-frame/reg-event-db
 ::entry-deleted
 (fn-traced [db [_ user-id {:keys [db/id]}]]
   (update-in db [:time-sheet-entries user-id] #(dissoc % id))))

(re-frame/reg-event-fx
 ::export-time-sheet
 (fn-traced [{:keys [db]} [_ user-id]]
   {::effects/save-file {:content (hiccup/render-html
                                   [:html
                                    [:body
                                     (->> (db/filtered-time-entries db user-id)
                                          (sort-by (comp tc/to-long tc/from-string :entry/start))
                                          (map (fn [{:keys [entry/start entry/duration entry/description]}]
                                                 (let [ended-at (t/plus (tc/from-string start) (t/millis duration))
                                                       interval (t/interval (tc/from-string start) ended-at)
                                                       started-at-string (tf/unparse (tf/formatter "yyyy.MM.dd") (tc/from-string start))
                                                       duration-string (if (zero? (t/in-hours interval))
                                                                         (str (t/in-minutes interval) "m")
                                                                         (str (t/in-hours interval) "h " (mod (t/in-minutes interval) 60) "m"))]
                                                   [:div
                                                    [:ul
                                                     [:li "Date: " started-at-string]
                                                     [:li "Total Time: " duration-string]
                                                     [:li "Description: " description]]]))))]])
                         :type "text/html"
                         :name "time-sheet.html"}}))


(re-frame/reg-event-db
 ::filter-start-date-updated
 (fn-traced [db [_ updated-date]]
   (assoc-in db [:filters :start-date] updated-date)))

(re-frame/reg-event-db
 ::filter-end-date-updated
 (fn-traced [db [_ updated-date]]
   (assoc-in db [:filters :end-date] updated-date)))


(re-frame/reg-event-db
 ::received-settings
 (fn-traced [db [_ settings]]
   (assoc db :settings settings
             :loading false)))


(re-frame/reg-event-fx
 ::update-settings
 (fn-traced [{:keys [db]} [_ {:keys [preferred-working-hours]}]]
   (let [form-data {:preferred-working-hours preferred-working-hours}]
     {:db (assoc db :loading true)
      :http-xhrio {:method :put
                   :uri (str config/api-url "/settings")
                   :params form-data
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :headers (auth-header (:auth-token db))
                   :on-success [::settings-updated]
                   :on-failure [::request-failed]}})))


(re-frame/reg-event-fx
 ::settings-updated
 (fn-traced [{:keys [db]} [_ updated-settings]]
   {:db (assoc db :settings updated-settings)
    ::effects/navigate-to "/"}))

(re-frame/reg-event-db
 ::received-users
 (fn-traced [db [_ {:keys [users]}]]
   (assoc db :users (->> users (map #(update % :user/role (partial map keyword))))
             :loading false)))

(re-frame/reg-event-fx
 ::delete-user
 (fn-traced [{:keys [db]} [_ id]]
   {:http-xhrio {:method :delete
                 :uri (str config/api-url "/users/" id)
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :headers (auth-header (:auth-token db))
                 :on-success [::user-deleted]
                 :on-failure [::request-failed]}}))

(re-frame/reg-event-db
 ::user-deleted
 (fn-traced [db [_ {:keys [db/id]}]]
   (update db :users
           (partial remove (fn [entry]
                             (= id (:db/id entry)))))))

(re-frame/reg-event-fx
 ::create-user
 (fn-traced [{:keys [db]} [_ {:keys [email password roles]}]]
   (let [form-data {:email email
                    :password password
                    :roles (map u/kw-string roles)}]
     {:db (assoc db :loading true)
      :http-xhrio {:method :post
                   :uri (str config/api-url "/users")
                   :params form-data
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :headers (auth-header (:auth-token db))
                   :on-success [::user-updated]
                   :on-failure [::request-failed]}})))

(re-frame/reg-event-fx
 ::update-user
 (fn-traced [{:keys [db]} [_ {:keys [id email password roles]}]]
   (let [form-data (-> {:email email
                        :roles (map u/kw-string roles)}
                       (u/assoc-when :password password))]
     {:db (assoc db :loading true)
      :http-xhrio {:method :put
                   :uri (str config/api-url "/users/" id)
                   :params form-data
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :headers (auth-header (:auth-token db))
                   :on-success [::user-updated]
                   :on-failure [::request-failed]}})))

(re-frame/reg-event-fx
 ::user-updated
 (fn-traced [{:keys [db]} _]
   {:db db
    ::effects/navigate-to "/users"}))

