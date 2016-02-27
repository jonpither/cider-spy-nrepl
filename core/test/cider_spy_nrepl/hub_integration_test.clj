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

(use-fixtures :once wrap-setup-once)

(defn- send-and-seq [transport msg]
  (-> (transport/send transport msg)
      (nrepl/response-seq 10000)))

(deftest test-connect-to-hub
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        session-id (:new-session (first (nrepl/response-seq (transport/send transport {:op "clone" :id "session-create-id"}) 5000)))]

    (is (= "done" (->> {:session session-id :id "22" :op "cider-spy-hub-register-connection-buffer"}
                       (send-and-seq transport)
                       first
                       :status
                       first)))

    (let [interesting-messages (->> {:session session-id :ns "clojure.string" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id "eval-msg"}
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

;; TODO FIGURE OUT A TEST FOR MULTI_REPL
