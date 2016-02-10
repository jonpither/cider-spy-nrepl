(ns cider-spy-nrepl.middleware.cider-spy-multi-repl
  (:require [clojure.tools.nrepl.transport :as nrepl-transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.interruptible-eval :refer [interruptible-eval]]))

(deftype TrackingTransport [transport]
  nrepl-transport/Transport
  (send [this msg]
    (nrepl-transport/send transport msg))
  (recv [this])
  (recv [this timeout]))

(defn wrap-multi-repl
  "Multi REPL Middleware - CURRENTLY NEVER GETS CALLED"
  [handler]
  (println "SETTING")
  (fn [{:keys [transport] :as msg}]
    (println "MULTI-REPL" (:code msg))
    (handler (assoc msg :transport (TrackingTransport. transport)))))

(set-descriptor!
 #'wrap-multi-repl
 {:expects #{#'interruptible-eval}
  :handles {"cider-spy-multi-repl" {:doc "See the cider-spy README"
                                    :returns {} :requires {}}}})
