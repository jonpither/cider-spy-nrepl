(ns cider-spy-nrepl.middleware.cider-spy-multi-repl
  (:require [clojure.tools.nrepl.transport :as nrepl-transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [cider-spy-nrepl.hub.client :as hub-client]
            [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.middleware.session]
            [cider-spy-nrepl.middleware.session-vars :refer [*summary-message-id* *watching?* *watch-session-request-id*]]
            [clojure.tools.nrepl.middleware.interruptible-eval]))

(deftype TrackingTransport [transport session]
  nrepl-transport/Transport
  (send [this {:keys [value] :as msg}]
    (hub-client/send-async! session :repl-out {:msg (dissoc msg :session :id)})
    (nrepl-transport/send transport msg))
  (recv [this])
  (recv [this timeout]))

(defn handle-watch
  "This operation is to start watching someone elses REPL"
  [{:keys [id target session] :as msg}]
  (swap! session assoc #'*watch-session-request-id* id)
  (hub-client/send-async! session :watch-repl {:target target})
  (cider/send-connected-msg! session (str "Sent watching REPL request to target " target)))

;; TODO this will DEFINITELY need a test ASAP:

(defn handle-eval
  "This operation is to eval some code in another persons REPL"
  [{:keys [id target session] :as msg}]
  (hub-client/send-async! session :multi-repl-eval {:target target :msg (dissoc msg :session :transport :pprint-fn)})
  (cider/send-connected-msg! session (str "Sent REPL eval to target " target)))

(defn- track-repl-evals [{:keys [transport op code session] :as msg} handler]
  (if (and session (= "eval" op) (@session #'*watching?*))
    (do
      (hub-client/send-async! session :repl-eval {:code code})
      (handler (assoc msg :transport (TrackingTransport. transport session))))
    (handler msg)))

(def cider-spy--nrepl-ops {"cider-spy-hub-watch-repl" #'handle-watch
                           "cider-spy-hub-multi-repl-eval" #'handle-eval})

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
  :expects #{#'clojure.tools.nrepl.middleware.interruptible-eval/interruptible-eval}
  :handles (zipmap (keys cider-spy--nrepl-ops)
                   (repeat {:doc "See the cider-spy README"
                            :returns {} :requires {}}))})
