(ns cider-spy-nrepl.hub.client-facade
  (:require [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [clojure.tools.logging :as log]))

(defn- send!
  [bootstrap op msg]
  (hubc/send! bootstrap (assoc msg :op op)))

(defn connect-to-hub!
  "Connect to the hub and immediately register the current session."
  [host port alias session]
  (try
    (when-let [bootstrap (hubc/connect host port session)]
      (send! bootstrap :register {:alias alias :session-id (:id @session)})
      bootstrap)
    (catch java.net.SocketException e
      (log/warn "Couldn't connect to HUB." host port))))
