(ns time-management-api.middleware
  (:require [buddy.auth]
            [ring.util.http-response :refer [unauthorized internal-server-error]]))

(defn wrap-auth-check [handler]
  (fn [request]
    (if-not (buddy.auth/authenticated? request)
      (unauthorized {:error "Authentication token is missing or invalid"})
      (handler request))))


(defn wrap-exception-handling [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e (internal-server-error {:error "An unexpected error occurred"
                                                 :exception (ex-message e)})))))