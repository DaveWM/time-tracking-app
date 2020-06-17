(ns time-management-client.routes
  (:require
    [bidi.bidi :as bidi]
    [pushy.core :as pushy]
    [re-frame.core :as re-frame]))

(def app-routes
  ["/" {"" :home
        "login" :login
        "register" :register
        "entries" {["/" :id] :edit-entry
                   true :create-entry}}
   true :not-found])

(defn set-page! [match]
  (re-frame/dispatch [:time-management-client.events/set-page (:handler match) (:route-params match)]))

(def history
  (pushy/pushy set-page! (partial bidi/match-route app-routes)))

(defn start-routing! []
  (pushy/start! history))

(defn navigate-to! [url]
  (pushy/set-token! history url))