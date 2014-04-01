(ns cider-spy-nrepl.interactions-test
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.middleware.cider :as cider]))

;; TODO - make this nicer and test for unregister event.
;; TODO - Test the summary msg for registrations.
;; TODO - this bypasses all the handler code which is fair enough. Perhaps need a dedicated test?

(defn- send-to-client-events
  "Stub out SEND-BACK-TO-CIDER! and capture what is sent."
  [client-session op & {:as msg}]
  (let [sent-to-client (promise)]
    (binding [cider/send-back-to-cider!
              (fn [transport session-id message-id s]
                (deliver sent-to-client [transport session-id message-id s]))]
      (client-events/process client-session (assoc msg :op op)))
    @sent-to-client))

(defn- process-on-server-and-client-receives
  "Process SERVER-MSG as an incoming msg to the server.
   We expect a msg to be sent back to the client, and for client to send
   a message to CIDER."
  [client-session server-msg]
  (binding [server-events/broadcast-msg!
            (partial send-to-client-events client-session)]
    (let [server-session (atom {:id "fooid"})]
      (server-events/process nil server-session server-msg))))

(deftest registration-should-bubble-to-cider []
  (let [client-session
        (atom {:id "fooid"
               :transport :t1
               :summary-message-id 123})]
    (let [[transport session-id message-id s]
          (process-on-server-and-client-receives
           client-session {:op :register :alias "Jon"})]
         (is (= (:transport @client-session) transport))
         (is (= (:id @client-session) session-id))
         (is (= (:summary-message-id @client-session) message-id))

         (is (re-find #"Devs hacking\n   Jon" s)))))
