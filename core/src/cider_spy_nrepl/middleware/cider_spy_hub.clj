(ns cider-spy-nrepl.middleware.cider-spy-hub
  (:require [cider-spy-nrepl.hub.client :as hub-client]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
            [cider-spy-nrepl.middleware.alias :as alias]
            [clojure.tools.nrepl.middleware.session]
            [clojure.tools.nrepl.middleware.interruptible-eval]
            [cider-spy-nrepl.middleware.session-vars :refer [*hub-connection-details* *hub-connection-buffer-id* *hub-client* *registrations* *user-disconnect* *desired-alias*]]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]))

(defn- register
  "Register the alias for the users session on the CIDER-SPY-HUB."
  [session]
  (let [alias (or (@session #'*desired-alias*) (alias/alias-from-env))]
    (cider/send-connected-msg! session (format "Setting alias on CIDER SPY HUB to %s." alias))
    (hub-client/send-async! session :register {:alias alias :session-id (-> session meta :id)})))

(defn- on-connect
  "Hub client could be nil in the event of a failed connection."
  [session hub-client]
  (if hub-client
    (do
      (swap! session assoc #'*hub-client* hub-client)
      (cider/send-connected-msg! session "You are connected to the CIDER SPY HUB.")
      (register session))
    (cider/send-connected-msg! session "You are NOT connected to the CIDER SPY HUB.")))

(defn- connect
  "Connect to the CIDER-SPY-HUB.
   Once connected, we attempt to register the user with an alias."
  [session hub-host hub-port]
  (cider/send-connected-msg! session (format "Connecting to SPY HUB %s:%s." hub-host hub-port))
  (let [handler (partial client-events/process session)]
    (on-connect session (hub-client/safe-connect hub-host hub-port handler))))

(defn- connect-to-hub! [session]
  (future
    (locking session
      (let [user-disconnect (@session #'*user-disconnect*)
            hub-client (@session #'*hub-client*)
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
  (swap! session assoc #'*hub-connection-buffer-id* id)
  ;; Resend out the alias
  (when-let [{:keys [alias]} (@session #'*hub-connection-details*)]
    (cider/send-connected-on-hub-msg! session alias)))

(defn- handle-change-hub-alias
  "Change alias in CIDER-SPY-HUB."
  [{:keys [alias]} session]
  (swap! session assoc #'*desired-alias* alias)
  (register session))

(defn- handle-send-msg
  "Send a message to a developer registered on the CIDER-SPY-HUB."
  [{:keys [recipient message]} session]
  (cider/send-connected-msg!
   session
   (format "Sending message to recipient %s on CIDER SPY HUB." recipient))
  (hub-client/send-async! session :message {:message message
                                            :recipient recipient}))

(defn- handle-cider-spy-disconnect
  "Disconnect permantently the user from the CIDER-SPY-HUB."
  [_ session]
  (swap! session assoc #'*user-disconnect* true)
  (swap! session dissoc #'*registrations*)
  (-> (@session #'*hub-client*) last .close)
  (cider/send-connected-msg! session "Disconnected from the HUB")
  (cider/update-spy-buffer-summary! session))

(def cider-spy-hub--nrepl-ops {"cider-spy-hub-register-connection-buffer" #'handle-register-hub-connection-buffer-msg
                               "cider-spy-hub-alias" #'handle-change-hub-alias
                               "cider-spy-hub-send-msg" #'handle-send-msg
                               "cider-spy-hub-disconnect" #'handle-cider-spy-disconnect})

(defn wrap-cider-spy-hub
  [handler]
  (fn [{:keys [op session] :as msg}]
    (if session
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
