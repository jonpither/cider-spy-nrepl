(ns cider-spy-nrepl.hub.client-facade
  (:require [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.client-events :as client-events]))

;; Need to split the register away from the connect-to-hub!
;; The hub-connection has a different lifecycle/management to individual sessions

(def hub-client (atom nil))

(defn- send!
  ([op msg]
     (send! @hub-client op msg))
  ([bootstrap op msg]
     (hubc/send! bootstrap (assoc msg :op op))))

(defn connect-to-hub! [host port alias session-id]
  (swap! hub-client #(or %
                         (when-let [bootstrap (hubc/connect host port)]
                           (send! bootstrap :register {:alias alias :session-id session-id})
                           bootstrap))))
