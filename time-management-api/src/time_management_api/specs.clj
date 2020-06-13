(ns time-management-api.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [phrase.alpha :refer [defphraser]]))

(def email-regex
  "Regex that matches all valid emails, copied from https://emailregex.com"
  #"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])")
(defn email? [s] (re-matches email-regex s))
(s/def ::email (s/and string? email?))
(defphraser email?
  [_ {:keys [val]}]
  (str val " is not a valid email address."))


;; not sure what the actual password rules should be - assume at least 5 chars, and at least 1 number
(s/def ::password (s/and string? #(>= (count %) 5) #(<= 1 (count (re-seq #"\d" %)))))

(defphraser #(>= (count %) min-length)
  {:via [::password]}
  [_ _ min-length]
  (str "Password must contain at least " min-length " character(s)."))

(defphraser #(<= num-numbers (count (re-seq #"\d" %)))
  {:via [::password]}
  [_ _ num-numbers]
  (str "Password must contain at least " num-numbers " number(s)."))


(s/def :request/create-user
  (s/keys :req-un [::email
                   ::password]))
(s/def :request/login :request/create-user)


(defphraser #(contains? % key)
  [_ _ key]
  (str "Missing field: " key))

(defphraser :default
  [_ problem]
  (str (or (:val problem) "Nil") " is not a valid value for the " (first (:in problem)) " field."))

