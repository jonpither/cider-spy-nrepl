(ns cider-spy-nrepl.middleware.cider
  (:require [cheshire.core :as json]
            [cider-spy-nrepl.middleware.summary-builder :as summary-builder]
            [cider-spy-nrepl.middleware.session-vars :refer [*summary-message-id* *hub-connection-buffer-id* *cider-spy-transport* *watch-session-request-id*]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as transport]))

(defn update-session-for-summary-msg!
  "Update the session with SUMMARY-MESSAGE-ID."
  [session {:keys [id]}]
  (swap! session assoc #'*summary-message-id* id))

;; Todo maybe make a macro to save unnecessary work if cider-spy is not place
(defn send-back-to-cider! [session message-id & opts]
  (let [cider-spy-transport (@session #'*cider-spy-transport*)]
    (when (and cider-spy-transport message-id)
      (transport/send cider-spy-transport
                      (apply response-for
                             {:session (-> session meta :id) :id message-id}
                             opts)))))

(defn update-spy-buffer-summary!
  "Send this string back to the users CIDER SPY buffer.
   EMACS CIDER SPY has a listener waiting for a message with an ID
   the same as SUMMARY-MESSAGE-ID in the session."
  [session]
  (send-back-to-cider! session
                       (@session #'*summary-message-id*)
                       :value (json/encode (summary-builder/summary @session))
                       ;; Avoid manipulation from clojure.tools.nrepl.middleware.pr-values:
                       :printed-value "true"))

(defn send-connected-on-hub-msg!
  [session alias]
  (send-back-to-cider! session (@session #'*hub-connection-buffer-id*) :hub-registered-alias alias))

(defn send-connected-msg!
  "Send a message back to CIDER-SPY pertaining to CIDER-SPY-HUB connectivity.
   The correct ID is used as to ensure the message shows up in the relevant
   CIDER-SPY buffer."
  [session s]
  (send-back-to-cider! session (@session #'*hub-connection-buffer-id*) :value (str "CIDER-SPY-NREPL: " s)))

(defn send-received-msg!
  "Send a message back to CIDER-SPY informing that a msg has been received
   from another developer on the HUB."
  [session from recipient s]
  (send-back-to-cider! session (@session #'*hub-connection-buffer-id*) :from from :recipient recipient :msg s))

(defn send-watch-repl-eval!
  "Send a message back to CIDER-SPY informing that a eval has been requested
   on a REPL that is being watched."
  [session code target]
  (send-back-to-cider! session (@session #'*hub-connection-buffer-id*) :target target :watch-repl-eval-code code))

(defn send-watch-repl-out!
  "Send a message back to CIDER-SPY informing that a eval has been performed
   on a REPL that is being watched."
  [session msg target]
  (apply send-back-to-cider! session (@session #'*watch-session-request-id*) :target target (reduce into [] msg)))
