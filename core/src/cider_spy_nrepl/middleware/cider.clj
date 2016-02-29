(ns cider-spy-nrepl.middleware.cider
  (:require [cheshire.core :as json]
            [cider-spy-nrepl.middleware.summary-builder :as summary-builder]
            [cider-spy-nrepl.middleware.session-vars :refer [*summary-message-id* *hub-connection-buffer-id* *cider-spy-transport* *watch-session-request-id*]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as transport]))

(defn send! [session msg]
  {:pre [(:id msg)]}
  (when-let [cider-spy-transport (@session #'*cider-spy-transport*)]
    (transport/send cider-spy-transport
                    (response-for {:session (-> session meta :id) :id (:id msg)} msg))))

(defn update-spy-buffer-summary!
  "Send this string back to the users CIDER SPY buffer.
   EMACS CIDER SPY has a listener waiting for a message with an ID
   the same as SUMMARY-MESSAGE-ID in the session."
  [session]
  (send! session {:id (@session #'*summary-message-id*)
                  :value (json/encode (summary-builder/summary @session))
                  ;; Avoid manipulation from clojure.tools.nrepl.middleware.pr-values:
                  :printed-value "true"}))

(defn send-connected-on-hub-msg!
  [session alias]
  (send! session {:id (@session #'*hub-connection-buffer-id*) :hub-registered-alias alias}))

(defn send-connected-msg!
  "Send a message back to CIDER-SPY pertaining to CIDER-SPY-HUB connectivity.
   The correct ID is used as to ensure the message shows up in the relevant
   CIDER-SPY buffer."
  [session s]
  (send! session {:id (@session #'*hub-connection-buffer-id*) :value (str "CIDER-SPY-NREPL: " s) :printed-value "true"}))
