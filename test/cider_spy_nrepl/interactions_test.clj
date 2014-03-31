(ns cider-spy-nrepl.interactions-test
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.hub.client-events :as client-events]))

;; to test interactions between server and hub.
;;  I need to be able to trigger events (from server to client).
;;  I want to test the client summary.

;; fix up a channel for the server-events that wires straight in a client-events.

(defn- send-to-client-events [client-session op & {:as msg}]
  (client-events/process))

(deftest registration-should-bubble-to-cider []
  (let [client-session (atom {:id "fooid"})]
    (binding [server-events/broadcast-msg!
              (partial send-to-client-events client-session)]
      (let [server-session (atom {:id "fooid"})]
        (server-events/process nil {:op :register} server-session)))))

;: TODO - test that summary is automatically updated when someone registers / deregisters.
;; TODO - need to test the correct code is called to asynchronously push a message to cider
