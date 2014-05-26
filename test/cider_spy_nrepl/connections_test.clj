(ns cider-spy-nrepl.connections-test
  (:use [clojure.core.async :only [chan timeout <!! alts!! >! go close!]])
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.utils :refer :all]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.hub.client :as hubc]
            [cider-spy-nrepl.hub.register :as register]
            [cider-spy-nrepl.middleware.cider-spy-hub :as middleware-spy-hub]
            [cider-spy-nrepl.middleware.alias :as alias]
            [cider-spy-nrepl.middleware.sessions :as middleware-sessions]
            [cider-spy-nrepl.middleware.hub-settings :as hub-settings]
            [clojure.tools.nrepl.transport :as transport])
  (:import [java.util UUID]))

;; TODO worried about never shutting down individual connections to hub
;; TODO test someone in a different session gets registered notice
;; TODO Manually test diff emacs CIDERs with diff aliases connect to same nrepl-server then on to the hub.
;; TODO fn on client-facade - other users (uses diff between session-ids)

(defmacro test-with-server [server-name port & forms]
  `(let [~server-name (hub-server/start-netty-server :port ~port)]
     (reset! register/sessions {})
     (try
       ~@forms
       (finally
         (hub-server/shutdown ~server-name)))))

(defmacro test-with-client [session-name alias & forms]
  `(do
     (let [~'cider-chan (chan)
           session-id# (str (UUID/randomUUID))
           transport# (reify transport/Transport
                        (send [_ r#]
                          (when-not (re-find #"Connecting to SPY HUB" (:value r#))
                            (go
                              (>! ~'cider-chan (:value r#))))))]
       (binding [hub-settings/hub-host-and-port
                 (fn [] ["localhost" 9812])
                 alias/alias-from-env
                 (constantly ~alias)]

         ;; Handle a middleware request to connect to CIDER SPY HUB
         ((middleware-spy-hub/wrap-cider-spy-hub nil)
          {:op "cider-spy-hub-connect"
           :hub-alias ~alias
           :session session-id#
           :transport transport#})

         ((middleware-spy-hub/wrap-cider-spy-hub (constantly nil))
          {:op "random-op"
           :session session-id#
           :transport transport#}))

       ;; Allow time for connection and registration messages to do a round trip

       (assert-async-msgs
        ~'cider-chan ["You are connected" "Setting alias on CIDER SPY HUB"])

       (let [~'session (get @middleware-sessions/sessions session-id#)
             ~session-name ~'session]
         (try
           ~@forms
           (finally
             (when (:hub-client @~session-name)
               (hubc/shutdown! (:hub-client @~session-name)))
             (close! ~'cider-chan)))))))

(defn- assert-summary-msg-sent-to-cider-with-user-in [cider-chan & users]
  (let [[msg c] (alts!! [cider-chan (timeout 1000)])]
    (is (= cider-chan c))
    (when (= cider-chan c)
      (doseq [user users]
        (is (re-find (re-pattern user) msg))))))

(deftest test-client-should-register-and-unregister
  (test-with-server
   server1 9812
   (test-with-client
    session1 "jonnyboy"

    (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")
    (is (= #{"jonnyboy"} (set (register/aliases))))
    (is (= #{"jonnyboy"} (set (map (comp :alias val) (:registrations @session)))))

    (hubc/shutdown! (:hub-client @session1))

    (Thread/sleep 500)

    (is (= #{} (set (register/aliases)))))))

(deftest test-two-registrations-and-unsubscribe
  (test-with-server
   server1 9812
   (test-with-client
    session1 "jonnyboy"

    (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

    (test-with-client
     session2 "frank"

     ;; Ensure frank registered    ;; Ensure jonnyboy registered
     (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy" "frank")
     (is (= #{"jonnyboy" "frank"} (set (map (comp :alias val) (:registrations @session1)))))
     (is (= #{"jonnyboy" "frank"} (set (map (comp :alias val) (:registrations @session2))))))

    (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")
    (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

    (is (= #{"jonnyboy"} (set (map (comp :alias val) (:registrations @session1)))))
    (is (= #{"jonnyboy"} (set (register/aliases)))))))

(deftest test-two-registrations-on-different-servers
  (test-with-server
   server1 9812
   (test-with-client
    session1 "jonnyboy"

    (assert-summary-msg-sent-to-cider-with-user-in cider-chan "jonnyboy")

    (test-with-server
     server2 9813
     (test-with-client
      session2 "frank"

      (assert-summary-msg-sent-to-cider-with-user-in cider-chan "frank")

      (is (= #{"jonnyboy"} (set (map (comp :alias val) (:registrations @session1)))))
      (is (= #{"frank"} (set (map (comp :alias val) (:registrations @session2)))))))

    (Thread/sleep 500)

    (is (= #{"jonnyboy"} (set (map (comp :alias val) (:registrations @session1))))))))
