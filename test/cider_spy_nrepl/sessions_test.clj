(ns cider-spy-nrepl.sessions-test
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.hub.client-facade :as client-facade]
            [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.hub.register :as register])
  (:import [java.util UUID]))

;; TODO test someone in a different session gets registered notice
;; TODO rework sleeps with core async, much nicer
;; TODO Make very clear tests that cover 2 sessions to same nrepl server
;; TODO test 2 connectons through same nrepl to diff hubs
;;  I should manually test what happenz if 2 diff emacs CIDERs with diff aliases connect to same nrepl-server then on to the hub.

(defmacro test-with-server [& forms]
  `(let [~'server (hub-server/start-netty-server :port 9812)]
     (reset! register/sessions {})
     (try
       ~@forms
       (finally
         (hub-server/shutdown ~'server)))))

(defmacro test-with-client [alias & forms]
  `(do
     (reset! client-events/registrations #{})
     (let [~'session-id (str (UUID/randomUUID))
           ~'client (client-facade/connect-to-hub! "localhost" 9812 ~alias ~'session-id)]
       ;; Allow time for registration message to do a round trip
       (Thread/sleep 500)
       (try
         ~@forms
         (finally
           (hubc/shutdown! ~'client))))))

(deftest test-client-should-register-and-unregister
  (test-with-server
   (test-with-client
    "jonnyboy"
    (is (= #{"jonnyboy"} (set (register/aliases))))
    (is (= #{"jonnyboy"} @client-events/registrations))

    (hubc/shutdown! client)

    (Thread/sleep 500)

    (is (= #{} (set (register/aliases)))))))
