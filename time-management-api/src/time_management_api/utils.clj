(ns time-management-api.utils)


(defn update-when [m k f & args]
  (if (some? (get m k))
    (apply update m k f args)
    m))