(ns cider-spy-nrepl.connections-test
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.register :as register]
            [cider-spy-nrepl.middleware.cider-spy-hub :as middleware-spy-hub]
            [cider-spy-nrepl.middleware.sessions :as middleware-sessions]
            [clojure.tools.nrepl.transport :as transport])
  (:import [java.util UUID]))

;; TODO worried about never shutting down individual connections to hub
;; TODO test someone in a different session gets registered notice
;; TODO rework sleeps with core async, much nicer
;; TODO Manually test diff emacs CIDERs with diff aliases connect to same nrepl-server then on to the hub.
;; TODO fn on client-facade - other users (uses diff between session-ids)

(defmacro test-with-server [server-name port & forms]
  `(let [~server-name (hub-server/start-netty-server :port ~port)]
     (reset! register/sessions {})
     (try
       ~@forms
       (finally
         (hub-server/shutdown ~server-name)))))

(defmacro test-with-client [client-name session-name alias & forms]
  `(do
     (let [session-id# (str (UUID/randomUUID))]

       ;; Handle a middleware request to connect to CIDER SPY HUB
       ((middleware-spy-hub/wrap-cider-spy-hub nil)
        {:op "cider-spy-hub-connect"
         :hub-host "localhost"
         :hub-port "9812"
         :hub-alias ~alias
         :session session-id#
         :transport (reify transport/Transport
                      (send [_ _]
                        (println "Stubbed sending back to CIDER")))})

       ;; Allow time for registration message to do a round trip
       (Thread/sleep 500)
       (let [~'session (get @middleware-sessions/sessions session-id#)
             ~session-name ~'session
             ~client-name (:hub-client @~'session)]
         (try
           ~@forms
           (finally
             (hubc/shutdown! ~client-name)))))))

(deftest test-client-should-register-and-unregister
  (test-with-server
   server1 9812
   (test-with-client
    client1 session1 "jonnyboy"
    (is (= #{"jonnyboy"} (set (register/aliases))))
    (is (= #{"jonnyboy"} (:registrations @session)))

    (hubc/shutdown! client1)

    (Thread/sleep 500)

    (is (= #{} (set (register/aliases)))))))

(deftest test-two-registrations-and-unsubscribe
  (test-with-server
   server1 9812
   (test-with-client
    client1 session1 "jonnyboy"

    (test-with-client
     client2 session2 "frank"

     (is (= #{"jonnyboy" "frank"} (:registrations @session1)))
     (is (= #{"jonnyboy" "frank"} (:registrations @session2))))

    (Thread/sleep 500)

    (is (= #{"jonnyboy"} (:registrations @session1)))
    (is (= #{"jonnyboy"} (set (register/aliases)))))))

(deftest test-two-registrations-on-different-servers
  (test-with-server
   server1 9812
   (test-with-client
    client1 session1 "jonnyboy"

    (test-with-server
     server2 9813
     (test-with-client
      client2 session2 "frank"

      (is (= #{"jonnyboy"} (:registrations @session1)))
      (is (= #{"frank"} (:registrations @session2)))))

    (Thread/sleep 500)

    (is (= #{"jonnyboy"} (:registrations @session1))))))
