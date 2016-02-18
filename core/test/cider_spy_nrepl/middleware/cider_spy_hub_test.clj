(ns cider-spy-nrepl.middleware.cider-spy-hub-test
  (:require [cider-spy-nrepl.hub.client :as client]
            [cider-spy-nrepl.middleware.cider-spy-hub :refer :all]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.test-utils :as test-utils]
            [clojure.core.async :refer [alts!! chan close! go timeout >!]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl.transport :as transport])
  (:import (java.net ConnectException))
  (:refer-clojure :exclude [sync]))

(def ^:dynamic *handler-chan*)
(def ^:dynamic *transport*)
(def ^:dynamic *transport-chan*)
(def ^:dynamic *hub-channel-chan*)
(def ^:dynamic *cider-spy-session*)
(def ^:dynamic *nrepl-middleware-session*)

(defprotocol CanBeOpen
  (isOpen [this])
  (writeAndFlush [this m])
  (sync [this]))

(deftype MockChannel [open?]
  CanBeOpen
  (isOpen [this] open?)
  (writeAndFlush [this m]
    (go (>! *hub-channel-chan* m))
    this)
  (sync [this]
    this))

(defn- handler-fixture [f]
  (let [cider-spy-session (atom {})]
    (binding [*cider-spy-session* cider-spy-session
              *nrepl-middleware-session* (atom {#'cider-spy-nrepl.middleware.sessions/session cider-spy-session})
              *handler-chan* (chan)
              *transport-chan* (chan)
              *hub-channel-chan* (chan)
              *transport* (reify transport/Transport
                            (send [_ r]
                              (go (>! *transport-chan* (:value r)))))
              client/connect (constantly [nil nil (MockChannel. true)])
              settings/hub-host-and-port (constantly ["some-host" 999])]

      (try
        (f)
        (finally (close! *handler-chan*)
                 (close! *transport-chan*))))))

(defn- handle-msg [msg]
  (let [handler-fn (fn [msg] (go (>! *handler-chan* msg)))]
    ((wrap-cider-spy-hub handler-fn) (merge msg {:session *nrepl-middleware-session*}))))

(use-fixtures :each handler-fixture)

(deftest register-hub-buffer-msg
  (testing "Registering the buffer ID for displaying connection messages in CIDER"
    (handle-msg {:op "cider-spy-hub-register-connection-buffer"
                 :id "hub-buffer-id"})
    (is (= "hub-buffer-id"
           (:hub-connection-buffer-id @*cider-spy-session*)))))

(deftest connect-to-hub
  (testing "Vanilla situation: a connection to hub is established"
    (reset! *cider-spy-session* {:hub-connection-buffer-id "hub-buffer-id"
                                 :transport *transport*})
    (handle-msg {:op "some-random-op"})
    (test-utils/assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                                    "You are connected to the CIDER SPY HUB"
                                                    "Setting alias on CIDER SPY HUB"])
    (is (first (alts!! [(timeout 2000) *handler-chan*])))))

(deftest re-connect-to-hub
  (reset! *cider-spy-session* {:hub-client [nil nil (MockChannel. false)]
                               :hub-connection-buffer-id "hub-buffer-id"
                               :transport *transport*})
  (handle-msg {:op "some-random-op"})

  (test-utils/assert-async-msgs *transport-chan* ["SPY HUB connection closed, reconnecting"
                                                  "Connecting to SPY HUB"
                                                  "You are connected to the CIDER SPY HUB"
                                                  "Setting alias on CIDER SPY HUB"])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-does-nothing-if-connected
  (reset! *cider-spy-session* {:hub-client [nil nil (MockChannel. true)]
                               :hub-connection-buffer-id "hub-buffer-id"
                               :transport *transport*})
  (handle-msg {:op "some-random-op"})

  (is (nil? (first (alts!! [(timeout 500) *transport-chan*]))))

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-handles-failure
  (reset! *cider-spy-session* {:hub-connection-buffer-id "hub-buffer-id"
                               :transport *transport*})
  (binding [client/connect (fn [& args] (throw (ConnectException.)))]
    (handle-msg {:op "some-random-op"}))

  (test-utils/assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                                  "You are NOT connected to the CIDER SPY HUB"])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-handles-no-config
  (reset! *cider-spy-session* {:hub-connection-buffer-id "hub-buffer-id"
                               :transport *transport*})
  (binding [settings/hub-host-and-port (constantly nil)]
    (handle-msg {:op "some-random-op"}))

  (test-utils/assert-async-msgs *transport-chan* ["No CIDER-SPY-HUB host and port specified."])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest register-alias-on-hub
  (testing "Vanilla case of an existing connection and session, user just wants to change their alias"
    (reset! *cider-spy-session* {:hub-client [nil nil (MockChannel. true)]
                                 :hub-connection-buffer-id "hub-buffer-id"
                                 :transport *transport*})
    (binding [settings/hub-host-and-port (constantly nil)]
      (handle-msg {:op "cider-spy-hub-alias" :alias "foobar"}))

    (test-utils/assert-async-msgs *transport-chan* ["Setting alias on CIDER SPY HUB to foobar"])
    (test-utils/assert-async-msgs *hub-channel-chan* [":op :register"])))

(deftest prepare-alias-on-hub-through-connect-message
  (reset! *nrepl-middleware-session* {:desired-alias "foobar2"})
  (handle-msg {:op "cider-spy-hub-register-connection-buffer"
               :id "hub-buffer-id"
               :transport *transport*})
  (handle-msg {:op "some-random-op"})

  (test-utils/assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                                  "You are connected to the CIDER SPY HUB"
                                                  "Setting alias on CIDER SPY HUB to foobar2"]))
