(ns cider-spy-nrepl.sessions-test
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.hub.client-facade :as client-facade]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.hub.server-events :as server-events]))

;; TODO assert on outcome of shutdown promise
;; TODO test someone in a different session gets registered notice

(defmacro test-with-server [& forms]
  `(let [~'server (hub-server/start-netty-server :port 9812)]
     (reset! server-events/registrations #{})
     (try
       ~@forms
       (finally
         (hub-server/shutdown ~'server)))))

;; This is going to be hairy - a full on async test.
(deftest test-client-should-register-and-receive-update
  (test-with-server
    (reset! client-events/registrations #{})
   (let [client (client-facade/connect-to-hub! "localhost" 9812 "jonnyboy")]

     ;; We have to wait for client to register and process server response asynchronously
     (Thread/sleep 500)

     (is (= #{"jonnyboy"} @client-events/registrations)))))
