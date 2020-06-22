(ns time-management-client.routes-test
  (:require [clojure.test :refer-macros [deftest is]]
            [bidi.bidi :as bidi]
            [time-management-client.routes :as sut]))

(deftest app-routes-test
  (is (= {:handler :home} (bidi/match-route sut/app-routes "/")))
  (is (= {:handler :login} (bidi/match-route sut/app-routes "/login")))
  (is (= {:handler :register} (bidi/match-route sut/app-routes "/register")))
  (is (= {:handler :create-entry} (bidi/match-route sut/app-routes "/entries/new")))
  (is (= {:handler :edit-entry
          :route-params {:id 123}}
         (bidi/match-route sut/app-routes "/entries/123")))
  (is (= {:handler :settings} (bidi/match-route sut/app-routes "/settings")))
  (is (= {:handler :create-user} (bidi/match-route sut/app-routes "/users/new")))
  (is (= {:handler :edit-user
          :route-params {:user-id 123}}
         (bidi/match-route sut/app-routes "/users/123")))
  (is (= {:handler :create-user-entry
          :route-params {:user-id 123}}
         (bidi/match-route sut/app-routes "/users/123/entries/new")))
  (is (= {:handler :edit-user-entry
          :route-params {:user-id 123
                         :id 234}}
         (bidi/match-route sut/app-routes "/users/123/entries/234")))
  (is (= {:handler :users} (bidi/match-route sut/app-routes "/users")))

  (is (= {:handler :not-found} (bidi/match-route sut/app-routes "/nope")))
  (is (= {:handler :not-found} (bidi/match-route sut/app-routes "/users/nope")))
  (is (= {:handler :not-found} (bidi/match-route sut/app-routes "/login/nope"))))
