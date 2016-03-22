(ns cider-spy-nrepl.hub-integration-test
  (:require [cider-spy-nrepl
             [nrepl-test-utils :refer [messages-chan! take-from-chan!]]
             [test-utils :refer [alias-and-dev msg->summary msgs-by-id some-eval wrap-setup-alias wrap-startup-hub wrap-startup-nrepl-server]]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.transport :as transport]))

(use-fixtures :each (wrap-setup-alias "foodude") wrap-startup-hub wrap-startup-nrepl-server)

(deftest test-connect-to-hub-and-change-alias
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        session-id (:new-session (first (nrepl/response-seq (transport/send transport {:op "clone" :id "session-create-id"}) 5000)))
        msgs-chan (messages-chan! transport)]

    (transport/send transport {:session session-id :id "22" :op "cider-spy-hub-register-connection-buffer"})

    (is (= "done" (->> msgs-chan
                       (take-from-chan! 1 1000)
                       first
                       :status
                       first)))

    (testing "Connection to the hub"

      ;; Connect to the hub:
      (transport/send transport {:session session-id :id "connect-msg-id" :op "cider-spy-hub-connect"})

      (let [msgs (->> msgs-chan
                      (take-from-chan! 5 1000)
                      (remove #(= (:id %) "eval-msg")))]

        (is (= #{"CIDER-SPY-NREPL: Connecting to SPY HUB localhost:7778."
                 "CIDER-SPY-NREPL: You are connected to the CIDER SPY HUB."
                 "CIDER-SPY-NREPL: Setting alias on CIDER SPY HUB to foodude."
                 "CIDER-SPY-NREPL: Registered on hub as: foodude"}
               (->> msgs
                    (filter :value)
                    (map :value)
                    set)))

        (is (= "foodude" (->> msgs (filter :hub-registered-alias) first :hub-registered-alias)))))

    (testing "Summary returns connection information"
      (transport/send transport {:session session-id :id "session-msg-ig" :op "cider-spy-summary"})

      (is (= [#{"foodude"} "foodude"]
             (->> msgs-chan (take-from-chan! 1 1000) first msg->summary alias-and-dev))))

    (testing "Change the alias"
      (transport/send transport {:op "cider-spy-hub-alias"
                                 :alias "Jon2"
                                 :session session-id})

      (let [msgs (->> msgs-chan
                      (take-from-chan! 4 1000))]

        (is (= [{:id "22",
                 :printed-value "true",
                 :session session-id,
                 :value "CIDER-SPY-NREPL: Setting alias on CIDER SPY HUB to Jon2."}
                {:hub-registered-alias "Jon2",
                 :id "22",
                 :session session-id}
                {:id "22",
                 :printed-value "true",
                 :session session-id,
                 :value "CIDER-SPY-NREPL: Registered on hub as: Jon2"}
                (->> msgs (msgs-by-id "22"))]))

        (is (= [#{"Jon2"} "Jon2"]
               (->> msgs (msgs-by-id "session-msg-ig") first msg->summary alias-and-dev)))))))

(defn- register-user-on-hub [expected-alias]
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        msgs-chan (messages-chan! transport)]

    ;; Create the session:
    (transport/send transport {:op "clone" :id "session-create-id"})

    (let [session-id (->> msgs-chan (take-from-chan! 1 1000) first :new-session)]
      (assert session-id)

      ;; Register connection buffer for status messages
      (transport/send transport {:session session-id :id "hub-connection-buffer-id" :op "cider-spy-hub-register-connection-buffer"})
      (assert (->> msgs-chan (take-from-chan! 1 1000)))

      ;; Connect to the hub:
      (transport/send transport {:session session-id :id "connect-msg-id" :op "cider-spy-hub-connect"})

      (let [msgs (->> msgs-chan (take-from-chan! 5 1000))]
        (assert (= 5 (count msgs)) (count msgs))
        (assert (= 4 (count (->> msgs (msgs-by-id "hub-connection-buffer-id") (filter :value)))))
        (assert (= expected-alias (->> msgs (filter :hub-registered-alias) first :hub-registered-alias))))
      [transport msgs-chan session-id])))

