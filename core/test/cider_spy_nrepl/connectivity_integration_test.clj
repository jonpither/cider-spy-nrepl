(ns cider-spy-nrepl.connectivity-integration-test
  (:require [cider-spy-nrepl
             [nrepl-test-utils :refer [messages-chan! take-from-chan!]]
             [test-utils :refer [alias-and-dev msg->summary msgs-by-id some-eval start-up-repl-server stop-repl-server]]]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.middleware.hub-settings :as hub-settings]
            [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.transport :as transport]))

(defn- wrap-setup-once [f]
  (with-redefs [hub-settings/hub-host-and-port (constantly ["localhost" 7778])
                cider-spy-nrepl.middleware.alias/alias-from-env (constantly "foodude")]
    (let [server (start-up-repl-server)
          hub-server (hub-server/start 7778)]
      (f)
      (stop-repl-server server)
      (hub-server/shutdown hub-server))))

(use-fixtures :each wrap-setup-once)

;; Step 1 stop using evals which create a non-deterministic race condition (location can arrive before registration, or after, make evals a separate test)
;; Step 2 actually fix the test

(defn- register-user-on-hub-with-summary [port expected-alias]
  (let [transport (nrepl/connect :port port :host "localhost")
        msgs-chan (messages-chan! transport)]

    ;; Create the session:
    (transport/send transport {:op "clone" :id "session-create-id"})

    (let [session-id (->> msgs-chan (take-from-chan! 1 1000) first :new-session)]
      (assert session-id)

      ;; Register connection buffer for status messages
      (transport/send transport {:session session-id :id "hub-connection-buffer-id" :op "cider-spy-hub-register-connection-buffer"})
      (assert (->> msgs-chan (take-from-chan! 1 1000)))

      ;; Register the summary message
      (transport/send transport {:session session-id :id "session-msg-ig" :op "cider-spy-summary"})
      (assert (->> msgs-chan (take-from-chan! 1 1000) first msg->summary :session :started))

      ;; Connect to the hub:
      (transport/send transport {:session session-id :id "connect-msg-id" :op "cider-spy-hub-connect"})

      ;; Ensure connected:

      (let [msgs (->> msgs-chan (take-from-chan! 6 10000))]
        (is (= 6 (count msgs)) (count msgs))
        (is (= 4 (count (->> msgs (msgs-by-id "hub-connection-buffer-id") (filter :value)))))
        ;; 1 summary message after registration happens
        (is (= 1 (count (->> msgs (msgs-by-id "session-msg-ig")))) (count (->> msgs (msgs-by-id "session-msg-ig"))))
        (is (= expected-alias (->> msgs (filter :hub-registered-alias) first :hub-registered-alias)))
        (let [registered-devs (->> msgs
                                   (msgs-by-id "session-msg-ig")
                                   (map msg->summary)
                                   (map :devs)
                                   (mapcat vals)
                                   (map :alias)
                                   (set))]
          (assert (registered-devs expected-alias))
          [transport msgs-chan session-id registered-devs]))


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

(deftest user-registrations
  (let [port1 7774
        port2 7775
        server-1 (start-up-repl-server 7774)
        server-2 (start-up-repl-server 7775)]

    (try
      (let [[transport-for-1 msgs-chan-for-1 session-id-1] (register-user-on-hub-with-summary 7774 "foodude")
            [transport-for-2 msgs-chan-for-2 session-id-2 registered-devs] (register-user-on-hub-with-summary 7775 "foodude~2")]

        (is (= #{"foodude" "foodude~2"}
               (->> msgs-chan-for-1 (take-from-chan! 1 5000) first msg->summary alias-and-dev first)))

        (stop-repl-server server-2)

        (is (= [#{"foodude"} "foodude"]
               (->> msgs-chan-for-1 (take-from-chan! 1 5000) first msg->summary alias-and-dev))))
      (finally
        (stop-repl-server server-1)
        (stop-repl-server server-2)))))
