(ns time-management-client.utils
  (:require [cljs-time.coerce :as tc]))


(defn days-since-epoch [date]
  (quot (tc/to-epoch date) (* 60 60 24)))

(defn from-days-since-epoch [days-since-epoch]
  (tc/from-long (* days-since-epoch 1000 60 60 24)))

(defn kw-string [keyword]
  (str (namespace keyword) "/" (name keyword)))

(defn assoc-when [m k v]
  (if (some? v)
    (assoc m k v)
    m))