(ns cider-spy-nrepl.connectivity-integration-test
  (:require  [clojure.test :refer :all]
             [clojure.tools.nrepl :as nrepl]
             [clojure.tools.nrepl.transport :as transport]
             [cider-spy-nrepl.middleware.hub-settings :as hub-settings]
             [cider-spy-nrepl.hub.server :as hub-server]
             [cider-spy-nrepl.nrepl-test-utils :refer [messages-chan! take-from-chan!]]
             [cider-spy-nrepl.test-utils :refer [some-eval msgs-by-id alias-and-dev msg->summary
                                                 start-up-repl-server stop-repl-server]]))

(defn- wrap-setup-once [f]
  (with-redefs [hub-settings/hub-host-and-port (constantly ["localhost" 7778])
                cider-spy-nrepl.middleware.alias/alias-from-env (constantly "foodude")]
    (let [server (start-up-repl-server)
          hub-server (hub-server/start 7778)]
      (f)
      (stop-repl-server server)
      (hub-server/shutdown hub-server))))

(use-fixtures :each wrap-setup-once)

(defn- register-user-on-hub-with-summary [port expected-alias]
  (let [transport (nrepl/connect :port port :host "localhost")
        msgs-chan (messages-chan! transport)]

    ;; Create the session:
    (transport/send transport {:op "clone" :id "session-create-id"})

    (let [session-id (->> msgs-chan (take-from-chan! 1 1000) first :new-session)]
      (assert session-id)

      ;; Register the summary message
      (transport/send transport {:session session-id :id "session-msg-ig" :op "cider-spy-summary"})
      (assert (->> msgs-chan (take-from-chan! 1 1000) first msg->summary :session :started))

      ;; Register connection buffer for status messages
      (transport/send transport {:session session-id :id "hub-connection-buffer-id" :op "cider-spy-hub-register-connection-buffer"})
      (assert (->> msgs-chan (take-from-chan! 1 1000)))

      ;; Do an eval to prompt connection to the hub:
      (transport/send transport (some-eval session-id))
      (let [msgs (->> msgs-chan (take-from-chan! 10 2000))]
        (assert (= 10 (count msgs)) (count msgs))
        (assert (= 2 (count (msgs-by-id "eval-msg" msgs))))
        (assert (= 4 (count (->> msgs (msgs-by-id "hub-connection-buffer-id") (filter :value)))))
        ;; 1 after the eval, 1 after location comes back, 1 after registration
        (assert (= 3 (count (->> msgs (msgs-by-id "session-msg-ig")))))
        (assert (= expected-alias (->> msgs (filter :hub-registered-alias) first :hub-registered-alias)))
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
            [transport-for-2 msgs-chan-for-2 session-id-2 registered-devs] (register-user-on-hub-with-summary 7775 "foodude")]

        (is (= #{"foodude" "foodude~2"}))

        (stop-repl-server server-2)

        (is (= [#{"foodude"} "foodude"]
               (->> msgs-chan-for-1 (take-from-chan! 1 2000) first msg->summary alias-and-dev))))
      (finally
        (stop-repl-server server-1)
        (stop-repl-server server-2)))))
