(ns cider-spy-nrepl.sessions-test
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.hub.client-facade :as client-facade]
            [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.hub.server-events :as server-events]))

;; TODO test someone in a different session gets registered notice
;; TODO rework sleeps with core async, much nicer

(defmacro test-with-server [& forms]
  `(let [~'server (hub-server/start-netty-server :port 9812)]
     (reset! server-events/registrations {})
     (try
       ~@forms
       (finally
         (hub-server/shutdown ~'server)))))

(defmacro test-with-client [alias & forms]
  `(do
     (reset! client-events/registrations #{})
     (let [~'client (client-facade/connect-to-hub! "localhost" 9812 ~alias)]
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
    (is (= #{"jonnyboy"} (set (vals @server-events/registrations))))
    (is (= #{"jonnyboy"} @client-events/registrations))

    (hubc/shutdown! client)

    (Thread/sleep 500)

    (is (= #{} (set (vals @server-events/registrations)))))))
