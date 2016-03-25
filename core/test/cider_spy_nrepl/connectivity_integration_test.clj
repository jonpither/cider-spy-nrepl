(ns cider-spy-nrepl.connectivity-integration-test
  (:require [cider-spy-nrepl
             [test-utils :refer [messages-chan! take-from-chan! alias-and-dev msg->summary msgs-by-id start-up-repl-server stop-repl-server wrap-setup-alias wrap-startup-hub wrap-nuke-sessions]]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [clojure.core.async :refer [>! alts!! buffer chan go go-loop timeout <!]]
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

(defn- register-user-on-hub-with-summary [port expected-alias]
  (let [transport (nrepl/connect :port port :host "localhost")
        all-msgs-chan (messages-chan! transport)
        hub-chan (chan 100)
        summary-chan (chan 100)
        other-chan (chan 100)]

    (go-loop []
      (when-let [{:keys [id] :as m} (<! all-msgs-chan)]
        (>! (case id
              "session-msg-ig" summary-chan
              "hub-connection-buffer-id" hub-chan
              other-chan) m)
        (recur)))

    ;; Create the session:
    (transport/send transport {:op "clone" :id "session-create-id"})

    (let [session-id (->> other-chan (take-from-chan! 1 1000) first :new-session)]
      (assert session-id)

      ;; Register connection buffer for status messages
      (transport/send transport {:session session-id :id "hub-connection-buffer-id" :op "cider-spy-hub-register-connection-buffer"})
      (assert (->> hub-chan (take-from-chan! 1 1000)))

      ;; Register the summary message
      (transport/send transport {:session session-id :id "session-msg-ig" :op "cider-spy-summary"})
      (assert (->> summary-chan (take-from-chan! 1 1000) first msg->summary :session :started))

      ;; Connect to the hub:
      (transport/send transport {:session session-id :id "connect-msg-id" :op "cider-spy-hub-connect"})

      ;; Ensure connected:
      (let [msgs (->> hub-chan (take-from-chan! 5 10000))]
        (is (= 5 (count msgs)))
        (is (= 4 (count (->> msgs (filter :value)))))
        (is (= expected-alias (->> msgs (filter :hub-registered-alias) first :hub-registered-alias))))

      (let [[msg] (->> summary-chan (take-from-chan! 1 10000))]
        ;; 1 summary message after registration happens
        (is (= msg))
        (let [registered-devs (->> msg msg->summary :devs vals (map :alias) set)]
          (assert (registered-devs expected-alias))
          [transport session-id hub-chan summary-chan other-chan registered-devs]))


      ;; the eval: the user may not be registered when location bit is done, hence competition
      ;; can we not do the eval? Send through an innocous describe (but pull this into a separate test)

      ;; TODO MAKE A SPECIFIC EVAL TEST, KEEP THE SETUP SIMPLE
      ;; Do an eval to prompt connection to the hub:
      #_(transport/send transport (some-eval session-id))
      #_(let [msgs (->> msgs-chan (take-from-chan! 10 10000))]
        (is (= 10 (count msgs)) (count msgs))
        (is (= 2 (count (msgs-by-id "eval-msg" msgs))))
        (is (= 4 (count (->> msgs (msgs-by-id "hub-connection-buffer-id") (filter :value)))))
        ;; 1 after the eval, 1 after location comes back, 1 after registration
        (is (= 3 (count (->> msgs (msgs-by-id "session-msg-ig")))) (count (->> msgs (msgs-by-id "session-msg-ig"))))
        (is (= expected-alias (->> msgs (filter :hub-registered-alias) first :hub-registered-alias)))
        (let [registered-devs (->> msgs
                                   (msgs-by-id "session-msg-ig")
                                   (map msg->summary)
                                   (map :devs)
                                   (mapcat vals)
                                   (map :alias)
                                   (set))]
          (assert (registered-devs expected-alias))
          [transport msgs-chan session-id registered-devs])))))

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
