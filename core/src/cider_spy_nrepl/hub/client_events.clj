(ns cider-spy-nrepl.hub.client-events
  (:require [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.middleware.interruptible-eval :refer [interruptible-eval]]
            [cider-spy-nrepl.middleware.session-vars :refer [*hub-connection-details* *watching?* *registrations* *hub-connection-buffer-id*
                                                             *cider-spy-transport*]]
            [clojure.tools.nrepl.transport :as nrepl-transport]
            [cider-spy-nrepl.hub.client :refer [send-async!]]
            [clojure.tools.logging :as log]))

(defmulti process (fn [_ msg] (-> msg :op keyword)))

(defmethod process :default [_ msg]
  (println "Did not understand message from hub" msg))

(defmethod process :connected [session {:keys [alias when-connected]}]
  (log/debug (format "Registered on hub as: %s" alias))
  (swap! session assoc #'*hub-connection-details* {:alias alias :when-connected when-connected})
  (cider/send-connected-on-hub-msg! session alias)
  (cider/send-connected-msg! session (format "Registered on hub as: %s" alias))
;;  (cider/update-spy-buffer-summary! session)
  )

(defmethod process :registered [session {:keys [alias registered] :as s}]
  (log/debug (format "Registered: %s" alias))
  (swap! session assoc #'*registrations* registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :unregistered [session {:keys [alias registered]}]
  (log/debug (format "Unregistered: %s" alias))
  (swap! session assoc #'*registrations* registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :location [session {:keys [alias registered]}]
  (log/debug (format "Location change: %s" alias))
  (swap! session assoc #'*registrations* registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :message [s {:keys [message from recipient]}]
  (log/debug (format "Message received from %s to %s: %s" from recipient message))
  (cider/send-received-msg! s from recipient message))

(defmethod process :watch-repl [s _]
  (log/debug (format "Someone is watching!"))
  (swap! s assoc #'*watching?* true)
  (cider/send-connected-msg! s "Someone is watching your REPL!"))

(deftype MultiReplTransport [session originator]
  nrepl-transport/Transport
  (send [this {:keys [value] :as msg}]
    (send-async! session :multi-repl-eval-out {:originator originator :msg (dissoc msg :session)}))
  (recv [this])
  (recv [this timeout]))

(defmethod process :multi-repl-eval [session {:keys [msg originator]}]
  (log/debug "Multi-REPL received eval request" msg)

  (let [hub-connection-buffer-id (@session #'*hub-connection-buffer-id*)
        transport (MultiReplTransport. session originator)
        eval-handler (interruptible-eval nil)]
    (eval-handler {:op "eval"
                   :id (:id msg)
                   :code (:code msg)
                   :session session
                   :interrupt-id nil
                   :transport transport}))

  (cider/send-connected-msg! session "Multi-REPL received eval request!"))

(defmethod process :multi-repl-eval-out [session {:keys [msg]}]
  (log/debug "Multi-REPL received eval response" msg)
  (cider/send-back-to-cider! session (:id msg) msg))

(defmethod process :watch-repl-eval [s {:keys [code target]}]
  (log/debug (format "REPL eval received from %s: %s" target code))
  (cider/send-watch-repl-eval! s code target))

(defmethod process :watch-repl-out [s {:keys [msg target]}]
  (log/debug (format "REPL out received from %s" target))
  (cider/send-watch-repl-out! s msg target))
