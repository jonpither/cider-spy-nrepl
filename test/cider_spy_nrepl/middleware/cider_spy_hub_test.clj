(ns cider-spy-nrepl.middleware.cider-spy-hub-test
  (:require [cider-spy-nrepl.test-utils :refer :all]
            [cider-spy-nrepl.middleware.cider-spy-hub :refer :all]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
            [cider-spy-nrepl.hub.client :as client]
            [clojure.core.async :refer [chan timeout >!! <!! buffer alts!! go-loop >! close! go]]
            [clojure.tools.nrepl.transport :as transport]
            [clojure.test :refer :all])
  (:import [java.net ConnectException]))

(def ^:dynamic *handler-chan*)
(def ^:dynamic *transport*)
(def ^:dynamic *transport-chan*)
(def ^:dynamic *hub-channel-chan*)

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

(defn- handler-fn [msg]
  (go (>! *handler-chan* msg)))

(defn- handler-fixture [f]
  (binding [*handler-chan* (chan)
            *transport-chan* (chan)
            *hub-channel-chan* (chan)
            *transport* (reify transport/Transport
                          (send [_ r]
                            (go (>! *transport-chan* (:value r)))))
            client/connect (constantly [nil nil (MockChannel. true)])
            settings/hub-host-and-port (constantly ["some-host" 999])]

    (reset! sessions/sessions {})
    (try
      (f)
      (finally (close! *handler-chan*)
               (close! *transport-chan*)))))

(use-fixtures :each handler-fixture)

(deftest register-hub-buffer-msg
  (testing "Registering the buffer ID for displaying connection messages in CIDER"
    ((wrap-cider-spy-hub handler-fn) {:op "cider-spy-hub-connect"
                                      :id "hub-buffer-id"
                                      :session "bob-id"})
    (is (= "hub-buffer-id" (:hub-connection-buffer-id @(@sessions/sessions "bob-id"))))))

(deftest connect-to-hub
  (testing "Vanilla situation: a connection to hub is established"
    ((wrap-cider-spy-hub handler-fn) {:op "some-random-op"
                                      :session "bob-id"
                                      :transport *transport*})
    (assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                         "You are connected to the CIDER SPY HUB"])
    (is (first (alts!! [(timeout 2000) *handler-chan*])))))

(deftest re-connect-to-hub
  (swap! sessions/sessions assoc "bob-id" (atom {:hub-client [nil nil (MockChannel. false)]
                                                 :transport *transport*}))
  ((wrap-cider-spy-hub handler-fn) {:op "some-random-op"
                                    :session "bob-id"})

  (assert-async-msgs *transport-chan* ["SPY HUB connection closed, reconnecting"
                                       "Connecting to SPY HUB"
                                       "You are connected to the CIDER SPY HUB"])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-does-nothing-if-connected
  (swap! sessions/sessions assoc "bob-id" (atom {:hub-client [nil nil (MockChannel. true)]}))
  ((wrap-cider-spy-hub handler-fn) {:op "some-random-op"
                                    :session "bob-id"
                                    :transport *transport*})

  (is (nil? (first (alts!! [(timeout 500) *transport-chan*]))))

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-handles-failure
  (binding [client/connect (fn [& args] (throw (ConnectException.)))]
    ((wrap-cider-spy-hub handler-fn) {:op "some-random-op"
                                      :session "bob-id"
                                      :transport *transport*}))

  (assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                       "You are NOT connected to the CIDER SPY HUB"])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-handles-no-config
  (binding [settings/hub-host-and-port (constantly nil)]
    ((wrap-cider-spy-hub handler-fn) {:op "some-random-op"
                                      :session "bob-id"
                                      :transport *transport*}))

  (assert-async-msgs *transport-chan* ["No CIDER-SPY-HUB host and port specified."])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest register-alias-on-hub
  (testing "Vanilla case of an existing connection and session, user just wants to change their alias"
    (swap! sessions/sessions assoc "bob-id" (atom {:hub-client [nil nil (MockChannel. true)]
                                                   :transport *transport*}))
    (binding [settings/hub-host-and-port (constantly nil)]
      ((wrap-cider-spy-hub handler-fn) {:op "cider-spy-hub-alias"
                                        :alias "foobar"
                                        :session "bob-id"}))

    (assert-async-msgs *transport-chan* ["Setting alias on CIDER SPY HUB to foobar"])
    (assert-async-msgs *hub-channel-chan* [":op :register"])))

(deftest prepare-alias-on-hub-through-connect-message
  ((wrap-cider-spy-hub handler-fn) {:op "cider-spy-hub-connect"
                                    :hub-alias "foobar2"
                                    :session "bob-id"
                                    :transport *transport*})
  ((wrap-cider-spy-hub handler-fn) {:op "some-random-op"
                                    :session "bob-id"})

  (assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                       "You are connected to the CIDER SPY HUB"
                                       "Setting alias on CIDER SPY HUB to foobar2"]))
