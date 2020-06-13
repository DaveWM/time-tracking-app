(ns time-management-api.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(def config
  (aero/read-config (io/resource "config.edn")))
