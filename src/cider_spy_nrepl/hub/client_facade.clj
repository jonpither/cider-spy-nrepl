(ns cider-spy-nrepl.hub.client-facade
  (:require [cider-spy-nrepl.hub.client :as hubc]))

(def hub-client (atom nil))

(defn- send!
  ([op msg]
     (send! @hub-client op msg))
  ([bootstrap op msg]
     (hubc/send! bootstrap (assoc msg :op op))))

(defn connect-to-hub! [alias]
  (swap! hub-client #(or %
                         (let [bootstrap (hubc/connect)]
                           (send! bootstrap :register {:alias alias})
                           bootstrap))))
