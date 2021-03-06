(defproject time-management-client "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.764"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library
                               org.clojure/google-closure-library-third-party]]
                 [thheller/shadow-cljs "2.9.3"]
                 [reagent "1.0.0-alpha2"]
                 [re-frame "1.0.0-rc2"]
                 [day8.re-frame/tracing "0.5.5"]
                 [day8.re-frame/http-fx "v0.2.0"]
                 [kibu/pushy "0.3.8"]
                 [bidi "2.1.6"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [hiccups "0.3.0"]]

  :plugins [[lein-shadow "0.2.0"]
            [lein-asset-minifier "0.4.6"]
            [lein-less "1.7.5"]
            [lein-shell "0.5.0"]]

  :min-lein-version "2.9.0"

  :source-paths ["src/clj" "src/cljs"]

  :test-paths   ["test/cljs"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"]


  :less {:source-paths ["less"]
         :target-path  "resources/public/css"}

  :minify-assets [[:css {:source "resources/public/css/site.css" :target "resources/public/css/site.min.css"}]]

  :shell {:commands {"open" {:windows ["cmd" "/c" "start"]
                             :macosx  "open"
                             :linux   "xdg-open"}}}

  :shadow-cljs {:nrepl {:port 8777}
                
                :builds {:app {:target :browser
                               :output-dir "resources/public/js/compiled"
                               :asset-path "/js/compiled"
                               :modules {:app {:init-fn time-management-client.core/init
                                               :preloads [devtools.preload
                                                          day8.re-frame-10x.preload]}}
                               :dev {:compiler-options {:closure-defines {re-frame.trace.trace-enabled? true
                                                                          day8.re-frame.tracing.trace-enabled? true
                                                                          time-management-client.config/api-url "http://localhost:8081"}}}
                               :release {:build-options
                                         {:ns-aliases
                                          {day8.re-frame.tracing day8.re-frame.tracing-stubs}}
                                         :compiler-options {:closure-defines {time-management-client.config/api-url "http://localhost:8081"}
                                                            :externs ["/externs/jwt-decode.ext.js"]
                                                            :infer-externs :auto}}

                               :devtools {:http-root "resources/public"
                                          :http-port 8280
                                          }}
                         :browser-test
                         {:target :browser-test
                          :ns-regexp "-test$"
                          :runner-ns shadow.test.browser
                          :test-dir "target/browser-test"
                          :devtools {:http-root "target/browser-test"
                                     :http-port 8290}}

                         :karma-test
                         {:target :karma
                          :ns-regexp "-test$"
                          :output-to "target/karma-test.js"}}}

  :aliases {"dev" ["with-profile" "dev,test" "do"
                   ["shadow" "watch" "app"]]
            "prod" ["with-profile" "prod" "do"
                    ["shadow" "release" "app"]]
            "build-report" ["with-profile" "prod" "do"
                            ["shadow" "run" "shadow.cljs.build-report" "app" "target/build-report.html"]
                            ["shell" "open" "target/build-report.html"]]
            "karma" ["with-profile" "test" "do"
                     ["shadow" "compile" "karma-test"]
                     ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]}

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "1.0.0"]
                   [day8.re-frame/re-frame-10x "0.6.5"]]
    :source-paths ["dev"]}

   :prod {}

   :test {:dependencies [[hiccup-find "1.0.0"]
                         [day8.re-frame/test "0.1.5"]]}
   }

  :prep-tasks [["less" "once"]
               ["minify-assets" "once"]])
