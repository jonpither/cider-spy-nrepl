(ns cider-spy-nrepl.middleware.cider-spy-hub
  (:require [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]))

(defn- register
  "Register the alias for the users session on the CIDER-SPY-HUB."
  [session]
  (cider/send-connected-msg! session (format "Setting alias on CIDER SPY HUB to %s." (:hub-alias @session)))
  (hub-client/register session))

(defn- on-connect [session hub-client]
  (if hub-client
    (do
      (sessions/update! session assoc :hub-client hub-client)
      (cider/send-connected-msg! session "You are connected to the CIDER SPY HUB.")
      (register session))
    (cider/send-connected-msg! session "You are NOT connected to the CIDER SPY HUB.")))

(defn- connect
  "Connect to the CIDER-SPY-HUB.
   Once connected, we attempt to register the user with an alias."
  [session hub-host hub-port]
  (cider/send-connected-msg! session (format "Connecting to SPY HUB %s:%s." hub-host hub-port))
  (on-connect session (hub-client/connect session hub-host hub-port)))

(defn- connect-to-hub! [session]
  (future
    (locking session
      (let [{:keys [hub-client hub-alias user-disconnect]} @session
            connected? (and hub-client (.isOpen (last hub-client)))
            closed? (and hub-client (not connected?))]
        (when (and (not connected?) (not user-disconnect))
          (if-let [[hub-host hub-port] (settings/hub-host-and-port)]
            (do
              (when closed?
                (cider/send-connected-msg! session "SPY HUB connection closed, reconnecting"))
              (connect session hub-host hub-port))
            (cider/send-connected-msg! session "No CIDER-SPY-HUB host and port specified.")))))))

(defn- handle-register-hub-buffer-msg
  "We register the buffer in EMACS used for displaying connection information
   about the CIDER-SPY-HUB."
  [{:keys [id hub-alias] :as msg} session]
  (sessions/update! session assoc :hub-connection-buffer-id (:id msg))
  (when hub-alias
    (sessions/update! session assoc :hub-alias hub-alias)))

(defn- handle-change-hub-alias
  "Change alias in CIDER-SPY-HUB."
  [{:keys [alias] :as msg} session]
  (sessions/update! session assoc :hub-alias alias)
  (register session))

(defn- handle-send-msg
  "Send a message to a developer registered on the CIDER-SPY-HUB."
  [{:keys [from recipient message] :as msg} session]
  (cider/send-connected-msg! session (format "Sending message from %s to recipient %s on CIDER SPY HUB." from recipient))
  (hub-client/send-msg session from recipient message))

(defn- handle-cider-spy-disconnect
  "Disconnect permantently the user from the CIDER-SPY-HUB."
  [session]
  (sessions/update! session assoc :user-disconnect true)
  (sessions/update! session dissoc :registrations)
  (.close (last (:hub-client @session)))
  (cider/send-connected-msg! session "Disconnected from the HUB")
  (cider/update-spy-buffer-summary! session))

(defn wrap-cider-spy-hub
  [handler]
  (fn [{:keys [op] :as msg}]
    (if-let [session (sessions/session! msg)]
      (case op
        "cider-spy-hub-connect" (handle-register-hub-buffer-msg msg session)
        "cider-spy-hub-alias" (handle-change-hub-alias msg session)
        "cider-spy-hub-send-msg" (handle-send-msg msg session)
        "cider-spy-hub-disconnect" (handle-cider-spy-disconnect session)

        (do
          (connect-to-hub! session)
          (handler msg)))
      (handler msg))))

(set-descriptor!
 #'wrap-cider-spy-hub
 {:handles
  {"cider-spy-hub-connect" {:doc "Connects to CIDER SPY HUB."}
   "cider-spy-hub-disconnect" {:doc "Disconnects from the CIDER SPY HUB."}
   "cider-spy-hub-alias" {:doc "Set CIDER SPY HUB alias."}
   "cider-spy-hub-send-msg" {:doc "Send a message via CIDER SPY HUB."}}})
