(ns cider-spy-nrepl.middleware.session-vars)

(def ^{:dynamic true :doc "A map of hub connection details"} *hub-connection-details*)

(def ^{:dynamic true :doc "The message ID used for sending asynchronous summary updates back to the client"} *summary-message-id*)

(def ^{:dynamic true :doc "The message ID used for sending asynchronous hub connection updates back to the client"} *hub-connection-buffer-id*)

(def ^{:dynamic true :doc "The Netty client to the Hub"} *hub-client*)

(def ^{:dynamic true :doc "Is this REPL session being watched?"} *watching?*)

(def ^{:dynamic true :doc "Cider Spy Hub Registrations"} *registrations*)

(def ^{:dynamic true :doc "Transport for sending messages to CIDER-SPY"} *cider-spy-transport*)

(def ^{:dynamic true :doc "When Cider-Spy nREPL session was started"} *session-started*)

(def ^{:dynamic true :doc "CIDER-spy tracking"} *tracking*)

(def ^{:dynamic true :doc "Has the user requested disconect"} *user-disconnect*)

(def ^{:dynamic true :doc "Desired alias on the Hub"} *desired-alias*)

(def ^{:dynamic true :doc "The message ID used for sending asynchronous multi REPL updates back to the client"} *watch-session-request-id*)
