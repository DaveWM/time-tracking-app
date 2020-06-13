(ns time-management-api.utils
  (:require [clojure.spec.alpha :as s]
            [ring.util.http-response :refer [bad-request]]))


(defn update-when [m k f & args]
  (if (some? (get m k))
    (apply update m k f args)
    m))


(defmacro with-spec [req-body spec & body]
  `(if (s/valid? ~spec ~req-body)
     (do ~@body)
     (bad-request  {:error (phrase.alpha/phrase-first {} ~spec ~req-body)})))