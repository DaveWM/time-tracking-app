(ns time-management-client.events-test
  (:require [clojure.test :refer-macros [deftest is testing]]
            [clojure.string :as s]
            [cljs-time.coerce :as tc]
            [hiccup-find.core :as hf]
            [hiccups.runtime :as hiccup]
            [time-management-client.effects :as effects]
            [time-management-client.config :as config]
            [time-management-client.events :as sut]))


(deftest initialize-test
  (let [token "token"]
    (is (= token
           (get-in (sut/initialize-db {:auth-token token}) [:db :auth-token])))))

(deftest set-page-tests
  (testing "accessing the login/register page when not logged in"
    (let [result (sut/set-page {:db {:auth-token nil}} [::sut/set-page :login])]
      (is (= :login (get-in result [:db :page]))))
    (let [result (sut/set-page {:db {:auth-token nil}} [::sut/set-page :register])]
      (is (= :register (get-in result [:db :page])))))

  (testing "should not be able to go to the home page when not logged in"
    (let [result (sut/set-page {:db {:auth-token nil
                                     :page :login}}
                               [::sut/set-page :home])]
      (is (= :login (get-in result [:db :page])))
      (is (= "/login" (::effects/navigate-to result)))))

  (testing "when logged in"
    ;; token with all 3 roles (user, manager, and admin)
    (let [admin-role-token "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VyLWlkIjoxMjMsImVtYWlsIjoiYWRtaW5AZ21haWwuY29tIiwicm9sZXMiOlsicm9sZS9tYW5hZ2VyIiwicm9sZS91c2VyIiwicm9sZS9hZG1pbiJdLCJleHAiOjE1OTI5MjYxNjZ9.oF5yrMjXs_B3SwVvfIaZsV7XFPbFqb5iDIbNJWkGdlsSIT3KaLmDjdXgMHMtRhooqPwsUQpfZ9FzjVhYGLzFgg"
          user-role-token "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VyLWlkIjoyMzQsImVtYWlsIjoidXNlckBnbWFpbC5jb20iLCJyb2xlcyI6WyJyb2xlL3VzZXIiXSwiZXhwIjoxNTkyOTI2NDM0fQ.hTnm7SgJF3pms2UYA6ggxx0LSb-dSL9wp_mq2clnfJp1az30YKE67B2zM_CJTRUMRdwXJofHP7j1giTo19-RAA"]
      (testing "accessing the home page"
        (let [result (sut/set-page {:db {:auth-token user-role-token}} [::sut/set-page :home])
              xhr-requests (->> result :http-xhrio (map #(select-keys % [:method :uri :headers])) set)]
          (is (= :home (get-in result [:db :page])))
          ;; makes a request for the time sheet, and one for settings also
          (is (= #{{:uri (str config/api-url "/time-sheet")
                    :method :get
                    :headers [:Authorization (str "Token " user-role-token)]}
                   {:uri (str config/api-url "/settings")
                    :method :get
                    :headers [:Authorization (str "Token " user-role-token)]}}
                 xhr-requests))))
      (testing "accessing the settings page"
        (let [result (sut/set-page {:db {:auth-token user-role-token}} [::sut/set-page :settings])]
          (is (= :settings (get-in result [:db :page])))
          (is (= :get (get-in result [:http-xhrio :method])))
          (is (= [:Authorization (str "Token " user-role-token)] (get-in result [:http-xhrio :headers])))
          (is (s/ends-with? (get-in result [:http-xhrio :uri]) "/settings"))))

      (testing "accessing the user management page as a normal user"
        (let [result (sut/set-page {:db {:auth-token user-role-token}} [::sut/set-page :users])]
          (is (= :not-authorized (get-in result [:db :page])))))
      (testing "accessing the user management page as a user with the manager role"
        (let [result (sut/set-page {:db {:auth-token admin-role-token}} [::sut/set-page :users])]
          (is (= :users (get-in result [:db :page])))
          (is (= :get (get-in result [:http-xhrio :method])))
          (is (= [:Authorization (str "Token " admin-role-token)] (get-in result [:http-xhrio :headers])))
          (is (s/ends-with? (get-in result [:http-xhrio :uri]) "/users"))))

      (testing "accessing another user's timesheet as a normal user"
        (let [result (sut/set-page {:db {:auth-token user-role-token}} [::sut/set-page :user-entries {:user-id 123}])]
          (is (= :not-authorized (get-in result [:db :page])))))
      (testing "accessing another user's timesheet as an admin user"
        (let [result (sut/set-page {:db {:auth-token admin-role-token}} [::sut/set-page :user-entries {:user-id 123}])]
          (is (= :user-entries (get-in result [:db :page])))
          (is (= :get (get-in result [:http-xhrio :method])))
          (is (= [:Authorization (str "Token " admin-role-token)] (get-in result [:http-xhrio :headers])))
          (is (s/ends-with? (get-in result [:http-xhrio :uri]) "/users/123/time-sheet")))))))


(deftest register-test
  (let [post-data {:data true}
        {:keys [db http-xhrio]} (sut/register {:db {}} [::sut/register post-data])]
    (is (true? (:loading db)))
    (is (= :post (:method http-xhrio)))
    (is (s/ends-with? (:uri http-xhrio) "/register"))
    (is (= [::sut/login-success] (:on-success http-xhrio)))
    (is (= post-data (:params http-xhrio)))))

(deftest login-test
  (let [post-data {:data true}
        {:keys [db http-xhrio]} (sut/login {:db {}} [::sut/login post-data])]
    (is (true? (:loading db)))
    (is (= :post (:method http-xhrio)))
    (is (s/ends-with? (:uri http-xhrio) "/login"))
    (is (= [::sut/login-success] (:on-success http-xhrio)))
    (is (= post-data (:params http-xhrio)))))

(deftest login-success-test
  (let [token "token"
        {:keys [db ::effects/set-token ::effects/navigate-to]} (sut/login-success {:db {}} [::sut/login-success {:token token}])]
    (is (false? (:loading db)))
    (is (= token (:auth-token db)))
    (is (= token set-token))
    (is (= navigate-to "/"))))

(deftest request-failed-test
  (testing "500 response"
    (let [error "some error"
          {:keys [db ::effects/navigate-to]} (sut/request-failed {:db {}} [::sut/request-failed {:status 500
                                                                                                 :response {:error error}}])]
      (is (false? (:loading db)))
      (is (= error (:error db)))
      (is (= navigate-to nil))))
  (testing "401 response"
    (let [error "auth error"
          {:keys [db ::effects/navigate-to]} (sut/request-failed {:db {}} [::sut/request-failed {:status 401
                                                                                                 :response {:error error}}])]
      (is (false? (:loading db)))
      (is (= error (:error db)))
      (is (= navigate-to "/login")))))

(deftest received-time-sheet-test
  (let [response {:time-sheet-entries [{:db/id 1}]}
        db (sut/received-time-sheet {} [::sut/received-time-sheet 123 response])]
    (is (false? (:loading db)))
    (is (= {123 {1 {:db/id 1}}}
           (:time-sheet-entries db)))))


(deftest create-entry-test
  (testing "for the current user"
    (let [{:keys [db http-xhrio]}
          (sut/create-entry {:db {:auth-token "token"}}
                            [::sut/create-entry nil {:description "desc"
                                                     :start (tc/from-string "2020-01-01")
                                                     :duration-hours 1
                                                     :duration-mins 15}])]
      (is (true? (:loading db)))
      (is (= {:description "desc"
              :duration (+ (* 1 1000 60 60)
                           (* 15 1000 60))
              :start (tc/to-string "2020-01-01")}
             (:params http-xhrio)))
      (is (= :post (:method http-xhrio)))
      (is (= [:Authorization "Token token"] (:headers http-xhrio)))
      (is (s/ends-with? (:uri http-xhrio) "/time-sheet"))))
  (testing "for another user"
    (let [{:keys [db http-xhrio]}
          (sut/create-entry {:db {:auth-token "token"}}
                            [::sut/create-entry 123 {:description "desc"
                                                     :start (tc/from-string "2020-01-01")
                                                     :duration-hours 1
                                                     :duration-mins 15}])]
      (is (true? (:loading db)))
      (is (= {:description "desc"
              :duration (+ (* 1 1000 60 60)
                           (* 15 1000 60))
              :start (tc/to-string "2020-01-01")}
             (:params http-xhrio)))
      (is (= [:Authorization "Token token"] (:headers http-xhrio)))
      (is (= :post (:method http-xhrio)))
      (is (s/ends-with? (:uri http-xhrio) "/users/123/time-sheet")))))

(deftest update-entry-test
  (testing "for the current user"
    (let [{:keys [db http-xhrio]}
          (sut/update-entry {:db {:auth-token "token"}}
                            [::sut/update-entry nil {:id 1
                                                     :description "desc"
                                                     :start (tc/from-string "2020-01-01")
                                                     :duration-hours 1
                                                     :duration-mins 15}])]
      (is (true? (:loading db)))
      (is (= {:description "desc"
              :duration (+ (* 1 1000 60 60)
                           (* 15 1000 60))
              :start (tc/to-string "2020-01-01")}
             (:params http-xhrio)))
      (is (= [:Authorization "Token token"] (:headers http-xhrio)))
      (is (= :put (:method http-xhrio)))
      (is (s/ends-with? (:uri http-xhrio) "/time-sheet/1"))))
  (testing "for another user"
    (let [{:keys [db http-xhrio]}
          (sut/update-entry {:db {:auth-token "token"}}
                            [::sut/update-entry 123 {:id 1
                                                     :description "desc"
                                                     :start (tc/from-string "2020-01-01")
                                                     :duration-hours 1
                                                     :duration-mins 15}])]
      (is (true? (:loading db)))
      (is (= {:description "desc"
              :duration (+ (* 1 1000 60 60)
                           (* 15 1000 60))
              :start (tc/to-string "2020-01-01")}
             (:params http-xhrio)))
      (is (= [:Authorization "Token token"] (:headers http-xhrio)))
      (is (= :put (:method http-xhrio)))
      (is (s/ends-with? (:uri http-xhrio) "/users/123/time-sheet/1")))))

(deftest delete-entry-test
  (testing "for the current user"
    (let [{:keys [http-xhrio]}
          (sut/delete-entry {:db {:auth-token "token"}} [::sut/delete-entry nil 1])]
      (is (= :delete (:method http-xhrio)))
      (is (= [:Authorization "Token token"] (:headers http-xhrio)))
      (is (s/ends-with? (:uri http-xhrio) "/time-sheet/1"))))
  (testing "for another user"
    (let [{:keys [http-xhrio]}
          (sut/delete-entry {:db {:auth-token "token"}} [::sut/delete-entry 123 1])]
      (is (= :delete (:method http-xhrio)))
      (is (= [:Authorization "Token token"] (:headers http-xhrio)))
      (is (s/ends-with? (:uri http-xhrio) "/users/123/time-sheet/1")))))


(deftest export-time-sheet-test
  (testing "for current user"
    (let [db {:time-sheet-entries {nil {1 {:entry/description "desc"
                                           ;; 2 hours 15 mins
                                           :entry/duration (+ (* 2 1000 60 60)
                                                              (* 15 1000 60))
                                           :entry/start "2020-01-01"}}}}
          {:keys [::effects/save-file]} (sut/export-time-sheet {:db db} [::sut/export-time-sheet nil])]
      (is (s/ends-with? (:name save-file) ".html"))
      (is (s/includes? (:content save-file) "desc"))
      (is (s/includes? (:content save-file) "2h 15m")))))

(deftest update-settings-test
  (let [{:keys [db http-xhrio]}
        (sut/update-settings {:db {:auth-token "token"}}
                             [::sut/update-settings {:preferred-working-hours 7}])]
    (is (true? (:loading db)))
    (is (= {:preferred-working-hours 7}
           (:params http-xhrio)))
    (is (= :put (:method http-xhrio)))
    (is (= [:Authorization "Token token"] (:headers http-xhrio)))
    (is (s/ends-with? (:uri http-xhrio) "/settings"))))

(deftest delete-user-test
  (let [{:keys [http-xhrio]}
        (sut/delete-user {:db {:auth-token "token"}}
                         [::sut/delete-user 123])]
    (is (= :delete (:method http-xhrio)))
    (is (= [:Authorization "Token token"] (:headers http-xhrio)))
    (is (s/ends-with? (:uri http-xhrio) "/users/123"))))

(deftest create-user-test
  (let [{:keys [db http-xhrio]}
        (sut/create-user {:db {:auth-token "token"}}
                         [::sut/create-user {:email "test@gmail.com"
                                             :password "password123"
                                             :roles #{:role/user}}])]
    (is (true? (:loading db)))
    (is (= {:email "test@gmail.com"
            :password "password123"
            :roles ["role/user"]}
           (:params http-xhrio)))
    (is (= :post (:method http-xhrio)))
    (is (= [:Authorization "Token token"] (:headers http-xhrio)))
    (is (s/ends-with? (:uri http-xhrio) "/users"))))

(deftest update-user-test
  (let [{:keys [db http-xhrio]}
        (sut/update-user {:db {:auth-token "token"}}
                         [::sut/update-user {:id 123
                                             :email "test@gmail.com"
                                             :password "password123"
                                             :roles #{:role/user}}])]
    (is (true? (:loading db)))
    (is (= {:email "test@gmail.com"
            :password "password123"
            :roles ["role/user"]}
           (:params http-xhrio)))
    (is (= [:Authorization "Token token"] (:headers http-xhrio)))
    (is (= :put (:method http-xhrio)))
    (is (s/ends-with? (:uri http-xhrio) "/users/123"))))


