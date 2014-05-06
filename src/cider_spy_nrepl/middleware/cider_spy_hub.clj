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

(defn- connect-to-hub! [session msg hub-alias]
  (if (and (:hub-client @session) (.isOpen (last (:hub-client @session))))
    (send-connected-msg! msg "Already connected to SPY HUB")
    (if-let [[hub-host hub-port] (settings/hub-host-and-port)]
      (do
        (send-connected-msg! msg (format "Connecting to SPY HUB %s:%s with alias %s" hub-host hub-port hub-alias))
        (if-let [hub-client (:hub-client (swap! session hub-client/connect-to-hub! session hub-host hub-port hub-alias))]
          (send-connected-msg! msg "You are connected to the CIDER SPY HUB.")
          (send-connected-msg! msg "You are NOT connected to the CIDER SPY HUB.")))
      (send-connected-msg! msg "No CIDER-SPY-HUB host and port specified."))))

(defn- reconnect-if-necessary! [msg]
  (let [session (sessions/session! msg)]
    (when (and session (:hub-message-id @session)
               ;; hub client not already connected
               (not (and (:hub-client @session) (.isOpen (last (:hub-client @session))))))
      (let [msg (assoc msg :id (:hub-message-id @session))
            {:keys [alias]} (:hub-setup @session)]
        (send-connected-msg! msg "SPY HUB connection closed, reconnecting")
        (connect-to-hub! session msg alias)))))

(defn- handle-connect-to-hub-request [{:keys [hub-alias] :as msg}]
  (future
    (if-let [session (sessions/session! msg)]
      (do
        (connect-to-hub! session msg hub-alias)
        (swap! session assoc :hub-message-id (:id msg)))
      (log/warn "Expected session for connection to hub."))))

(defn wrap-cider-spy-hub
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= "cider-spy-hub-connect" op)
      (handle-connect-to-hub-request msg)
      (do
        (reconnect-if-necessary! msg)
        (handler msg)))))

(set-descriptor!
 #'wrap-cider-spy-hub
 {:handles
  {"spy-hub-connect"
   {:doc "Connects to CIDER SPY HUB."}}})
