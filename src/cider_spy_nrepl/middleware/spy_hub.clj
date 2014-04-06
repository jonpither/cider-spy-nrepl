(ns cider-spy-nrepl.middleware.spy-hub
  (:use [clojure.core.async :only [chan thread]])
  (:require [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.logging :as log]))

(defn- send-connected-msg! [{:keys [transport hub-host hub-port hub-alias] :as msg} s]
  (transport/send transport (response-for msg :value (str "CIDER-SPY-NREPL: " s))))

(defn- connect-to-hub! [{:keys [hub-client] :as session} session-atom
                        {:keys [hub-host hub-port hub-alias] :as msg}]
  (if-not hub-client
    (do
      (send-connected-msg! msg (format "Connecting to SPY HUB %s:%s with alias %s" hub-host hub-port hub-alias))
      (if-let [hub-client (hub-client/connect-to-hub! hub-host (Integer/parseInt hub-port) hub-alias session-atom)]
        (do
          (send-connected-msg! msg (format "You are connected to the CIDER SPY HUB."))
          (assoc session :hub-client hub-client))
        (do
          (send-connected-msg! msg (format "You are NOT connected to the CIDER SPY HUB."))
          session)))
    (do
      (send-connected-msg! msg "Already connected to SPY HUB")
      session)))

(defn- handle-connect-to-hub-request [msg]
  (future
    (let [session (sessions/session! msg)]
      (if session
        (swap! session connect-to-hub! session msg)
        (log/warn "Expected session for connection to hub.")))))

(defn handler
  "Middleware that looks up info for a symbol within the context of a particular namespace."
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= "cider-spy-hub-connect" op)
      (handle-connect-to-hub-request msg)
      (handler msg))))

(set-descriptor!
 #'handler
 {:handles
  {"spy-hub-connect"
   {:doc "Return a summary of hacking information about the nrepl session."
    :returns {"status" "done"}}}})
