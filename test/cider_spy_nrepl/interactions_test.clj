(ns cider-spy-nrepl.interactions-test
  (:use [clojure.core.async :only [chan timeout >!! <!! buffer alts!! go-loop >! close! go]])
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.middleware.cider :as cider]))

;; TODO - this bypasses all the handler code which is fair enough. Perhaps need a dedicated test?
;; TODO - wider scope so that everything is client initiated rather than triggering server events

(defn- run-nrepl-server-stub
  "Process messages on NREPL-SERVER-CHAN delegating to CIDER-NREPL server.
   When the server raises a response We stub out a function as to
   pass a message on to CIDER-CHAN, as though CIDER were receiving the msg."
  [nrepl-server-chan cider-chan]
  (go-loop []
    (when-let [[session msg] (<! nrepl-server-chan)]
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
  [hub-chan nrepl-server-chan]
  (go-loop []
    (when-let [[hub-session nrepl-session m] (<! hub-chan)]
      (binding [server-events/broadcast-msg!
                (fn [op & {:as msg}]
                  (println "server broadcast" msg)
                  (go (>! nrepl-server-chan [nrepl-session (assoc msg :op op)])))]
        (server-events/process nil hub-session m))
      (recur))
    (close! nrepl-server-chan)))

(defmacro spy-harness [& body]
  `(let [~'hub-chan (chan)
         ~'nrepl-server-chan (chan)
         ~'cider-chan (chan)]

     (try
       (run-hub-stub ~'hub-chan ~'nrepl-server-chan)
       (run-nrepl-server-stub ~'nrepl-server-chan ~'cider-chan)

       ~@body

       (finally (close! ~'hub-chan)))))

;; Test to ensure correctness of message sent to CIDER
(deftest registration-should-bubble-to-cider
  (spy-harness
   (let [hub-session (atom {:id "fooid"})
         nrepl-session (atom {:id "fooid"
                              :transport "a-transport"
                              :summary-message-id "summary-msg-id"})]
     (>!! hub-chan [hub-session nrepl-session {:op :register :alias "Jon"}])

     (let [[transport session-id message-id s]
           (first (alts!! [(timeout 2000) cider-chan]))]

       (is (= (:transport @nrepl-session) transport))
       (is (= (:id @nrepl-session) session-id))
       (is (= (:summary-message-id @nrepl-session) message-id))

       (is (re-find #"Devs hacking:\s*Jon" s))))))

(defn- cider-msg
  "Utility to extract string message sent to CIDER."
  [cider-chan]
  (let [[_ _ _ s]
        (first (alts!! [(timeout 4000) cider-chan]))]
    s))

(deftest user-registrations
  (spy-harness
   (>!! hub-chan [(atom {}) (atom {}) {:session-id 1 :op :register :alias "Jon"}])
   (re-find #"Devs hacking:\s*Jon" (cider-msg cider-chan))
   (>!! hub-chan [(atom {:id 2}) (atom {}) {:session-id 2 :op :register :alias "Dave"}])
   (re-find #"Devs hacking:\s*Jon, Dave" (cider-msg cider-chan))
   (>!! hub-chan [(atom {:id 2}) (atom {}) {:op :unregister}])
   (re-find #"Devs hacking:\s*Jon" (cider-msg cider-chan))
))
