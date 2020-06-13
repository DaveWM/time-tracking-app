(ns time-management-api.auth
  (:require [clj-time.core :as time]
            [buddy.sign.jwt :as jwt]
            [buddy.auth.backends.token :refer [jws-backend]]
            [ring.util.http-response :refer [ok bad-request unauthorized]]
            [time-management-api.config :refer [config]]))

(def token-options
  {:alg :hs512})

(defn create-token [username]
  (let [claims {:user username
                :roles [:user]
                :exp (time/plus (time/now) (time/days 1))}]
    (jwt/sign claims (:auth-secret config) token-options)))

(def auth-backend
  (jws-backend {:secret (:auth-secret config)
                :options token-options
                :unauthorized-handler (constantly
                                        (unauthorized {:error "Authentication token is missing or invalid"}))}))