(deftest test-send-messages
  (let [[transport-for-1 msgs-chan-for-1 session-id-1] (register-user-on-hub "foodude")
        [transport-for-2 msgs-chan-for-2 session-id-2] (register-user-on-hub "foodude~2")]

    (transport/send transport-for-1 {:op "cider-spy-hub-send-msg"
                                     :recipient "foodude~2"
                                     :from "foodude"
                                     :message "Hows it going?"
                                     :session session-id-1})

    (is (= "CIDER-SPY-NREPL: Sending message to recipient foodude~2 on CIDER SPY HUB."
           (->> msgs-chan-for-1 (take-from-chan! 1 1000) first :value)))

    (is (= {:from "foodude",
            :id "hub-connection-buffer-id",
            :msg "Hows it going?",
            :recipient "foodude~2",
            :session session-id-2}
           (->> msgs-chan-for-2 (take-from-chan! 1 1000) first)))

    (transport/send transport-for-2 {:op "cider-spy-hub-send-msg"
                                     :recipient "foodude"
                                     :from "foodude~2"
                                     :message "Not bad dude."
                                     :session session-id-2})

    (is (= "CIDER-SPY-NREPL: Sending message to recipient foodude on CIDER SPY HUB."
           (->> msgs-chan-for-2 (take-from-chan! 1 1000) first :value)))

    (is (= {:from "foodude~2",
            :id "hub-connection-buffer-id",
            :msg "Not bad dude.",
            :recipient "foodude",
            :session session-id-1}
           (->> msgs-chan-for-1 (take-from-chan! 1 1000) first)))))

(deftest test-multi-repl-watch
  (let [[transport-for-1 msgs-chan-for-1 session-id-1] (register-user-on-hub "foodude")
        [transport-for-2 msgs-chan-for-2 session-id-2] (register-user-on-hub "foodude~2")]

    (transport/send transport-for-2 {:session session-id-2 :op "cider-spy-hub-watch-repl" :target "foodude" :id "watching-msg-id"})

    (is (= "CIDER-SPY-NREPL: Sent watching REPL request to target foodude"
           (->> msgs-chan-for-2 (take-from-chan! 1 1000) first :value)))

    (is (= "CIDER-SPY-NREPL: Someone is watching your REPL!"
           (->> msgs-chan-for-1 (take-from-chan! 1 1000) first :value)))

    ;; Regular eval with 2 responses, shown in full here for reference purposes
    (transport/send transport-for-1 (some-eval session-id-1))
    (is (= [{:id "eval-msg",
             :ns "clojure.string",
             :session session-id-1
             :value "2"}
            {:id "eval-msg",
             :session session-id-1
             :status ["done"]}]
           (->> msgs-chan-for-1 (take-from-chan! 2 1000))))

    (testing "User 2 can watch user 1 evals"
      (is (= [{:id "hub-connection-buffer-id",
               :session session-id-2
               :target "foodude",
               :watch-repl-eval-code "( + 1 1)"}
              {:cs-sequence 1,
               :id "watching-msg-id",
               :ns "clojure.string",
               :session session-id-2
               :target "foodude",
               :value "2"
               :originator "foodude",
               :op "multi-repl-out"}
              {:cs-sequence 2,
               :id "watching-msg-id",
               :session session-id-2
               :status ["done"],
               :originator "foodude",
               :target "foodude"
               :op "multi-repl-out"}]
             (->> msgs-chan-for-2 (take-from-chan! 3 1000)))))

    (testing "User 2 can do an eval on user 1s repl"
      (transport/send transport-for-2 (assoc (some-eval session-id-2) :op "cider-spy-hub-multi-repl-eval" :target "foodude"))

      (testing "User 2 can see the eval triggered by User 2"
        (is (= [{:id "hub-connection-buffer-id",
                 :session session-id-2
                 :value "CIDER-SPY-NREPL: Sent REPL eval to target foodude"}
                {:id "eval-msg",
                 :ns "user",
                 :op "multi-repl-out"
                 :session session-id-2
                 :target "foodude"
                 :value "2"
                 :printed-value "true",
                 :cs-sequence 1
                 :origin-session-id session-id-2
                 :originator "foodude"}
                {:id "eval-msg",
                 :session session-id-2
                 :op "multi-repl-out"
                 :status ["done"]
                 :target "foodude"
                 :cs-sequence 2
                 :origin-session-id session-id-2
                 :originator "foodude"}]
               (->> msgs-chan-for-2 (take-from-chan! 3 1000)))))

      (testing "User 1 can see the eval triggered by User 2"
        (is (= [{:ns "clojure.string",
                 :file "*cider-repl blog*",
                 :op "multi-repl->repl-eval",
                 :column 6,
                 :origin-session-id session-id-2
                 :line 12,
                 :out "( + 1 1)",
                 :id "hub-connection-buffer-id",
                 :code "( + 1 1)",
                 :target "foodude",
                 :outside-multi-repl-eval "true",
                 :originator "foodude~2",
                 :session session-id-1}
                {:id "hub-connection-buffer-id",
                 :session session-id-1
                 :value "CIDER-SPY-NREPL: Multi-REPL received eval request!"}
                {:id "hub-connection-buffer-id",
                 :ns "user",
                 :originator "foodude~2",
                 :outside-multi-repl-eval "true",
                 :session session-id-1
                 :value "2"}
                {:id "hub-connection-buffer-id",
                 :originator "foodude~2",
                 :outside-multi-repl-eval "true",
                 :session session-id-1
                 :status ["done"]}]
               (->> msgs-chan-for-1 (take-from-chan! 4 1000))))))))

