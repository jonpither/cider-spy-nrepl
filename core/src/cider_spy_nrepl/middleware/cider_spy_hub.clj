(ns cider-spy-nrepl.middleware.cider-spy-hub
  (:require [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.alias :as alias]
            [clojure.tools.nrepl.middleware.session]
            [clojure.tools.nrepl.middleware.interruptible-eval]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]))

(defn- register
  "Register the alias for the users session on the CIDER-SPY-HUB."
  [session]
  (let [alias (or (:desired-alias @session) (alias/alias-from-env))]
    (cider/send-connected-msg! session (format "Setting alias on CIDER SPY HUB to %s." alias))
    (hub-client/register session alias)))

(defn- on-connect [session hub-client]
  (if hub-client
    (do
      (swap! session assoc :hub-client hub-client)
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
      (let [{:keys [hub-client user-disconnect]} @session
            connected? (and hub-client (.isOpen (last hub-client)))
            closed? (and hub-client (not connected?))]
        (when (and (not connected?) (not user-disconnect))
          (if-let [[hub-host hub-port] (settings/hub-host-and-port)]
            (do
              (when closed?
                (cider/send-connected-msg! session "SPY HUB connection closed, reconnecting"))
              (connect session hub-host hub-port))
            (cider/send-connected-msg! session "No CIDER-SPY-HUB host and port specified.")))))))

(defn- handle-register-hub-connection-buffer-msg
  "We register the buffer in EMACS used for displaying connection information
   about the CIDER-SPY-HUB."
  [{:keys [id]} session]
  (swap! session assoc :hub-connection-buffer-id id))

(defn- handle-change-hub-alias
  "Change alias in CIDER-SPY-HUB."
  [{:keys [alias]} session]
  (swap! session assoc :desired-alias alias)
  (register session))

(defn- handle-send-msg
  "Send a message to a developer registered on the CIDER-SPY-HUB."
  [{:keys [recipient message]} session]
  (cider/send-connected-msg!
   session
   (format "Sending message to recipient %s on CIDER SPY HUB." recipient))
  (hub-client/send-msg session recipient message))

(defn- handle-cider-spy-disconnect
  "Disconnect permantently the user from the CIDER-SPY-HUB."
  [_ session]
  (swap! session assoc :user-disconnect true)
  (swap! session dissoc :registrations)
  (-> (:hub-client @session) last .close)
  (cider/send-connected-msg! session "Disconnected from the HUB")
  (cider/update-spy-buffer-summary! session))

(def cider-spy-hub--nrepl-ops {"cider-spy-hub-register-connection-buffer" #'handle-register-hub-connection-buffer-msg
                               "cider-spy-hub-alias" #'handle-change-hub-alias
                               "cider-spy-hub-send-msg" #'handle-send-msg
                               "cider-spy-hub-disconnect" #'handle-cider-spy-disconnect})

(defn wrap-cider-spy-hub
  [handler]
  (fn [{:keys [op] :as msg}]
    (if-let [session (sessions/session! msg)]
      (if-let [cider-spy-handler (get cider-spy-hub--nrepl-ops op)]
        (cider-spy-handler msg session)
        (do
          (connect-to-hub! session)
          (handler msg)))
      (handler msg))))

(set-descriptor!
 #'wrap-cider-spy-hub
 {:requires #{#'clojure.tools.nrepl.middleware.session/session}
  :expects  #{#'clojure.tools.nrepl.middleware.interruptible-eval/interruptible-eval}
  :handles (zipmap (keys cider-spy-hub--nrepl-ops)
                   (repeat {:doc "See the cider-spy-hub README"
                            :returns {} :requires {}}))})
