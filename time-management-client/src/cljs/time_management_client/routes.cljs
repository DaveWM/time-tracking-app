(ns time-management-client.routes
  (:require
    [bidi.bidi :as bidi]
    [pushy.core :as pushy]
    [re-frame.core :as re-frame]))

(def app-routes
  ["/" {"" :home
        "login" :login
        "register" :register}
   true :not-found])

(defn set-page! [match]
  (re-frame/dispatch [:time-management-client.events/set-page (:handler match)]))

(def history
  (pushy/pushy set-page! (partial bidi/match-route app-routes)))

(defn start-routing! []
  (pushy/start! history))

(defn navigate-to! [url]
  (pushy/set-token! history url))