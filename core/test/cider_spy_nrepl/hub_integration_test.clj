(ns cider-spy-nrepl.hub-integration-test
  (:require [clojure.tools.nrepl.server :as nrserver]
            [cider-spy-nrepl.middleware.cider-spy]
            [cider-spy-nrepl.middleware.alias]
            [cheshire.core :as json]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.middleware.cider-spy-session]
            [cider-spy-nrepl.middleware.cider-spy-multi-repl]
            [clojure.tools.nrepl.transport :as transport]
            [cider-spy-nrepl.middleware.cider-spy-hub]
            [cider-spy-nrepl.middleware.hub-settings :as hub-settings]
            [clojure.test :refer :all]
            [cider-spy-nrepl.nrepl-test-utils :refer [messages-chan! take-from-chan!]]
            [clojure.tools.nrepl :as nrepl]))

(defn- start-up-repl-server []
  (let [server
        (nrserver/start-server
         :port 7777
         :handler (nrserver/default-handler
                   #'cider-spy-nrepl.middleware.cider-spy-session/wrap-cider-spy-session
                   #'cider-spy-nrepl.middleware.cider-spy-multi-repl/wrap-multi-repl
                   #'cider-spy-nrepl.middleware.cider-spy-hub/wrap-cider-spy-hub
                   #'cider-spy-nrepl.middleware.cider-spy/wrap-cider-spy))]
    server))

(defn- stop-repl-server [server]
  (nrserver/stop-server server))

(defn- wrap-setup-once [f]
  (with-redefs [hub-settings/hub-host-and-port (constantly ["localhost" 7778])
                cider-spy-nrepl.middleware.alias/alias-from-env (constantly "foodude")]
    (let [server (start-up-repl-server)
          hub-server (hub-server/start 7778)]
      (f)
      (stop-repl-server server)
      (hub-server/shutdown hub-server))))

(use-fixtures :each wrap-setup-once)

(defn- send-and-seq [transport msg]
  (let [s (nrepl/response-seq transport 3000)]
    (transport/send transport msg)
    s))

(defn- some-eval [session-id]
  {:session session-id :ns "clojure.string" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id "eval-msg"})

(defn- msg->summary [msg]
  {:pre [msg (:value msg)]}
  (-> msg
      :value
      (json/parse-string keyword)))

(defn- msgs-by-id [id msgs]
  (filter #(= id (:id %)) msgs))

(defn- alias-and-dev [summary-msg]
  ((juxt (comp set (partial map :alias) vals :devs) (comp :alias :hub-connection)) summary-msg))

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

    (testing "Eval provokes connection to hub"
      (transport/send transport (some-eval session-id))

      (let [msgs (->> msgs-chan
                      (take-from-chan! 7 1000)
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
        session-id (:new-session (first (nrepl/response-seq (transport/send transport {:op "clone" :id "session-create-id"}))))]

    (assert session-id)
    (assert (->> {:session session-id :id "hub-connection-buffer-id" :op "cider-spy-hub-register-connection-buffer"}
                 (send-and-seq transport)
                 first))

    (assert (->> (some-eval session-id)
                 (send-and-seq transport)
                 (filter #(= (:value %) (str "CIDER-SPY-NREPL: Registered on hub as: " expected-alias)))
                 first))
    [transport session-id]))

(deftest test-send-messages
  (let [[transport-for-1 session-id-1] (register-user-on-hub "foodude")
        [transport-for-2 session-id-2] (register-user-on-hub "foodude~2")]

    (let [msgs-for-1 (nrepl/response-seq transport-for-1 1000)
          msgs-for-2 (nrepl/response-seq transport-for-2 1000)]

      (is (= "CIDER-SPY-NREPL: Sending message to recipient foodude~2 on CIDER SPY HUB."
             (->> {:op "cider-spy-hub-send-msg"
                   :recipient "foodude~2"
                   :from "foodude"
                   :message "Hows it going?"
                   :session session-id-1}
                  (send-and-seq transport-for-1)
                  first :value)))

      (is (= {:from "foodude",
              :id "hub-connection-buffer-id",
              :msg "Hows it going?",
              :recipient "foodude~2",
              :session session-id-2}
             (first msgs-for-2)))

      (is (= "CIDER-SPY-NREPL: Sending message to recipient foodude on CIDER SPY HUB."
             (->> {:op "cider-spy-hub-send-msg"
                   :recipient "foodude"
                   :from "foodude~2"
                   :message "Not bad dude."
                   :session session-id-2}
                  (send-and-seq transport-for-2)
                  first :value)))

      (is (= {:from "foodude~2",
              :id "hub-connection-buffer-id",
              :msg "Not bad dude.",
              :recipient "foodude",
              :session session-id-1}
             (first msgs-for-1))))))

(deftest test-multi-repl-watch
  (let [[transport-for-1 session-id-1] (register-user-on-hub "foodude")
        [transport-for-2 session-id-2] (register-user-on-hub "foodude~2")]

    (let [msgs-for-1 (nrepl/response-seq transport-for-1 10000)]

      (is (= "CIDER-SPY-NREPL: Sent watching REPL request to target foodude"
             (->> {:session session-id-2 :op "cider-spy-hub-watch-repl" :target "foodude" :id "watching-msg-id"}
                  (send-and-seq transport-for-2)
                  first :value)))

      (is (= "CIDER-SPY-NREPL: Someone is watching your REPL!" (:value (first msgs-for-1)))))

    (let [msgs-for-2 (nrepl/response-seq transport-for-2 10000)]

      ;; Regular eval with 2 responses, shown in full here for reference purposes
      (is (= [{:id "eval-msg",
               :ns "clojure.string",
               :session session-id-1
               :value "2"}
              {:id "eval-msg",
               :session session-id-1
               :status ["done"]}]
             (->> (some-eval session-id-1)
                  (send-and-seq transport-for-1)
                  (take 2))))

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
               (->> msgs-for-2 (take 3))))))

    (testing "User 2 can do an eval on user 1s repl"
      (let [msgs-for-1 (nrepl/response-seq transport-for-1 10000)]

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
                 (->> (assoc (some-eval session-id-2) :op "cider-spy-hub-multi-repl-eval" :target "foodude")
                      (send-and-seq transport-for-2)
                      (take 3)))))

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
                 (->> msgs-for-1
                      (take 4)))))))))

(deftest test-print-ln-bug
  (let [[transport-for-1 session-id-1] (register-user-on-hub "foodude")
        [transport-for-2 session-id-2] (register-user-on-hub "foodude~2")]

    (let [msgs-for-1 (nrepl/response-seq transport-for-1 10000)]

      (assert (->> {:session session-id-2 :op "cider-spy-hub-watch-repl" :target "foodude" :id "watching-msg-id"}
                   (send-and-seq transport-for-2)
                   first))

      (assert (= "CIDER-SPY-NREPL: Someone is watching your REPL!" (:value (first msgs-for-1)))))

    (let [msgs-for-2 (nrepl/response-seq transport-for-2 10000)]

      ;; Regular eval with 2 responses, shown in full here for reference purposes
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
             (->> (assoc (some-eval session-id-1) :code "(println \"sd\")")
                  (send-and-seq transport-for-1)
                  (take 3))))

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
               (->> msgs-for-2 (take 4))))))))
