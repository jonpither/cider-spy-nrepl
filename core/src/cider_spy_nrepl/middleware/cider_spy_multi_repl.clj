(ns cider-spy-nrepl.middleware.cider-spy-multi-repl
  (:require [clojure.tools.nrepl.transport :as nrepl-transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.middleware.session]
            [clojure.tools.nrepl.middleware.interruptible-eval]))

;; TODO possibly reuse the tracker transport
;; TODO capture scenario the forwarding fails and log?

(deftype TrackingTransport [transport session]
  nrepl-transport/Transport
  (send [this {:keys [value] :as msg}]
    (hub-client/forward-repl-output session (dissoc msg :session :id))
    (nrepl-transport/send transport msg))
  (recv [this])
  (recv [this timeout]))

(defn handle-watch
  "This operation is to start watching someone elses REPL"
  [{:keys [id target] :as msg}]
  (let [session (sessions/session! msg)]
    (swap! session assoc :watch-session-request-id id)
    (hub-client/watch-repl session target)
    (cider/send-connected-msg! session (str "Sent watching REPL request to target " target))))

(defn- track-repl-evals [{:keys [transport op code] :as msg} handler]
  (let [session (sessions/session! msg)]
    (if (and (= "eval" op) session (:watching? @session))
      (do
        (hub-client/forward-repl-eval session code)
        (handler (assoc msg :transport (TrackingTransport. transport session))))
      (handler msg))))

(def cider-spy--nrepl-ops {"cider-spy-hub-watch-repl" #'handle-watch})

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
