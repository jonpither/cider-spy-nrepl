(ns cider-spy-nrepl.middleware.cider-spy-multi-repl
  (:require [clojure.tools.nrepl.transport :as nrepl-transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.middleware.interruptible-eval :refer [interruptible-eval]]))

(deftype TrackingTransport [transport]
  nrepl-transport/Transport
  (send [this msg]
    (nrepl-transport/send transport msg))
  (recv [this])
  (recv [this timeout]))

(defn handle-watch
  "This operation is to start watching someone elses REPL"
  [{:keys [target] :as msg}]
  (let [session (sessions/session! msg)]
    (hub-client/watch-repl session target)
    (cider/send-connected-msg! session (str "Sent watching REPL request to target " target))))

;; basically we spy on code and what comes out.
;; We need to send these down to the hub...
;; I think:
(comment
  cider-spy-nrepl.hub.client-facade/send-repl-cmd
  cider-spy-nrepl.hub.client-facade/send-repl-response)

;; easy peasy

(defn- track-repl-evals [{:keys [transport] :as msg} handler]
  (println "MULTI-REPL" (:code msg))
  (handler (assoc msg :transport (TrackingTransport. transport))))

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
 {:expects #{#'interruptible-eval}
  :handles (zipmap (keys cider-spy--nrepl-ops)
                   (repeat {:doc "See the cider-spy README"
                            :returns {} :requires {}}))})
