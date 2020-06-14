(ns time-management-client.views
  (:require
   [re-frame.core :as re-frame]
   [time-management-client.subs :as subs]
   ))


;; home

(defn home-page []
  [:div
   "Hello!"])


;; main

(defn pages [panel-name]
  (case panel-name
    :home [home-page]
    [:div "Page not found!"]))

(defn show-page [panel-name]
  [pages panel-name])

(defn main-panel []
  (let [page (re-frame/subscribe [::subs/page])]
    [:div
     [:div#navbar.uk-navbar-container
      [:div.uk-navbar-left
       [:h1.uk-navbar-item.uk-logo "Time Management App"]]]
     [:div.uk-section
      [:div.uk-container.uk-container-large
       [show-page @page]]]]))
