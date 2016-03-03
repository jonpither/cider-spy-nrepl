(ns cider-spy-nrepl.hub.client-events
  (:require [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.middleware.interruptible-eval :refer [interruptible-eval]]
            [cider-spy-nrepl.middleware.cider-spy-multi-repl :refer [wrap-multi-repl]]
            [cider-spy-nrepl.middleware.session-vars :refer :all]
            [clojure.tools.nrepl.transport :as nrepl-transport]
            [clojure.tools.nrepl.server :refer [default-handler unknown-op]]
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

(defmethod process :message [session {:keys [message from recipient]}]
  "Send a message back to CIDER-SPY informing that a msg has been received
   from another developer on the HUB."
  (log/debug (format "Message received from %s to %s: %s" from recipient message))
  (cider/send! session {:id (@session #'*hub-connection-buffer-id*)
                        :from from
                        :recipient recipient
                        :msg message}))

(defmethod process :watch-repl [s _]
  (log/debug (format "Someone is watching!"))
  (swap! s assoc #'*watching?* true)
  (cider/send-connected-msg! s "Someone is watching your REPL!"))

(defmethod process :multi-repl->repl-eval [session {:keys [originator origin-session-id] :as msg}]
  "An eval has been initiated in the multi-REPL, we must propagate this to the foundation REPL."
  (log/debug "Multi-REPL received eval request" msg)
  (when-let [connection-buffer-id (@session #'*hub-connection-buffer-id*)]
    (let [handler ((comp #'clojure.tools.nrepl.middleware.session/session #'wrap-multi-repl #'interruptible-eval) unknown-op)]
      (cider/send! session (merge msg {:id connection-buffer-id :outside-multi-repl-eval "true" :out (:code msg)}))
      (handler {:op "eval"
                :id (:id msg)
                :code (:code msg)
                :session (-> session meta :id)
                :interrupt-id nil
                :transport (@session #'*cider-spy-transport*)
                :originator originator
                :origin-session-id origin-session-id}))
    (cider/send-connected-msg! session "Multi-REPL received eval request!")))

(defmethod process :repl->mult-repl-eval [session {:keys [code target]}]
  "An eval has been initiated in the foundation REPL, we must propagate this to the multi-REPL."
  (log/debug (format "REPL eval received from %s: %s" target code))
  (cider/send! session {:id (@session #'*hub-connection-buffer-id*) :target target :watch-repl-eval-code code}))

(defn send-out-unsent-messages-if-in-order! [session id target f]
  (let [stored-messages (get-in @session [#'*watched-messages* target id])
        large-sequence-no (apply max (map :cs-sequence (vals stored-messages)))]
    (if (= large-sequence-no (count (vals stored-messages)))
      ;; send all the ones that are pending, in order:
      (doseq [{:keys [cs-sequence sent?] :as msg} (sort-by :cs-sequence (vals stored-messages)) :when (not sent?)]
        (log/error "Sending back" msg)
        (f (-> msg (assoc :target target)))
        (swap! session assoc-in [#'*watched-messages* target id cs-sequence :sent?] true))
      (log/warn "Holding on to message" id large-sequence-no stored-messages))))

(defmethod process :multi-repl-out [session {:keys [origin-session-id id target originator] :as msg}]
  "Send a message back to CIDER-SPY informing that a eval has been performed
   on a REPL that is being watched."
  (log/error "REPL out received from" target msg (@session #'*watch-session-request-id*))

  (let [id-to-use (if (= origin-session-id (-> session meta :id)) id (@session #'*watch-session-request-id*))]
    (swap! session assoc-in [#'*watched-messages* target id (:cs-sequence msg)] (merge msg
                                                                                       {:id id-to-use}
                                                                                       (when (:value msg)
                                                                                         {:printed-value "true"}))))

  (send-out-unsent-messages-if-in-order! session id target (partial cider/send! session))
  ;; Evict any pending messages do not match this ID (brutal!)
  ;; If we don't do this we get a leak. Could in future aim for a less strict regime
  (swap! session update-in [#'*watched-messages* target] select-keys [id]))
