(ns cider-spy-nrepl.hub.client-facade
  (:require [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [clojure.tools.logging :as log]))

(defn- send!
  [bootstrap op msg]
  (when bootstrap
    (hubc/send! bootstrap (assoc msg :op op))))

(defn register [session]
  (let [{:keys [hub-client hub-alias id]} @session]
    (send! hub-client :register {:alias hub-alias :session-id id})))

(defn connect
  "Connect to the hub and immediately register the current session."
  [session host port]
  (try
    (hubc/connect host port session)
    (catch java.net.SocketException e
      (log/warn "Couldn't connect to HUB." host port))))

(defn update-location
  "Update the location of where this developer is on the hub.
   A location is a namespace and a timestamp."
  [session ns ts]
  (send! (:hub-client @session) :location {:ns ns :dt (.toDate ts)}))
