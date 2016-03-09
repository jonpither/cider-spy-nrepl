(ns cider-spy-nrepl.middleware.cider-spy-multi-repl
  (:require [clojure.tools.nrepl.transport :as nrepl-transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [cider-spy-nrepl.hub.client :as hub-client]
            [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.middleware.session]
            [cider-spy-nrepl.middleware.session-vars :refer :all]
            [clojure.tools.nrepl.middleware.interruptible-eval]
            [clojure.tools.nrepl.middleware.load-file]))

(deftype TrackingTransport [session parent-msg sequence-no]
  nrepl-transport/Transport
  (send [this {:keys [value] :as msg}]
    (hub-client/send-async! session (-> msg
                                        (dissoc msg :session)
                                        ;; Attach a sequence number
                                        (assoc :op :multi-repl-out
                                               :cs-sequence (swap! sequence-no inc))
                                        (merge (select-keys parent-msg [:origin-session-id]))))
    (nrepl-transport/send (:transport parent-msg)
                          (if (:originator parent-msg)
                            (assoc msg
                                   :id (@session #'*hub-connection-buffer-id*)
                                   :outside-multi-repl-eval "true"
                                   :originator (:originator parent-msg))
                            msg)))
  (recv [this])
  (recv [this timeout]))

(defn handle-watch
  "This operation is to start watching someone elses REPL"
  [{:keys [id target session] :as msg}]
  (swap! session assoc #'*watch-session-request-id* id)
  (hub-client/send-async! session {:op :start-multi-repl :target target})
  (cider/send-connected-msg! session (str "Sent watching REPL request to target " target)))

(defn handle-eval
  "This operation is to eval some code in another persons REPL"
  [{:keys [id target session] :as msg}]
  (hub-client/send-async! session (-> msg
                                      (dissoc :session :transport :pprint-fn)
                                      (assoc :op :multi-repl->repl-eval
                                             :target target)))
  (cider/send-connected-msg! session (str "Sent REPL eval to target " target)))

(defn handle-interrupt
  "This operation is to eval some code in another persons REPL"
  [{:keys [id target session] :as msg}]
  (hub-client/send-async! session (-> msg
                                      (dissoc :session :transport :pprint-fn)
                                      (assoc :op :multi-repl->interrupt
                                             :target target)))
  (cider/send-connected-msg! session (str "Sent REPL eval to target " target)))

(defn- track-repl-evals
  "Wrap a standard so we can track and distribute msgs"
  [{:keys [op session] :as msg} handler]
  (if (and session (= "eval" op) (@session #'*watching?*))
    (do
      (hub-client/send-async! session (-> msg (assoc :op :repl->mult-repl-eval) (dissoc :session :transport :pprint-fn)))
      (handler (assoc msg :transport (TrackingTransport. session msg (atom 0)))))
    (handler msg)))

(def cider-spy--nrepl-ops {"cider-spy-hub-watch-repl" #'handle-watch
                           "cider-spy-hub-multi-repl-eval" #'handle-eval
                           "cider-spy-hub-multi-repl-interrupt" #'handle-interrupt})

(defn wrap-multi-repl
  "Multi REPL Middleware - CURRENTLY NEVER GETS CALLED"
  [handler]
  (fn [{:keys [op] :as msg}]
    (if-let [cider-spy-handler (get cider-spy--nrepl-ops op)]
      (cider-spy-handler msg)
      (track-repl-evals msg handler))))

(set-descriptor!
 #'wrap-multi-repl
 {:requires #{#'clojure.tools.nrepl.middleware.session/session}
  :expects #{#'clojure.tools.nrepl.middleware.interruptible-eval/interruptible-eval
             #'clojure.tools.nrepl.middleware.load-file/wrap-load-file}
  :handles (zipmap (keys cider-spy--nrepl-ops)
                   (repeat {:doc "See the cider-spy README"
                            :returns {} :requires {}}))})
