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
  [{:keys [transport] :as msg} s]
  (transport/send transport (response-for msg :value (str "CIDER-SPY-NREPL: " s))))

(defn- connect-to-hub! [msg]
  (let [session (sessions/session! msg)
        {:keys [hub-client hub-alias]} @session
        connected? (and hub-client (.isOpen (last hub-client)))
        closed? (and hub-client (not connected?))]
    (when (not connected?)
      (let [msg (assoc msg :id (:hub-connection-buffer-id @session))]
        (if-let [[hub-host hub-port] (settings/hub-host-and-port)]
          (do
            (when closed?
              (send-connected-msg! msg "SPY HUB connection closed, reconnecting"))
            (send-connected-msg! msg (format "Connecting to SPY HUB %s:%s with alias %s" hub-host hub-port :hub-alias))
            (if (:hub-client (swap! session hub-client/connect-to-hub! session hub-host hub-port))
              (send-connected-msg! msg "You are connected to the CIDER SPY HUB.")
              (send-connected-msg! msg "You are NOT connected to the CIDER SPY HUB.")))
          (send-connected-msg! msg "No CIDER-SPY-HUB host and port specified."))))))

(defn- handle-register-hub-buffer-msg
  "We register the buffer in EMACS used for displaying connection information
   about the CIDER-SPY-HUB."
  [msg]
  (when-let [session (sessions/session! msg)]
    (swap! session assoc :hub-connection-buffer-id (:id msg))))

(defn wrap-cider-spy-hub
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= "cider-spy-hub-connect" op)
      (handle-register-hub-buffer-msg msg)
      (do
        (connect-to-hub! msg)
        (handler msg)))))

(set-descriptor!
 #'wrap-cider-spy-hub
 {:handles
  {"spy-hub-connect"
   {:doc "Connects to CIDER SPY HUB."}}})
