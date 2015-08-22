(ns cider-spy-nrepl.middleware.cider
  (:require [cheshire.core :as json]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.summary-builder :as summary-builder]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as transport]))

(defn update-session-for-summary-msg!
  "Update the session with SUMMARY-MESSAGE-ID."
  [session {:keys [id]}]
  (sessions/update! session assoc :summary-message-id id))

;; Todo maybe make a macro to save unnecessary work if cider-spy is not place
(defn- send-back-to-cider! [transport session-id message-id & opts]
  (when message-id
    (transport/send transport
                    (apply response-for {:session session-id :id message-id} opts))))

(defn update-spy-buffer-summary!
  "Send this string back to the users CIDER SPY buffer.
   EMACS CIDER SPY has a listener waiting for a message with an ID
   the same as SUMMARY-MESSAGE-ID in the session."
  [session]
  (let [summary (summary-builder/summary @session)
        {:keys [id summary-message-id transport]} @session]
    (send-back-to-cider! transport id summary-message-id :value (json/encode summary))))

(defn send-connected-on-hub-msg!
  [session alias]
  (let [{:keys [id hub-connection-buffer-id transport]} @session]
    (send-back-to-cider! transport id hub-connection-buffer-id :hub-registered-alias alias)))

(defn send-connected-msg!
  "Send a message back to CIDER-SPY pertaining to CIDER-SPY-HUB connectivity.
   The correct ID is used as to ensure the message shows up in the relevant
   CIDER-SPY buffer."
  [session s]
  (let [{:keys [id hub-connection-buffer-id transport]} @session]
    (send-back-to-cider! transport id hub-connection-buffer-id :value (str "CIDER-SPY-NREPL: " s))))

(defn send-received-msg!
  "Send a message back to CIDER-SPY informing that a msg has been received
   from another developer on the HUB."
  [session from recipient s]
  (let [{:keys [id hub-connection-buffer-id transport]} @session]
    (send-back-to-cider! transport id hub-connection-buffer-id :from from :recipient recipient :msg s)))
