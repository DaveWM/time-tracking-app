(ns time-management-client.config)

(def debug?
  ^boolean goog.DEBUG)

;; Set in project.clj under :closure-defines
(goog-define api-url "NOT SET")

(def auth-token-key "auth-token")
