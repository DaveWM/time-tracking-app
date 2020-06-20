(ns time-management-client.events
  (:require
   [re-frame.core :as re-frame]
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [ajax.core :as ajax]
   [cljs-time.coerce :as tc]
   [cljs-time.format :as tf]
   [cljs-time.core :as t]
   [hiccups.runtime :as hiccup]
   ["jwt-decode" :as jwt-decode]
   [time-management-client.db :as db]
   [time-management-client.effects :as effects]
   [time-management-client.coeffects :as coeffects]
   [time-management-client.config :as config]
   [time-management-client.routes :as routes]))

(defn auth-header [token]
  [:Authorization (str "Token " token)])


(defn effects-on-page-load [page db]
  (let [load-time-sheet-effect {:method :get
                                :uri (str config/api-url "/time-sheet")
                                :response-format (ajax/json-response-format {:keywords? true})
                                :headers (auth-header (:auth-token db))
                                :on-success [::received-time-sheet]
                                :on-failure [::request-failed]}
        load-settings-effect {:method :get
                              :uri (str config/api-url "/settings")
                              :response-format (ajax/json-response-format {:keywords? true})
                              :headers (auth-header (:auth-token db))
                              :on-success [::received-settings]
                              :on-failure [::request-failed]}]
    (case page
      :home {:db (assoc db :loading true)
             :http-xhrio [load-time-sheet-effect load-settings-effect]}
      :edit-entry {:db (assoc db :loading true)
                   :http-xhrio load-time-sheet-effect}
      :settings {:db (assoc db :loading true)
                 :http-xhrio load-settings-effect}
      :users {:db (assoc db :loading true)
              :http-xhrio {:method :get
                           :uri (str config/api-url "/users")
                           :response-format (ajax/json-response-format {:keywords? true})
                           :headers (auth-header (:auth-token db))
                           :on-success [::received-users]
                           :on-failure [::request-failed]}}
      nil)))

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
       (clojure.set/subset? route-roles user-roles) (-> (merge {:db db}
                                                               (effects-on-page-load page db))
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
 (fn-traced [{:keys [db]} [_ {:keys [id description start duration-hours duration-mins]}]]
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

(re-frame/reg-event-fx
 ::export-time-sheet
 (fn-traced [{:keys [db]} _]
   {::effects/save-file {:content (hiccup/render-html
                                   [:html
                                    [:body
                                     (->> (db/filtered-time-entries db)
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
