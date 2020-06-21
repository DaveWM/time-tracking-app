(ns time-management-client.routes
  (:require
    [bidi.bidi :as bidi]
    [pushy.core :as pushy]
    [re-frame.core :as re-frame]))

(def app-routes
  ["/" {"" :home
        "login" :login
        "register" :register
        "entries" {"/new" :create-entry
                   ["/" [long :id]] :edit-entry}
        "settings" :settings
        "users" {"/new" :create-user
                 ["/" [long :id]] :edit-user
                 ["/" [long :user-id] "/entries"] {"/new" :create-user-entry
                                                   ["/" [long :id]] :edit-user-entry
                                                   "" :user-entries}
                 "" :users}
        true :not-found}])

(def page->roles
  "A map of page name to the roles necessary to access it.
   If a route is not present, it may be accessed without authentication."
  {:home #{:role/user}
   :entries #{:role/user}
   :settings #{:role/user}
   :users #{:role/manager}
   :user-time-sheet #{:role/admin}
   :create-user-entry #{:role/admin}
   :edit-user-entry #{:role/admin}
   :user-entries #{:role/admin}})

(defn set-page! [match]
  (re-frame/dispatch [:time-management-client.events/set-page (:handler match) (:route-params match)]))

(def history
  (pushy/pushy set-page! (partial bidi/match-route app-routes)))

(defn start-routing! []
  (pushy/start! history))

(defn navigate-to! [url]
  (pushy/set-token! history url))