(deftest test-print-ln-bug
  (let [[transport-for-1 msgs-chan-for-1 session-id-1] (register-user-on-hub "foodude")
        [transport-for-2 msgs-chan-for-2 session-id-2] (register-user-on-hub "foodude~2")]

    (transport/send transport-for-2 {:session session-id-2 :op "cider-spy-hub-watch-repl" :target "foodude" :id "watching-msg-id"})

    (assert (= "CIDER-SPY-NREPL: Someone is watching your REPL!"
               (->> msgs-chan-for-1 (take-from-chan! 1 1000) first :value)))
    (assert (= "CIDER-SPY-NREPL: Sent watching REPL request to target foodude"
               (->> msgs-chan-for-2 (take-from-chan! 1 1000) first :value)))

    ;; Regular eval with 2 responses, shown in full here for reference purposes
    (transport/send transport-for-1 (assoc (some-eval session-id-1) :code "(println \"sd\")"))
    (is (= [{:id "eval-msg",
             :session session-id-1
             :out "sd\n"}
            {:id "eval-msg",
             :ns "clojure.string",
             :session session-id-1
             :value "nil"}
            {:id "eval-msg",
             :session session-id-1
             :status ["done"]}]
           (->> msgs-chan-for-1 (take-from-chan! 3 1000))))

    (testing "User 2 can watch user 1 evals"
      (is (= [{:id "hub-connection-buffer-id",
               :session session-id-2
               :target "foodude",
               :watch-repl-eval-code "(println \"sd\")"}
              {:cs-sequence 1,
               :id "watching-msg-id",
               :session session-id-2
               :target "foodude",
               :out "sd\n"
               :op "multi-repl-out"
               :originator "foodude"}
              {:cs-sequence 2,
               :id "watching-msg-id",
               :ns "clojure.string",
               :session session-id-2
               :target "foodude",
               :value "nil"
               :op "multi-repl-out"
               :originator "foodude"}
              {:cs-sequence 3,
               :id "watching-msg-id",
               :session session-id-2
               :status ["done"],
               :target "foodude"
               :op "multi-repl-out"
               :originator "foodude"}]
             (->> msgs-chan-for-2 (take-from-chan! 4 1000)))))))
