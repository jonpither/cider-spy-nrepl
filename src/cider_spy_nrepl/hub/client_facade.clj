(ns cider-spy-nrepl.hub.client-facade
  (:require [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [clojure.tools.logging :as log]))

(defn- send!
  [bootstrap op msg]
  (when bootstrap
    (hubc/send! bootstrap (assoc msg :op op))))

(defn- connect-and-register! [session host port alias]
  (try
    (let [bootstrap (hubc/connect host port session)]
      (when bootstrap
        (send! bootstrap :register {:alias alias :session-id (:id @session)}))
      bootstrap)
    (catch java.net.SocketException e
      (log/warn "Couldn't connect to HUB." host port))))

(defn connect-to-hub!
  "Connect to the hub and immediately register the current session."
  [{:keys [hub-client] :as session} session-atom host port alias]
  (if-not (and hub-client (.isOpen (last hub-client)))
    (assoc session
      :hub-client (connect-and-register! session-atom host port alias)
      :hub-setup {:host host :port port :alias alias})
    session))

(defn update-location
  "Update the location of where this developer is on the hub.
   A location is a namespace and a timestamp."
  [session ns ts]
  (send! (:hub-client @session) :location {:ns ns :dt (.toDate ts)}))
