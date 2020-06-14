(ns time-management-client.views
  (:require
    [re-frame.core :as re-frame]
    [time-management-client.subs :as subs]
    [time-management-client.events :as events]
    [reagent.core :as r]))


;; home

(defn home-page []
  [:div
   "Hello!"])



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

        [:a {:href "/#/register"} "Don't have an account? Click this link to register"]

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

        [:a {:href "/#/login"} "Already have an account? Click this link to log in"]

        [form-input "text" "Email" email]
        [form-input "password" "Password" password]

        [:button.uk-button.uk-button-primary "Register"]]])))


;; main

(defn pages [panel-name]
  (case panel-name
    :home [home-page]
    :login [login-page]
    :register [register-page]
    [:p "Page not found!"]))

(defn show-page [panel-name]
  [pages panel-name])

(defn main-panel []
  (let [page  (re-frame/subscribe [::subs/page])
        error (re-frame/subscribe [::subs/error])]
    [:div
     [:div#navbar.uk-navbar-container
      [:div.uk-navbar-left
       [:h1.uk-navbar-item.uk-logo "Time Management App"]]]
     [:div.uk-section
      [:div.uk-container.uk-container-large
       (when @error [:div.uk-alert-danger {"uk-alert" ""}
                     [:a.uk-alert-close {"uk-close" ""}]
                     [:p @error]])
       [show-page @page]]]]))
