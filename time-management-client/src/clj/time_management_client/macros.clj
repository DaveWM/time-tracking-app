(ns time-management-client.macros
  (:require [re-frame.core]))

(defmacro def-event-fx
  ([id fn] `(def-event-fx ~id [] ~fn))
  ([id injections fn]
   `(do
      (def ~(symbol (name id)) ~fn)
      (re-frame.core/reg-event-fx
       ~id
       ~injections
       ~fn))))

(defmacro def-event-db [id fn]
  `(do
     (def ~(symbol (name id)) ~fn)
     (re-frame.core/reg-event-db ~id ~fn)))