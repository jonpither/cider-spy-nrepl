(ns cider-spy-nrepl.interactions-test
  (:use [clojure.core.async :only [chan timeout >!! <!! buffer alts!! go-loop >! close! go]])
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.middleware.cider :as cider]))

;; TODO - test for unregister event.
;; TODO - this bypasses all the handler code which is fair enough. Perhaps need a dedicated test?
;; TODO - wider scope so that everything is client initiated rather than triggering server events

(defn- run-nrepl-server-stub
  "Process messages on NREPL-SERVER-CHAN delegating to CIDER-NREPL server.
   When the server raises a response We stub out a function as to
   pass a message on to CIDER-CHAN, as though CIDER were receiving the msg."
  [session nrepl-server-chan cider-chan]
  (go-loop []
    (when-let [msg (<! nrepl-server-chan)]
      (binding [cider/send-back-to-cider!
                (fn [transport session-id message-id s]
                  (go (>! cider-chan [transport session-id message-id s])))]
        (client-events/process session msg))
      (recur))
    (close! cider-chan)))

(defn- run-hub-stub
  "Process messages on HUB-CHAN delegating to CIDER-SPY server.
   When the server raises a response We stub out a function as to
   pass a message on to NREPL-SERVER-CHAN, bypassing Netty."
  [session hub-chan nrepl-server-chan]
  (go-loop []
    (when-let [m (<! hub-chan)]
      (binding [server-events/broadcast-msg!
                (fn [op & {:as msg}]
                  (go (>! nrepl-server-chan (assoc msg :op op))))]
        (server-events/process nil session m))
      (recur))
    (close! nrepl-server-chan)))

(defmacro spy-harness [& body]
  `(let [~'hub-chan (chan)
         ~'nrepl-server-chan (chan)
         ~'cider-chan (chan)

         ;; Sessions
         ~'session-on-hub (atom {:id "fooid"})
         ~'session-on-nrepl-server (atom {:id "fooid"})]

     (try
       (run-hub-stub ~'session-on-hub ~'hub-chan ~'nrepl-server-chan)
       (run-nrepl-server-stub ~'session-on-nrepl-server ~'nrepl-server-chan ~'cider-chan)

       ~@body

       (finally (close! ~'hub-chan)))))

(deftest registration-should-bubble-to-cider
  (spy-harness
   (>!! hub-chan {:op :register :alias "Jon"})

   (let [[transport session-id message-id s]
         (first (alts!! [(timeout 2000) cider-chan]))]

     (is (= (:transport @session-on-nrepl-server) transport))
     (is (= (:id @session-on-nrepl-server) session-id))
     (is (= (:summary-message-id @session-on-nrepl-server) message-id))

     (is (re-find #"Devs hacking:\s*Jon" s)))))
