(ns time-management-client.views
  (:require
    [re-frame.core :as re-frame]
    [time-management-client.subs :as subs]
    [time-management-client.events :as events]
    [time-management-client.utils :as u]
    [reagent.core :as r]
    [cljs-time.coerce :as tc]
    [cljs-time.format :as tf]
    [cljs-time.core :as t]))


;; home

(defn home-page []
  (let [time-sheet-entries (re-frame/subscribe [::subs/time-sheet-entries])
        loading            (re-frame/subscribe [::subs/loading])]
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
                   [:div.uk-card.uk-card-body.uk-card-default.uk-margin
                    [:h4.uk-card-title (tf/unparse (tf/formatter "do MMMM, yyyy") (u/from-days-since-epoch date-days))]
                    [:ul.uk-list.uk-list-divider
                     (->> entries
                          (map (fn [{:keys [entry/start entry/duration entry/description]}]
                                 (let [ended-at          (t/plus start (t/millis duration))
                                       interval          (t/interval start ended-at)
                                       started-at-string (tf/unparse (tf/formatter "HH:mm") start)
                                       duration-string   (if (zero? (t/in-hours interval))
                                                           (str (t/in-minutes interval) " minutes")
                                                           (str (t/in-hours interval) " hours, " (t/in-minutes interval) " minutes"))]
                                   [:li
                                    [:div description]
                                    [:div.uk-text-muted.uk-text-small (str started-at-string " for " duration-string)]]))))]])))
       [:a.uk-button.uk-button-primary {:href "/entries"} "New Entry"]])))



(defn form-input [type label value-atom]
  [:div.uk-margin
   [:label.uk-form-label {:for label} label]
   [:div.uk-form-controls
    [:input.uk-input {:type type
                      :id label
                      :value @value-atom
                      :on-change #(reset! value-atom (-> % .-target .-value))}]]])


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

        [form-input "text" "Email" email]
        [form-input "password" "Password" password]

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

        [form-input "text" "Email" email]
        [form-input "password" "Password" password]

        [:button.uk-button.uk-button-primary "Register"]]])))


(defn create-entry-page []
  (let [description (r/atom nil)
        duration-hours (r/atom 0)
        duration-mins (r/atom 0)
        start (r/atom nil)]
    [:form.uk-form-stacked {:on-submit #(do (re-frame/dispatch [::events/create-entry {:description @description
                                                                                       :duration-hours @duration-hours
                                                                                       :duration-mins @duration-mins
                                                                                       :start-string @start}])
                                            (.preventDefault %))}
     [form-input "text" "Description" description]
     [form-input "number" "Duration - Hours" duration-hours]
     [form-input "number" "Duration - Minutes" duration-mins]
     [form-input "text" "Start date/time (DD/MM/YY HH:mm)" start]
     [:button.uk-button.uk-button-primary "Create"]]))

(defn edit-entry-page [id]
  [:p id])


(defn show-page [page-name route-params]
  (case page-name
    :home [home-page]
    :login [login-page]
    :register [register-page]
    :create-entry [create-entry-page]
    :edit-entry [edit-entry-page (:id route-params)]
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
