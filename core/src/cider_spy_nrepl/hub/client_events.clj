(ns cider-spy-nrepl.hub.client-events
  (:require [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.middleware.interruptible-eval :refer [interruptible-eval]]
            [cider-spy-nrepl.middleware.cider-spy-multi-repl :refer [wrap-multi-repl]]
            [cider-spy-nrepl.middleware.session-vars :refer :all]
            [clojure.tools.nrepl.transport :as nrepl-transport]
            [clojure.tools.nrepl.server :refer [default-handler unknown-op]]
            [cider-spy-nrepl.hub.client :refer [send-async!]]))

(defn- log
  "Used for testing"
  [& args]
  ;; (clojure.tools.logging/info m)
  )

(defmulti process (fn [_ msg] (-> msg :op keyword)))

(defmethod process :default [_ msg]
  (println "Did not understand message from hub" msg))

(defmethod process :connected [session {:keys [alias when-connected]}]
  (log (format "Registered on hub as: %s" alias))
  (swap! (cs-session session) assoc '*hub-connection-details* {:alias alias :when-connected when-connected})
  (cider/send-connected-on-hub-msg! session alias)
  (cider/send-connected-msg! session (format "Registered on hub as: %s" alias))
;;  (cider/update-spy-buffer-summary! session)
  )

(defmethod process :registered [session {:keys [alias registered] :as s}]
  (log (format "Registered: %s" alias))
  (swap! (cs-session session) assoc #'*registrations* registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :unregistered [session {:keys [alias registered]}]
  (log (format "Unregistered: %s" alias))
  (swap! (cs-session session) assoc #'*registrations* registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :location [session {:keys [alias registered]}]
  (log (format "Location change: %s" alias))
  (swap! (cs-session session) assoc #'*registrations* registered)
  (cider/update-spy-buffer-summary! session))

(defmethod process :message [session {:keys [message from recipient]}]
  "Send a message back to CIDER-SPY informing that a msg has been received
   from another developer on the HUB."
  (log (format "Message received from %s to %s: %s" from recipient message))
  (cider/send! session {:id (get @(cs-session session) #'*hub-connection-buffer-id*)
                        :from from
                        :recipient recipient
                        :msg message}))

(defmethod process :start-multi-repl [s _]
  (log (format "Someone is watching!"))
  (swap! (cs-session s) assoc #'*watching?* true)
  (cider/send-connected-msg! s "Someone is watching your REPL!"))

(def eval-handler ((comp #'clojure.tools.nrepl.middleware.session/session #'wrap-multi-repl #'interruptible-eval) unknown-op))

(defmethod process :multi-repl->repl-eval [session {:keys [originator origin-session-id] :as msg}]
  "An eval has been initiated in the multi-REPL, we must propagate this to the foundation REPL."
  (log "Multi-REPL received eval request" msg)
  (when-let [connection-buffer-id (get @(cs-session session) #'*hub-connection-buffer-id*)]
    (cider/send! session (merge msg {:id connection-buffer-id :outside-multi-repl-eval "true" :out (:code msg)}))
    (eval-handler {:op "eval"
                   :id (:id msg)
                   :code (:code msg)
                   :session (-> session meta :id)
                   :interrupt-id nil
                   :transport (get @(cs-session session) #'*cider-spy-transport*)
                   :originator originator
                   :origin-session-id origin-session-id})
    (cider/send-connected-msg! session "Multi-REPL received eval request!")))

(defmethod process :multi-repl->interrupt [session msg]
  "An interupt has been performed in the Multi-REPL."
  (log "REPL interrupt received")
  (eval-handler (assoc msg
                       :op "interrupt"
                       :session (-> session meta :id)
                       :transport (get @(cs-session session) #'*cider-spy-transport*))))

(defmethod process :repl->mult-repl-eval [session {:keys [code target]}]
  "An eval has been initiated in the foundation REPL, we must propagate this to the multi-REPL."
  (log (format "REPL eval received from %s: %s" target code))
  (cider/send! session {:id (get @(cs-session session) #'*hub-connection-buffer-id*) :target target :watch-repl-eval-code code}))

(defn send-out-unsent-messages-if-in-order! [cs-session id target f]
  (let [stored-messages (get-in @cs-session [#'*watched-messages* target id])
        large-sequence-no (apply max (map :cs-sequence (vals stored-messages)))]
    (if (= large-sequence-no (count (vals stored-messages)))
      ;; send all the ones that are pending, in order:
      (doseq [{:keys [cs-sequence sent?] :as msg} (sort-by :cs-sequence (vals stored-messages)) :when (not sent?)]
        (f (-> msg (assoc :target target)))
        (swap! cs-session assoc-in [#'*watched-messages* target id cs-sequence :sent?] true))
      (log "Holding on to message" id large-sequence-no stored-messages))))

(defmethod process :multi-repl-out [session {:keys [origin-session-id id target] :as msg}]
  "Send a message back to CIDER-SPY informing that a eval has been performed
   on a REPL that is being watched."
  (log "REPL out received from" target msg (get @(cs-session session) #'*watch-session-request-id*))

  (let [id-to-use (if (= origin-session-id (-> session meta :id)) id (get @(cs-session session) #'*watch-session-request-id*))]
    (swap! (cs-session session) assoc-in [#'*watched-messages* target id (:cs-sequence msg)] (merge msg {:id id-to-use})))
  (send-out-unsent-messages-if-in-order! (cs-session session) id target (partial cider/send! session))
  ;; Evict any pending messages do not match this ID (brutal!)
  ;; If we don't do this we get a leak. Could in future aim for a less strict regime
  (swap! (cs-session session) update-in [#'*watched-messages* target] select-keys [id]))
