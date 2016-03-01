(ns cider-spy-nrepl.hub-integration-test
  (:require [clojure.tools.nrepl.server :as nrserver]
            [cider-spy-nrepl.middleware.cider-spy]
            [cider-spy-nrepl.middleware.alias]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.middleware.cider-spy-multi-repl]
            [clojure.tools.nrepl.transport :as transport]
            [cider-spy-nrepl.middleware.cider-spy-hub]
            [cider-spy-nrepl.middleware.hub-settings :as hub-settings]
            [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]))

(defn- start-up-repl-server []
  (let [server
        (nrserver/start-server
         :port 7777
         :handler (nrserver/default-handler
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
  (let [s (nrepl/response-seq transport 10000)]
    (transport/send transport msg)
    s))

(defn some-eval [session-id]
  {:session session-id :ns "clojure.string" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id "eval-msg"})

(deftest test-connect-to-hub
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        session-id (:new-session (first (nrepl/response-seq (transport/send transport {:op "clone" :id "session-create-id"}) 5000)))]

    (is (= "done" (->> {:session session-id :id "22" :op "cider-spy-hub-register-connection-buffer"}
                       (send-and-seq transport)
                       first
                       :status
                       first)))

    (let [interesting-messages (->> (some-eval session-id)
                                    (send-and-seq transport)
                                    (take 7)
                                    (remove #(= (:id %) "eval-msg")))]

      (is (= #{"CIDER-SPY-NREPL: Connecting to SPY HUB localhost:7778."
               "CIDER-SPY-NREPL: You are connected to the CIDER SPY HUB."
               "CIDER-SPY-NREPL: Setting alias on CIDER SPY HUB to foodude."
               "CIDER-SPY-NREPL: Registered on hub as: foodude"}
             (->> interesting-messages
                  (filter :value)
                  (map :value)
                  set)))

      (is (= "foodude" (->> interesting-messages (filter :hub-registered-alias) first :hub-registered-alias))))))

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

(deftest test-multi-repl-watch
  (let [[transport-for-1 session-id-1] (register-user-on-hub "foodude")
        [transport-for-2 session-id-2] (register-user-on-hub "foodude~2")]

    (let [msgs-for-1 (nrepl/response-seq transport-for-1 10000)]

      (is (= "CIDER-SPY-NREPL: Sent watching REPL request to target foodude"
             (->> {:session session-id-2 :op "cider-spy-hub-watch-repl" :target "foodude" :id "watching-msg-id"}
                  (send-and-seq transport-for-2)
                  first :value)))

      (is (= "CIDER-SPY-NREPL: Someone is watching your REPL!" (:value (first msgs-for-1)))))

    (println "1" session-id-1 "2" session-id-2)

    (let [msgs-for-2 (nrepl/response-seq transport-for-2 10000)]
      (testing "User 2 can watch user 1 evals"
        ;; Regular eval with 2 responses
        (assert (= 2 (count (->> (some-eval session-id-1)
                                 (send-and-seq transport-for-1)
                                 (take 2)))))

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
                 :op "multi-repl-out"}
                {:cs-sequence 2,
                 :id "watching-msg-id",
                 :session session-id-2
                 :status ["done"],
                 :target "foodude"
                 :op "multi-repl-out"}]
               (->> msgs-for-2 (take 3))))))

    (testing "User 2 can do an eval on user 1s repl"
      (is (= [{:id "hub-connection-buffer-id",
               :session session-id-2
               :value "CIDER-SPY-NREPL: Sent REPL eval to target foodude"}
              {:id "eval-msg",
               :ns "user",
               :op "multi-repl-out"
               :session session-id-2
               :target "foodude"
               :value "2"
               :cs-sequence 1
               :origin-session-id session-id-2}
              {:id "eval-msg",
               :session session-id-2
               :op "multi-repl-out"
               :status ["done"]
               :target "foodude"
               :cs-sequence 2
               :origin-session-id session-id-2}]
             (->> (assoc (some-eval session-id-2) :op "cider-spy-hub-multi-repl-eval" :target "foodude")
                  (send-and-seq transport-for-2)
                  (take 3))))

      ;; msg should appear for the other person REPL
      )))
