(ns time-management-client.views
  (:require
   [re-frame.core :as re-frame]
   [time-management-client.subs :as subs]
   [time-management-client.events :as events]
   [time-management-client.utils :as u]
   [reagent.core :as r]
   ["react-datepicker" :default DatePicker]
   [cljs-time.coerce :as tc]
   [cljs-time.format :as tf]
   [cljs-time.core :as t]
   [cljs-time.local :as tl]))


(def date-picker* (r/adapt-react-class DatePicker))

(defn date-picker [{:keys [on-change] :as props}]
  [date-picker* (-> props
                    (assoc :on-change (fn [bad-date]
                                        (on-change
                                         ;; workaround for this bug: https://github.com/Hacker0x01/react-datepicker/issues/1018
                                         ;; round date to nearest day
                                         (-> bad-date
                                             (tc/from-date)
                                             (t/plus (t/hours 12))
                                             (t/at-midnight)
                                             (tc/to-date))))))])

(def spinner [:div {"uk-spinner" "ratio: 2"}])


(defn time-entries-page [user-id]
  (let [time-sheet-entries (re-frame/subscribe [::subs/time-sheet-entries user-id])
        loading (re-frame/subscribe [::subs/loading])
        {:keys [start-date end-date]} @(re-frame/subscribe [::subs/filters])
        {:keys [settings/preferred-working-hours]} @(re-frame/subscribe [::subs/settings])]
    (if @loading
      spinner
      [:div.home
       [:div.uk-card.uk-card-body.uk-card-default.uk-margin
        [:h4.uk-card-title "Filters"]
        [:form.uk-grid-small.uk-form-stacked {"uk-grid" "true"}
         [:div {:class "uk-width-1-2@s"}
          [:label.uk-form-label {:for "start-date"} "Start Date"]
          [date-picker {:selected start-date
                        :id "start-date"
                        :show-time-select false
                        :on-change #(re-frame/dispatch [::events/filter-start-date-updated %])}]]
         [:div {:class "uk-width-1-2@s"}
          [:label.uk-form-label {:for "end-date"} "End Date"]
          [date-picker {:selected end-date
                        :id "end-date"
                        :show-time-select false
                        :on-change #(re-frame/dispatch [::events/filter-end-date-updated %])}]]]]
       (->> @time-sheet-entries
            (map (fn [entry]
                   (update entry :entry/start tc/from-string)))
            (sort-by (comp tc/to-epoch :entry/start))
            (group-by (fn [entry]
                        (u/days-since-epoch (:entry/start entry))))
            (map (fn [[date-days entries]]
                   (let [total-worked-hours (->> entries
                                                 (map :entry/duration)
                                                 (map #(/ % (* 1000 60 60)))
                                                 (reduce + 0))
                         over-preferred-hours? (< preferred-working-hours total-worked-hours)]
                     ^{:key date-days}
                     [:div.uk-card.uk-card-body.uk-card-default.uk-margin.day-entry
                      {:class (when (nil? user-id)
                                (if over-preferred-hours?
                                  "day-entry--warning"
                                  "day-entry--ok"))}
                      [:h4.uk-card-title (tf/unparse (tf/formatter "do MMMM, yyyy") (u/from-days-since-epoch date-days))]
                      [:ul.uk-list.uk-list-divider
                       (->> entries
                            (map (fn [{:keys [entry/start entry/duration entry/description db/id]}]
                                   (let [ended-at (t/plus start (t/millis duration))
                                         interval (t/interval start ended-at)
                                         duration-string (if (zero? (t/in-hours interval))
                                                           (str (t/in-minutes interval) " minutes")
                                                           (str (t/in-hours interval) " hours, " (mod (t/in-minutes interval) 60) " minutes"))]
                                     ^{:key id}
                                     [:li.time-entry
                                      [:div
                                       [:div description]
                                       [:div.uk-text-muted.uk-text-small duration-string]]
                                      [:div.time-entry__controls
                                       [:a.uk-button.uk-button-default
                                        {:href (if user-id
                                                 (str "/users/" user-id "/entries/" id)
                                                 (str "/entries/" id))}
                                        "Edit"]
                                       [:button.uk-button.uk-button-danger {:on-click #(re-frame/dispatch [::events/delete-entry user-id id])} "Delete"]]]))))]]))))
       [:a.uk-button.uk-button-primary {:href (if user-id
                                                (str "/users/" user-id "/entries/new")
                                                "/entries/new")} "New Entry"]
       (when-not (empty? @time-sheet-entries)
         [:button.uk-button.uk-button-default {:on-click #(re-frame/dispatch [::events/export-time-sheet user-id])} "Export as HTML"])])))



(defn form-input [label value-atom & [input-attrs]]
  [:div.uk-margin
   [:label.uk-form-label {:for label} label]
   [:div.uk-form-controls
    [:input.uk-input (merge {:type "text"
                             :id label
                             :value @value-atom
                             :on-change #(reset! value-atom (-> % .-target .-value))}
                            input-attrs)]]])


(defn login-page []
  (let [email    (r/atom nil)
        password (r/atom nil)]
    (fn []
      [:div
       [:legend.uk-legend "Log In"]
       [:form.uk-form-stacked {:on-submit #(do
                                             (re-frame/dispatch [::events/login {:email @email :password @password}])
                                             (.preventDefault %))}

        [:a {:href "/register"} "Don't have an account? Click this link to register"]

        [form-input "Email" email {:required "true"}]
        [form-input "Password" password {:type "password"
                                         :required "true"}]

        [:button.uk-button.uk-button-primary "Log In"]]])))

(defn register-page []
  (let [email    (r/atom nil)
        password (r/atom nil)]
    (fn []
      [:div
       [:legend.uk-legend "Register"]
       [:form.uk-form-stacked {:on-submit #(do
                                             (re-frame/dispatch [::events/register {:email @email :password @password}])
                                             (.preventDefault %))}

        [:a {:href "/login"} "Already have an account? Click this link to log in"]

        [form-input "Email" email {:required "true"}]
        [form-input "Password" password {:type "password"
                                         :required "true"}]

        [:button.uk-button.uk-button-primary "Register"]]])))

(defn time-entry-form [id submit-btn-label submit-event {:keys [description duration-hours duration-mins start]}]
  [:form.uk-form-stacked {:on-submit #(do (re-frame/dispatch (conj submit-event {:description @description
                                                                                 :duration-hours @duration-hours
                                                                                 :duration-mins @duration-mins
                                                                                 :start @start
                                                                                 :id id}))
                                          (.preventDefault %))}
   [form-input "Description" description {:required "true"}]
   [form-input "Duration - Hours" duration-hours {:type "number"
                                                  :min 0
                                                  :step 1
                                                  :required "true"}]
   [form-input "Duration - Minutes" duration-mins {:type "number"
                                                   :min 0
                                                   :max 59
                                                   :step 1
                                                   :required "true"}]
   [:div.uk-margin
    [:label.uk-form-label {:for "start-date"} "Start"]
    [:div.uk-form-controls
     [date-picker {:id "start-date"
                   :show-time-select false
                   :selected @start
                   :on-change #(reset! start %)}]]]
   [:button.uk-button.uk-button-primary submit-btn-label]])

(defn create-entry-page [user-id]
  (let [description (r/atom nil)
        duration-hours (r/atom 0)
        duration-mins (r/atom 0)
        start (r/atom nil)]
    [time-entry-form nil "Create" [::events/create-entry user-id] {:description description
                                                                   :duration-hours duration-hours
                                                                   :duration-mins duration-mins
                                                                   :start start}]))

(defn edit-entry-page [user-id id]
  (if-let [entry @(re-frame/subscribe [::subs/time-entry user-id id])]
    (let [description (r/atom (:entry/description entry))
          duration-hours (r/atom (-> (:entry/duration entry)
                                     (quot (* 1000 60 60))))
          duration-mins (r/atom (-> (:entry/duration entry)
                                    (quot (* 1000 60))
                                    (rem 60)))
          start (r/atom (tc/to-date (tc/from-string (:entry/start entry))))]
      [time-entry-form id "Update" [::events/update-entry user-id] {:description description
                                                                    :duration-hours duration-hours
                                                                    :duration-mins duration-mins
                                                                    :start start}])
    spinner))


(defn settings-page []
  (let [settings (re-frame/subscribe [::subs/settings])]
    (if (some? @settings)
      (let [preferred-working-hours (r/atom (:settings/preferred-working-hours @settings))]
        [:form.uk-form-stacked {:on-submit #(do
                                              (re-frame/dispatch [::events/update-settings {:preferred-working-hours (long @preferred-working-hours)}])
                                              (.preventDefault %))}
         [:legend.uk-legend "Settings"]
         [form-input "Preferred Working Hours per Day" preferred-working-hours {:type "number"
                                                                                :min 0
                                                                                :step 1}]
         [:button.uk-button.uk-button-primary "Save"]])
      spinner)))

(defn users-page []
  (let [users (re-frame/subscribe [::subs/all-users])
        loading (re-frame/subscribe [::subs/loading])]
    (if @loading
      spinner
      [:div.users
       [:h2 "Users"]
       [:ul.uk-list.uk-list-divider.users-list
        (->> @users
             (map (fn [{:keys [db/id user/email user/role]}]
                    [:li.users-list__item
                     [:div
                      [:div.uk-text-bold email]
                      [:div
                       (->> role
                            (map #(-> [:span.uk-label.users-list__role (name %)])))]]
                     [:div
                      [:a.uk-button.uk-button-default {:href (str "/users/" id "/entries")} "Time Sheet"]
                      [:a.uk-button.uk-button-default {:href (str "/users/" id)} "Edit"]
                      [:button.uk-button.uk-button-danger {:on-click #(re-frame/dispatch [::events/delete-user id])} "Delete"]]])))]
       [:a.uk-button.uk-button-primary {:href "/users/new"} "New User"]])))

(defn user-form [id submit-btn-label submit-event {:keys [email password roles]}]
  (let [all-roles [:role/user :role/manager :role/admin]]
    [:form.uk-form-stacked {:on-submit #(do (re-frame/dispatch [submit-event {:email @email
                                                                              :password @password
                                                                              :roles @roles
                                                                              :id id}])
                                            (.preventDefault %))}
     [form-input "Email" email {:required "true"}]
     [form-input "Password" password {:type "password"
                                      :placeholder (when id "Leave blank to not change")}]
     [:div.uk-margin
      [:label.uk-form-label "Roles"]
      [:div.uk-form-controls
       (let [rs @roles]
         [:div.uk-button-group
          (->> all-roles
               (map (fn [role]
                      (let [role-selected? (contains? rs role)]
                        ^{:key role}
                        [:button.uk-button {:type "button"
                                            :class (if role-selected?
                                                     "uk-button-primary"
                                                     "uk-button-default")
                                            :on-click #(if role-selected?
                                                        (swap! roles disj role)
                                                        (swap! roles conj role))}
                         (name role)]))))])]]
     [:button.uk-button.uk-button-primary {:type "submit"} submit-btn-label]]))

(defn create-user-page []
  (let [email (r/atom nil)
        password (r/atom nil)
        roles (r/atom #{:role/user})]
    [user-form nil "Create" ::events/create-user {:email email
                                                  :password password
                                                  :roles roles}]))

(defn edit-user-page [id]
  (if-let [user @(re-frame/subscribe [::subs/user id])]
    (let [email (r/atom (:user/email user))
          password (r/atom nil)
          roles (r/atom (set (:user/role user)))]
      [user-form id "Update" ::events/update-user {:email email
                                                   :password password
                                                   :roles roles}])
    spinner))

(defn not-authorized-page []
  [:div.uk-alert-danger {"uk-alert" ""}
   [:a.uk-alert-close {"uk-close" ""}]
   [:p "You don't have permission to access this page. Please contact your administrator."]])


(defn show-page [page-name route-params]
  (case page-name
    :home [time-entries-page nil]
    :login [login-page]
    :register [register-page]
    :create-entry [create-entry-page nil]
    :edit-entry [edit-entry-page nil (:id route-params)]
    :settings [settings-page]
    :users [users-page]
    :create-user [create-user-page]
    :edit-user [edit-user-page (:id route-params)]
    :user-entries [time-entries-page (:user-id route-params)]
    :create-user-entry [create-entry-page (:user-id route-params)]
    :edit-user-entry [edit-entry-page (:user-id route-params) (:id route-params)]
    :not-found [:p "Page not found!"]
    :not-authorized [not-authorized-page]
    [:div]))

(defn main-panel []
  (let [page-sub (re-frame/subscribe [::subs/page])
        error (re-frame/subscribe [::subs/error])]
    (let [{:keys [page params]} @page-sub]
      [:div
       [:div#navbar.uk-navbar-container {"uk-navbar" "true"}
        [:div.uk-navbar-left
         [:a.uk-navbar-item.uk-logo
          {:href "/"}
          "Time Management App"]]
        [:div.uk-navbar-right
         [:div.uk-navbar-item
          [:a.uk-button.uk-button-primary {:href "/users"} "Users"]
          [:a.uk-button.uk-button-primary {:href "/settings"} "Settings"]
          [:button.uk-button.uk-button-primary {:on-click #(re-frame/dispatch [::events/logout])} "Log out"]]]]
       [:div.uk-section
        [:div.uk-container.uk-container-large
         (when @error [:div.uk-alert-danger {"uk-alert" ""}
                       [:a.uk-alert-close {"uk-close" ""}]
                       [:p @error]])
         [show-page page params]]]])))
