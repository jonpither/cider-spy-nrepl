(ns cider-spy-nrepl.integration-test
  (:require [clojure.tools.nrepl.server :as nrserver]
            [cider-spy-nrepl.middleware.cider-spy]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.middleware.cider-spy-multi-repl]
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
  (let [server (start-up-repl-server)]
    (f)
    (stop-repl-server server)))

(use-fixtures :once wrap-setup-once)

(defn- nrepl-message
  ([timeout tr payload]
   (nrepl/message (nrepl/client tr timeout) payload))
  ([tr payload]
   (nrepl-message 5000 tr payload)))

(deftest test-stc-eval
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        response (nrepl-message transport {:ns "user" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id 14})]
    (is (= ["done"] (:status (second response))))))

(deftest test-display-a-summary
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        response (nrepl-message transport {:op "cider-spy-summary"})]
    (is response)))
