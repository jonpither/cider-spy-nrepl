(ns cider-spy-nrepl.interactions-test
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.middleware.cider :as cider]))

;; to test interactions between server and hub.
;;  I need to be able to trigger events (from server to client).
;;  I want to test the client summary.

;; fix up a channel for the server-events that wires straight in a client-events.

;; TODO test each arg to send-back-to-cider!
(defn- send-to-client-events [client-session op & {:as msg}]
  (binding [cider/send-back-to-cider!
            (fn [transport session-id message-id s]
              (is (= (:transport @client-session) transport))
              (is (= (:id @client-session) session-id))
              (is (= (:summary-message-id @client-session) message-id)))]
    (client-events/process client-session (assoc msg :op op))))

(deftest registration-should-bubble-to-cider []
  (let [client-session
        (atom {:id "fooid"
               :transport :t1
               :summary-message-id 123})]
    (binding [server-events/broadcast-msg!
              (partial send-to-client-events client-session)]
      (let [server-session (atom {:id "fooid"})]
        (server-events/process nil server-session {:op :register})))))

;: TODO - test that summary is automatically updated when someone registers / deregisters.
;; TODO - need to test the correct code is called to asynchronously push a message to cider
