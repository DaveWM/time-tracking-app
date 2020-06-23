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
   [time-management-client.utils :as u]
   [time-management-client.macros :refer [def-event-fx def-event-db]]))

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
                                  :uri (str config/api-url "/users/" (:user-id route-params) "/time-sheet")
                                  :response-format (ajax/json-response-format {:keywords? true})
                                  :headers (auth-header (:auth-token db))
                                  :on-success [::received-time-sheet (:user-id route-params)]
                                  :on-failure [::request-failed]}}
      {:db db})))

(def-event-fx
  ::initialize-db
  [(re-frame/inject-cofx ::coeffects/auth-token)]
  (fn-traced [{:keys [auth-token]} _]
    {:db (assoc db/default-db :auth-token auth-token)}))

(def-event-fx
 ::set-page
 (fn-traced [{:keys [db]} [_ page route-params]]
   (let [token (:auth-token db)
         decoded-token (when token
                         (try
                           (js->clj (jwt-decode token) :keywordize-keys true)
                           (catch :default e nil)))
         user-roles (->> decoded-token :roles (map keyword) (set))
         route-roles (get routes/page->roles page)]
     (cond
       (clojure.set/subset? route-roles user-roles) (-> (effects-on-page-load page route-params db)
                                                        (assoc-in [:db :page] page)
                                                        (assoc-in [:db :route-params] route-params)
                                                        (assoc-in [:db :error] nil))
       (nil? decoded-token) {:db db
                             ::effects/navigate-to "/login"}
       :else {:db (assoc db :page :not-authorized)}))))


