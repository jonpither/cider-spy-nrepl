(ns cider-spy-nrepl.middleware.cider-spy-hub
  (:use [clojure.core.async :only [chan thread]])
  (:require [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
            [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.logging :as log]))

(defn- register
  "Register the alias for the users session on the CIDER-SPY-HUB."
  [session msg]
  (cider/send-connected-msg! session (format "Setting alias on CIDER SPY HUB to %s." (:hub-alias @session)))
  (hub-client/register session))

(defn- connect
  "Connect to the CIDER-SPY-HUB.
   Once connected, we attempt to register the user with an alias."
  [session msg hub-host hub-port]
<<<<<<< HEAD
  (send-connected-msg! msg (format "Connecting to SPY HUB %s:%s." hub-host hub-port))
  (if (:hub-client (sessions/update! session assoc :hub-client (hub-client/connect session hub-host hub-port)))
=======
  (cider/send-connected-msg! session (format "Connecting to SPY HUB %s:%s." hub-host hub-port))
  (if (:hub-client (swap! session assoc :hub-client (hub-client/connect session hub-host hub-port)))
>>>>>>> WIP for message sending
    (do
      (cider/send-connected-msg! session "You are connected to the CIDER SPY HUB.")
      (register session msg))
    (cider/send-connected-msg! session "You are NOT connected to the CIDER SPY HUB.")))

(defn- connect-to-hub! [msg]
  (when-let [session (sessions/session! msg)]
    (let [{:keys [hub-client hub-alias]} @session
          connected? (and hub-client (.isOpen (last hub-client)))
          closed? (and hub-client (not connected?))]
      (when (not connected?)
        (if-let [[hub-host hub-port] (settings/hub-host-and-port)]
          (do
            (when closed?
              (cider/send-connected-msg! session "SPY HUB connection closed, reconnecting"))
            (connect session msg hub-host hub-port))
          (cider/send-connected-msg! session "No CIDER-SPY-HUB host and port specified."))))))

(defn- handle-register-hub-buffer-msg
  "We register the buffer in EMACS used for displaying connection information
   about the CIDER-SPY-HUB."
  [{:keys [id hub-alias] :as msg}]
  (when-let [session (sessions/session! msg)]
    (sessions/update! session assoc :hub-connection-buffer-id (:id msg))
    (when hub-alias
      (sessions/update! session assoc :hub-alias hub-alias))))

(defn- handle-change-hub-alias
  "Change alias in CIDER-SPY-HUB."
  [{:keys [alias] :as msg}]
  (when-let [session (sessions/session! msg)]
    (sessions/update! session assoc :hub-alias alias)
    (register session msg)))

(defn- handle-send-msg
  "Send a message to a developer registered on the CIDER-SPY-HUB."
  [{:keys [recipient message] :as msg}]
  (when-let [session (sessions/session! msg)]
    (cider/send-connected-msg! session "Sending message to recipient on CIDER SPY HUB.")
    (hub-client/send-msg session recipient message)))

(defn wrap-cider-spy-hub
  [handler]
  (fn [{:keys [op] :as msg}]
    (case op
      "cider-spy-hub-connect" (handle-register-hub-buffer-msg msg)
      "cider-spy-hub-alias" (handle-change-hub-alias msg)
      "cider-spy-hub-send-msg" (handle-send-msg msg)

      (do
        (connect-to-hub! msg)
        (handler msg)))))

(set-descriptor!
 #'wrap-cider-spy-hub
 {:handles
  {"spy-hub-connect"
   {:doc "Connects to CIDER SPY HUB."}}})
