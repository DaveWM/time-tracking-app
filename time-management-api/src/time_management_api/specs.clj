(ns time-management-api.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [phrase.alpha :refer [defphraser]]
            [clj-time.coerce :as tc]))

(def email-regex
  "Regex that matches all valid emails, copied from https://emailregex.com"
  #"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])")
(defn email? [s] (re-matches email-regex s))
(s/def :user/email (s/and string? email?))
(defphraser email?
  [_ {:keys [val]}]
  (str val " is not a valid email address."))


;; not sure what the actual password rules should be - assume at least 5 chars, and at least 1 number
(s/def :user/password (s/and string? #(>= (count %) 5) #(<= 1 (count (re-seq #"\d" %)))))

(defphraser #(>= (count %) min-length)
  {:via [:user/password]}
  [_ _ min-length]
  (str "Password must contain at least " min-length " character(s)."))

(defphraser #(<= num-numbers (count (re-seq #"\d" %)))
  {:via [:user/password]}
  [_ _ num-numbers]
  (str "Password must contain at least " num-numbers " number(s)."))

(s/def :user/role (->> [:role/user :role/manager :role/admin]
                       (map #(str (namespace %) "/" (name %)))
                       (set)))
(s/def :user/roles (s/coll-of :user/role :min-count 1))


(s/def :entry/description string?)

(s/def :entry/start (s/and string? tc/from-string))
(defphraser tc/from-string
  [_ {:keys [val]}]
  (str val " is not a valid date."))

(s/def :entry/duration pos-int?)
(defphraser pos-int?
  {:via [:entry/duration]}
  [_ {:keys [val]}]
  (str val " is not a valid duration. It should be an integer above 0."))


(s/def :request/create-user
  (s/keys :req-un [:user/email
                   :user/password]
          :opt-un [:user/roles]))
(s/def :request/login :request/create-user)

(s/def :request/create-time-sheet-entry
  (s/keys :req-un [:entry/description
                   :entry/start
                   :entry/duration]))
(s/def :request/update-time-sheet-entry :request/create-time-sheet-entry)


(s/def :settings/preferred-working-hours pos-int?)
(defphraser pos-int? [_ {:keys [val]}]
  {:via [:settings/preferred-working-hours]}
  (str val " is not valid for 'preferred working hours', it must be an integer above 0"))


(s/def :request/update-settings
  (s/keys :req-un [:settings/preferred-working-hours]))

(s/def :request/update-user
  (s/keys :req-un [:user/email
                   :user/roles]
          :opt-un [:user/password]))


(defphraser #(contains? % key)
  [_ _ key]
  (str "Missing field: " key))

(defphraser :default
  [_ problem]
  (str (or (:val problem) "nil") " is not a valid value for the " (name (first (:in problem))) " field."))

