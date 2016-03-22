(ns cider-spy-nrepl.test-utils
  (:require [cheshire.core :as json]
            [clojure.tools.nrepl.server :as nrserver]
            [cider-spy-nrepl.hub.client :as client]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.middleware.cider-spy :as spy-middleware]
            [cider-spy-nrepl.middleware.cider-spy-hub :as hub-middleware]
            [clojure.core.async :refer [alts!! chan go timeout >!]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl.transport :as transport])
  (:import (io.netty.channel ChannelHandlerContext))
  (:refer-clojure :exclude [sync]))

(defn assert-async-msgs
  "Take msg patterns, asserts all accounted for in chan"
  [c msgs]
  (let [received-msgs (for [_ msgs]
                        (first (alts!! [(timeout 2500) c])))]
    (doseq [m msgs]
      (is (some (partial re-find (re-pattern m))
                (remove nil? received-msgs))
          (str "Could not find msg: " m " got:\n" (clojure.string/join " " received-msgs))))))

(defmacro spy-harness [& body]
  `(do
     (reset! cider-spy-nrepl.hub.register/sessions {})
     ~@body))

(defprotocol CanBeOpen
  (isOpen [this])
  (writeAndFlush [this m])
  (sync [this])
  (close [this]))

(defn- stub-connect-to-hub [_ _ handler]
  (let [hub-session
        (atom {:channel
               (reify ChannelHandlerContext
                 (writeAndFlush [this msg]
                   (handler msg)
                   nil))})]
    [nil nil (reify CanBeOpen
               (isOpen [this] true)
               (writeAndFlush [this m]
                 (server-events/process nil hub-session m)
                 nil)
               (sync [this]
                 this)
               (close [this]
                 (server-events/unregister! hub-session)
                 this))]))

(defn raw-cider-msg
  "Utility to extract string message sent to CIDER."
  [cider-chan]
  (first (alts!! [(timeout 2000) cider-chan])))

(defn cider-msg
  "Utility to extract string message sent to CIDER."
  [cider-chan]
  (when-let [msg (:value (raw-cider-msg cider-chan))]
    (json/parse-string msg true)))

(defn foo [session]
  (let [cider-chan (chan)
        cider-transport (reify transport/Transport
                          (send [_ r]
                            (when (or (not= "connection-buffer-msg" (:id r))
                                      (:msg r))
                              (go (>! cider-chan r)))))]

    ((spy-middleware/wrap-cider-spy nil)
     {:id "summary-buffer-msg"
      :op "cider-spy-summary"
      :session session
      :transport cider-transport})

    ;; drain the first summary message
    (raw-cider-msg cider-chan)

    ;; Handle a middleware request to connect to CIDER SPY HUB
    ((hub-middleware/wrap-cider-spy-hub nil)
     {:id "connection-buffer-msg"
      :op "cider-spy-hub-register-connection-buffer"
      :session session
      :transport cider-transport})

    (binding [client/connect stub-connect-to-hub]
      ((hub-middleware/wrap-cider-spy-hub (constantly nil))
       {:op "random-op"
        :session session}))

    cider-chan))

(defn some-eval [session-id]
  {:session session-id :ns "clojure.string" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id "eval-msg"})

(defn msg->summary [msg]
  (when msg
    (-> msg
        :value
        (json/parse-string keyword))))

(defn msgs-by-id [id msgs]
  (filter #(= id (:id %)) msgs))

(defn alias-and-dev [summary-msg]
  ((juxt (comp set (partial map :alias) vals :devs) (comp :alias :hub-connection)) summary-msg))

(defn start-up-repl-server
  ([] (start-up-repl-server 7777))
  ([port]
   (let [server
         (nrserver/start-server
          :port port
          :handler (nrserver/default-handler
                    #'cider-spy-nrepl.middleware.cider-spy-session/wrap-cider-spy-session
                    #'cider-spy-nrepl.middleware.cider-spy-multi-repl/wrap-multi-repl
                    #'cider-spy-nrepl.middleware.cider-spy-hub/wrap-cider-spy-hub
                    #'cider-spy-nrepl.middleware.cider-spy/wrap-cider-spy))]
     server)))

(defn stop-repl-server [server]
  (try
    (nrserver/stop-server server)
    (catch Throwable t
      (println "Couldn't stop nrepl server"))))
