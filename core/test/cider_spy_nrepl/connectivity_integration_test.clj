(ns cider-spy-nrepl.connectivity-integration-test
  (:require [cider-spy-nrepl.test-utils
             :refer
             [alias-and-dev
              msg->summary
              register-user-on-hub-with-summary
              start-up-repl-server
              stop-repl-server
              take-from-chan!
              wrap-nuke-sessions
              wrap-setup-alias
              wrap-startup-hub]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl.transport :as transport]))

(use-fixtures :each wrap-nuke-sessions (wrap-setup-alias "foodude") wrap-startup-hub)

;; Path to stability
;; Step 1 stop using evals which create a non-deterministic race condition (location can arrive before registration, or after, make evals a separate test)
;; Step 2 actually fix the test
;; Step 3 rework register-user-on-hub-with-summary into a common fixture
;; Step 4 fix ALL tests
;; Step 5 Remove the legacy connectivity-test
;; Step 6 fix the session trampling bug
;; Step 7 a dedicated and nice looking eval/location test

(deftest test-user-registrations
  (let [port1 7774
        port2 7775
        server-1 (start-up-repl-server 7774)
        server-2 (start-up-repl-server 7775)]

    (try
      (let [[transport-for-1 session-id-1 _ summary-chan-1] (register-user-on-hub-with-summary 7774 "foodude")
            [transport-for-2 session-id-2 _ summary-chan-2 _ registered-devs] (register-user-on-hub-with-summary 7775 "foodude~2")]

        (is (= #{"foodude" "foodude~2"}
               (->> summary-chan-1 (take-from-chan! 1 5000) first msg->summary alias-and-dev first)))

        (transport/send transport-for-2 {:op "close" :id "close-session-id" :session session-id-2})

        (is (= [#{"foodude"} "foodude"]
               (->> summary-chan-1 (take-from-chan! 1 5000) first msg->summary alias-and-dev))))
      (finally
        (stop-repl-server server-1)
        (stop-repl-server server-2)))))
