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
   [cljs-time.core :as t]))


(def date-picker (r/adapt-react-class DatePicker))


(defn home-page []
  (let [time-sheet-entries (re-frame/subscribe [::subs/time-sheet-entries])
        loading (re-frame/subscribe [::subs/loading])]
    (if @loading
      [:div {"uk-spinner" "ratio: 2"}]
      [:div.home
       (->> @time-sheet-entries
            (map (fn [entry]
                   (update entry :entry/start tc/from-string)))
            (sort-by (comp tc/to-epoch :entry/start))
            (group-by (fn [entry]
                        (u/days-since-epoch (:entry/start entry))))
            (map (fn [[date-days entries]]
                   ^{:key date-days}
                   [:div.uk-card.uk-card-body.uk-card-default.uk-margin
                    [:h4.uk-card-title (tf/unparse (tf/formatter "do MMMM, yyyy") (u/from-days-since-epoch date-days))]
                    [:ul.uk-list.uk-list-divider
                     (->> entries
                          (map (fn [{:keys [entry/start entry/duration entry/description db/id]}]
                                 (let [ended-at (t/plus start (t/millis duration))
                                       interval (t/interval start ended-at)
                                       started-at-string (tf/unparse (tf/formatter "HH:mm") start)
                                       duration-string (if (zero? (t/in-hours interval))
                                                         (str (t/in-minutes interval) " minutes")
                                                         (str (t/in-hours interval) " hours, " (mod (t/in-minutes interval) 60) " minutes"))]
                                   ^{:key id}
                                   [:li.time-entry
                                    [:div
                                     [:div description]
                                     [:div.uk-text-muted.uk-text-small (str started-at-string " for " duration-string)]]
                                    [:div.time-entry__controls
                                     [:a.uk-button.uk-button-default {:href (str "/entries/" id)} "Edit"]
                                     [:button.uk-button.uk-button-danger {:on-click #(re-frame/dispatch [::events/delete-entry id])} "Delete"]]]))))]])))
       [:a.uk-button.uk-button-primary {:href "/entries"} "New Entry"]
       (when-not (empty? @time-sheet-entries)
         [:button.uk-button.uk-button-default {:on-click #(re-frame/dispatch [::events/export-time-sheet])} "Export as HTML"])])))



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

        [form-input "Email" email]
        [form-input "Password" password {:type "password"}]

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

        [form-input "Email" email]
        [form-input "Password" password {:type "password"}]

        [:button.uk-button.uk-button-primary "Register"]]])))

(defn time-entry-form [id submit-btn-label submit-event {:keys [description duration-hours duration-mins start]}]
  [:form.uk-form-stacked {:on-submit #(do (re-frame/dispatch [submit-event {:description @description
                                                                            :duration-hours @duration-hours
                                                                            :duration-mins @duration-mins
                                                                            :start @start
                                                                            :id id}])
                                          (.preventDefault %))}
   [form-input "Description" description]
   [form-input "Duration - Hours" duration-hours {:type "number"
                                                  :min 0
                                                  :step 1}]
   [form-input "Duration - Minutes" duration-mins {:type "number"
                                                   :min 0
                                                   :max 59
                                                   :step 1}]
   [:div.uk-margin
    [:label.uk-form-label {:for "start-date"} "Start"]
    [:div.uk-form-controls
     [date-picker {:id "start-date"
                   :show-time-select true
                   :selected @start
                   :on-change #(reset! start %)}]]]
   [:button.uk-button.uk-button-primary submit-btn-label]])

(defn create-entry-page []
  (let [description (r/atom nil)
        duration-hours (r/atom 0)
        duration-mins (r/atom 0)
        start (r/atom nil)]
    [time-entry-form nil "Create" ::events/create-entry {:description description
                                                         :duration-hours duration-hours
                                                         :duration-mins duration-mins
                                                         :start start}]))

(defn edit-entry-page [id]
  (if-let [entry @(re-frame/subscribe [::subs/time-entry id])]
    (let [description (r/atom (:entry/description entry))
          duration-hours (r/atom (-> (:entry/duration entry)
                                     (quot (* 1000 60 60))))
          duration-mins (r/atom (-> (:entry/duration entry)
                                    (quot (* 1000 60))
                                    (rem 60)))
          start (r/atom (tc/to-date (tc/from-string (:entry/start entry))))]
      [time-entry-form id "Update" ::events/update-entry {:description description
                                                          :duration-hours duration-hours
                                                          :duration-mins duration-mins
                                                          :start start}])
    [:div {"uk-spinner" "ratio: 2"}]))


(defn show-page [page-name route-params]
  (case page-name
    :home [home-page]
    :login [login-page]
    :register [register-page]
    :create-entry [create-entry-page]
    :edit-entry [edit-entry-page (js/parseInt (:id route-params))]
    :not-found [:p "Page not found!"]
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
          [:button.uk-button.uk-button-primary {:on-click #(re-frame/dispatch [::events/logout])} "Log out"]]]]
       [:div.uk-section
        [:div.uk-container.uk-container-large
         (when @error [:div.uk-alert-danger {"uk-alert" ""}
                       [:a.uk-alert-close {"uk-close" ""}]
                       [:p @error]])
         [show-page page params]]]])))
