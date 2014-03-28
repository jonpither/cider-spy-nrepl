(ns cider-spy-nrepl.hub.client-facade
  (:require [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.client-events :as client-events]))

(defn- send!
  [bootstrap op msg]
  (hubc/send! bootstrap (assoc msg :op op)))

(defn- hub-connection! [session host port alias bootstrap]
  (or bootstrap
      (when-let [bootstrap (hubc/connect host port session)]
        (send! bootstrap :register {:alias alias :session-id (:id @session)})
        bootstrap)))

(defn connect-to-hub! [host port alias session]
  (:hub-client (swap! session update-in [:hub-client] (partial hub-connection! session host port alias))))
