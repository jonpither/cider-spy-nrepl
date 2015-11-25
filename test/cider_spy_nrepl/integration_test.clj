(ns cider-spy-nrepl.integration-test
  (:require [clojure.tools.nrepl.server :as nrserver]
            [cider-spy-nrepl.middleware.cider-spy]
            [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]))

(defn- start-up-repl-server []
  (let [server
        (nrserver/start-server
         :port 7777
         :handler (nrserver/default-handler
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

(deftest test-display-a-summary
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        response (nrepl-message transport {:op "cider-spy-summary"})]
    (println "The response" response)))