(def-event-fx
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

(def-event-fx
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


(def-event-fx
  ::login-success
  (fn-traced [{:keys [db]} [_ response]]
    {:db (-> db
             (assoc :loading false
                    :error nil
                    :auth-token (:token response)))
     ::effects/set-token (:token response)
     ::effects/navigate-to "/"}))

(def-event-fx
  ::request-failed
  (fn-traced [{:keys [db]} [_ {:keys [response status]}]]
    (merge
      {:db (-> db
               (assoc :loading false)
               (assoc :error (:error response)))}
      (when (#{401 403} status)
        {::effects/navigate-to "/login"}))))

(def-event-db
 ::received-time-sheet
 (fn-traced [db [_ user-id {:keys [time-sheet-entries]}]]
   (-> db
       (assoc-in [:time-sheet-entries user-id] (->> time-sheet-entries
                                                    (map #(-> [(:db/id %) %]))
                                                    (into {})))
       (assoc :loading false
              :error nil))))

(def-event-fx
  ::logout
  (fn-traced [{:keys [db]} _]
    {:db (assoc db :auth-token nil)
     ::effects/set-token nil
     ::effects/navigate-to "/login"}))


(def-event-fx
 ::create-entry
 (fn-traced [{:keys [db]} [_ user-id {:keys [description start duration-hours duration-mins]}]]
   (let [form-data {:description description
                    :duration (+ (* duration-mins 1000 60) (* duration-hours 1000 60 60))
                    :start (tc/to-string start)}]
     {:db (assoc db :loading true)
      :http-xhrio {:method :post
                   :uri (if user-id
                          (str config/api-url "/users/" user-id "/time-sheet")
                          (str config/api-url "/time-sheet"))
                   :params form-data
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :headers (auth-header (:auth-token db))
                   :on-success [::entry-created user-id]
                   :on-failure [::request-failed]}})))

(def-event-fx
 ::entry-created
 (fn-traced [{:keys [db]} [_ user-id]]
   {:db db
    ::effects/navigate-to (if user-id (str "/users/" user-id "/entries") "/")}))


(def-event-fx
 ::update-entry
 (fn-traced [{:keys [db]} [_ user-id {:keys [id description start duration-hours duration-mins]}]]
   (let [form-data {:description description
                    :duration (+ (* duration-mins 1000 60) (* duration-hours 1000 60 60))
                    :start (tc/to-string start)}]
     {:db (assoc db :loading true)
      :http-xhrio {:method :put
                   :uri (if user-id
                          (str config/api-url "/users/" user-id "/time-sheet/" id)
                          (str config/api-url "/time-sheet/" id))
                   :params form-data
                   :format (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :headers (auth-header (:auth-token db))
                   :on-success [::entry-updated user-id]
                   :on-failure [::request-failed]}})))

(def-event-fx
 ::entry-updated
 (fn-traced [{:keys [db]} [_ user-id updated-entry]]
   {:db (assoc-in db [:time-sheet-entries user-id (:db/id updated-entry)] updated-entry)
    ::effects/navigate-to (if user-id (str "/users/" user-id "/entries") "/")}))

(def-event-fx
 ::delete-entry
 (fn-traced [{:keys [db]} [_ user-id id]]
   {:http-xhrio {:method :delete
                 :uri (if user-id
                        (str config/api-url "/users/" user-id "/time-sheet/" id)
                        (str config/api-url "/time-sheet/" id))
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :headers (auth-header (:auth-token db))
                 :on-success [::entry-deleted user-id]
                 :on-failure [::request-failed]}}))

(def-event-db
 ::entry-deleted
 (fn-traced [db [_ user-id {:keys [db/id]}]]
   (update-in db [:time-sheet-entries user-id] #(dissoc % id))))

(def-event-fx
 ::export-time-sheet
 (fn-traced [{:keys [db]} [_ user-id]]
   {::effects/save-file {:content (hiccup/render-html
                                   [:html
                                    [:body
                                     (->> (db/filtered-time-entries db user-id)
                                          (sort-by (comp tc/to-long tc/from-string :entry/start))
                                          (group-by (fn [entry]
                                                      (u/days-since-epoch (:entry/start entry))))
                                          (map (fn [[date-in-days entries]]
                                                 (let [total-time (->> entries
                                                                       (map :entry/duration)
                                                                       (reduce + 0))
                                                       interval (t/interval (t/epoch) (t/plus (t/epoch) (t/millis total-time)))
                                                       date-string (tf/unparse (tf/formatter "yyyy.MM.dd") (u/from-days-since-epoch date-in-days))
                                                       duration-string (if (zero? (t/in-hours interval))
                                                                         (str (t/in-minutes interval) "m")
                                                                         (str (t/in-hours interval) "h " (mod (t/in-minutes interval) 60) "m"))]
                                                   [:div
                                                    [:ul
                                                     [:li "Date: " date-string]
                                                     [:li "Total Time: " duration-string]
                                                     [:li "Notes:"
                                                      [:ul
                                                       (->> entries
                                                            (map (fn [e]
                                                                   [:li (:entry/description e)])))]]]]))))]])
                         :type "text/html"
                         :name "time-sheet.html"}}))


(def-event-db
 ::filter-start-date-updated
 (fn-traced [db [_ updated-date]]
   (assoc-in db [:filters :start-date] updated-date)))

(def-event-db
 ::filter-end-date-updated
 (fn-traced [db [_ updated-date]]
   (assoc-in db [:filters :end-date] updated-date)))


(def-event-db
 ::received-settings
 (fn-traced [db [_ settings]]
   (assoc db :settings settings
             :loading false
             :error nil)))


(def-event-fx
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


(def-event-fx
 ::settings-updated
 (fn-traced [{:keys [db]} [_ updated-settings]]
   {:db (assoc db :settings updated-settings)
    ::effects/navigate-to "/"}))

(def-event-db
 ::received-users
 (fn-traced [db [_ {:keys [users]}]]
   (assoc db :users (->> users
                         (map #(update % :user/role (partial map keyword)))
                         (map #(-> [(:db/id %) %]))
                         (into {}))
             :loading false
             :error nil)))

(def-event-fx
 ::delete-user
 (fn-traced [{:keys [db]} [_ id]]
   {:http-xhrio {:method :delete
                 :uri (str config/api-url "/users/" id)
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :headers (auth-header (:auth-token db))
                 :on-success [::user-deleted]
                 :on-failure [::request-failed]}}))

(def-event-db
 ::user-deleted
 (fn-traced [db [_ {:keys [db/id]}]]
   (update db :users #(dissoc % id))))

(def-event-fx
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

(def-event-fx
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

(def-event-fx
 ::user-updated
 (fn-traced [{:keys [db]} _]
   {:db db
    ::effects/navigate-to "/users"}))

