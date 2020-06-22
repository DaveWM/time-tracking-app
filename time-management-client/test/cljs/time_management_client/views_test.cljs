(ns time-management-client.views-test
  (:require [clojure.test :refer-macros [deftest is]]
            [reagent.core :as r]
            [hiccup-find.core :as hf]
            [re-frame.db :refer [app-db]]
            [time-management-client.views :as sut]))


(deftest main-panel-test
  (let [result (sut/main-panel)
        logo (first (hf/hiccup-find [:.uk-logo] result))]
    (is (some? logo))))
