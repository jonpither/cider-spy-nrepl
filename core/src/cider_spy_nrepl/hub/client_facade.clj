(ns cider-spy-nrepl.hub.client-facade
  (:require [cider-spy-nrepl.hub.client :as hubc]))

(defn- send!
  [bootstrap op msg]
  (when bootstrap
    (future
      (hubc/send! bootstrap (assoc msg :op op)))))

(defn register [session alias]
  (let [{:keys [hub-client id]} @session]
    (send! hub-client :register {:alias alias :session-id id})))

(defn connect
  "Connect to the hub.
   If a connection cannot be returned, then nil will be passed through to the callback."
  [session host port]
  (try
    (hubc/connect host port session)
    (catch java.net.SocketException e
      nil)))

(defn update-location
  "Update the location of where this developer is on the hub.
   A location is a namespace and a timestamp."
  [session ns ts]
  (send! (:hub-client @session) :location {:ns ns :dt (.toDate ts)}))

(defn send-msg [session recipient message]
  (send! (:hub-client @session) :message {:message message
                                          :recipient recipient}))

(defn watch-repl [session target]
  (send! (:hub-client @session) :watch-repl {:target target}))

(defn forward-repl-output [session out]
  (send! (:hub-client @session) :repl-out {:out out}))

(defn forward-repl-eval [session code]
  (send! (:hub-client @session) :repl-eval {:code code}))
