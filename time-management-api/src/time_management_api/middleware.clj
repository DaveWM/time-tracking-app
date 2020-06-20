(ns time-management-api.middleware
  (:require [buddy.auth]
            [ring.util.http-response :refer [unauthorized internal-server-error forbidden]]))

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

(defn wrap-validate-role [handler necessary-roles]
  (fn [request]
    (let [roles (->> (get-in request [:identity :roles])
                     (map keyword)
                     (set))]
      (if (clojure.set/subset? (set necessary-roles) roles)
        (handler request)
        (forbidden {:error "You do not have permission for this resource."})))))