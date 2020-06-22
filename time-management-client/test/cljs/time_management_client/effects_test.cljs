(ns time-management-client.effects-test
  (:require [clojure.test :refer-macros [deftest testing is]]
            [re-frame.registrar :refer [get-handler]]
            ["file-saver" :as file-saver]
            [time-management-client.config :as config]
            [time-management-client.routes :as routes]
            [time-management-client.effects :as sut]))

(deftest set-token-test
  (let [effect-handler (get-handler :fx ::sut/set-token)]
    (effect-handler "new token")
    (is (= "new token" (js/localStorage.getItem config/auth-token-key)))))

(deftest navigate-to-test
  (let [effect-handler (get-handler :fx ::sut/navigate-to)
        called-with (atom nil)]
    (with-redefs [routes/navigate-to! #(reset! called-with %)]
      (effect-handler "url")
      (is (= "url" @called-with)))))

(deftest save-file-test
  (let [effect-handler (get-handler :fx ::sut/save-file)
        called-with-blob (atom nil)
        called-with-name (atom nil)]
    (with-redefs [file-saver/saveAs #(do
                                       (reset! called-with-blob %1)
                                       (reset! called-with-name %2))]
                 (effect-handler {:content "content"
                                  :type "html"
                                  :name "file.html"})
      (is (= "file.html" @called-with-name)))))
