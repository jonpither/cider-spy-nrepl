(ns cider-spy-nrepl.middleware.cider-spy-hub
  (:use [clojure.core.async :only [chan thread]])
  (:require [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.logging :as log]))

(defn- send-connected-msg!
  "Send a message back to CIDER-SPY pertaining to CIDER-SPY-HUB connectivity.
   The correct ID is used as to ensure the message shows up in the relevant
   CIDER-SPY buffer."
  [{:keys [transport] :as msg} s]
  (let [{:keys [hub-connection-buffer-id]} @(sessions/session! msg)
        msg (assoc msg :id hub-connection-buffer-id)]
    (transport/send transport (response-for msg :value (str "CIDER-SPY-NREPL: " s)))))

(defn- register
  "Register the alias for the users session on the CIDER-SPY-HUB."
  [session msg]
  (send-connected-msg! msg (format "Setting alias on CIDER SPY HUB to %s." (:hub-alias @session)))
  (hub-client/register session))

(defn- connect
  "Connect to the CIDER-SPY-HUB.
   Once connected, we attempt to register the user with an alias."
  [session msg hub-host hub-port]
  (send-connected-msg! msg (format "Connecting to SPY HUB %s:%s." hub-host hub-port))
  (if (:hub-client (swap! session assoc :hub-client (hub-client/connect session hub-host hub-port)))
    (do
      (send-connected-msg! msg "You are connected to the CIDER SPY HUB.")
      (register session msg))
    (send-connected-msg! msg "You are NOT connected to the CIDER SPY HUB.")))

(defn- connect-to-hub! [msg]
  (when-let [session (sessions/session! msg)]
    (let [{:keys [hub-client hub-alias]} @session
          connected? (and hub-client (.isOpen (last hub-client)))
          closed? (and hub-client (not connected?))]
      (when (not connected?)
        (if-let [[hub-host hub-port] (settings/hub-host-and-port)]
          (do
            (when closed?
              (send-connected-msg! msg "SPY HUB connection closed, reconnecting"))
            (connect session msg hub-host hub-port))
          (send-connected-msg! msg "No CIDER-SPY-HUB host and port specified."))))))

(defn- handle-register-hub-buffer-msg
  "We register the buffer in EMACS used for displaying connection information
   about the CIDER-SPY-HUB."
  [{:keys [id hub-alias] :as msg}]
  (when-let [session (sessions/session! msg)]
    (swap! session assoc :hub-connection-buffer-id (:id msg))
    (when hub-alias
      (swap! session assoc :hub-alias hub-alias))))

(defn- handle-change-hub-alias
  "Change alias in CIDER-SPY-HUB."
  [{:keys [alias] :as msg}]
  (when-let [session (sessions/session! msg)]
    (swap! session assoc :hub-alias alias)
    (register session msg)))

(defn wrap-cider-spy-hub
  [handler]
  (fn [{:keys [op] :as msg}]
    (case op
      "cider-spy-hub-connect" (handle-register-hub-buffer-msg msg)
      "cider-spy-hub-alias" (handle-change-hub-alias msg)
      (do
        (connect-to-hub! msg)
        (handler msg)))))

(set-descriptor!
 #'wrap-cider-spy-hub
 {:handles
  {"spy-hub-connect"
   {:doc "Connects to CIDER SPY HUB."}}})
