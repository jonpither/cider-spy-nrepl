(ns cider-spy-nrepl.middleware.cider-spy-hub-test
  (:require [cider-spy-nrepl.hub.client :as client]
            [cider-spy-nrepl.middleware.cider-spy-hub :refer :all]
            [cider-spy-nrepl.middleware.session-vars :refer [*hub-connection-buffer-id* *hub-client* *cider-spy-transport* *desired-alias*]]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
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
(def ^:dynamic *nrepl-session*)

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
  (binding [*nrepl-session* (atom {})
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
               (close! *transport-chan*)))))

(defn- handle-msg [msg]
  (let [handler-fn (fn [msg] (go (>! *handler-chan* msg)))]
    ((wrap-cider-spy-hub handler-fn) (merge msg {:session *nrepl-session*}))))

(use-fixtures :each handler-fixture)

(deftest register-hub-buffer-msg
  (testing "Registering the buffer ID for displaying connection messages in CIDER"
    (handle-msg {:op "cider-spy-hub-register-connection-buffer"
                 :id "hub-buffer-id"
                 :transport *transport*})
    (is (= "hub-buffer-id"
           (@*nrepl-session* #'*hub-connection-buffer-id*)))))

(deftest connect-to-hub
  (testing "Vanilla situation: a connection to hub is established"
    (reset! *nrepl-session* {#'*hub-connection-buffer-id* "hub-buffer-id"
                             #'*cider-spy-transport* *transport*})
    (handle-msg {:op "some-random-op"})
    (test-utils/assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                                    "You are connected to the CIDER SPY HUB"
                                                    "Setting alias on CIDER SPY HUB"])
    (is (first (alts!! [(timeout 2000) *handler-chan*])))))

(deftest re-connect-to-hub
  (reset! *nrepl-session* {#'*hub-client* [nil nil (MockChannel. false)]
                           #'*hub-connection-buffer-id* "hub-buffer-id"
                           #'*cider-spy-transport* *transport*})
  (handle-msg {:op "some-random-op"})

  (test-utils/assert-async-msgs *transport-chan* ["SPY HUB connection closed, reconnecting"
                                                  "Connecting to SPY HUB"
                                                  "You are connected to the CIDER SPY HUB"
                                                  "Setting alias on CIDER SPY HUB"])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-does-nothing-if-connected
  (reset! *nrepl-session* {#'*hub-client* [nil nil (MockChannel. true)]
                           #'*hub-connection-buffer-id* "hub-buffer-id"
                           #'*cider-spy-transport* *transport*})
  (handle-msg {:op "some-random-op"})

  (is (nil? (first (alts!! [(timeout 500) *transport-chan*]))))

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-handles-failure
  (reset! *nrepl-session* {#'*hub-connection-buffer-id* "hub-buffer-id"
                           #'*cider-spy-transport* *transport*})
  (binding [client/connect (fn [& args] (throw (ConnectException.)))]
    (handle-msg {:op "some-random-op"}))

  (test-utils/assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                                  "You are NOT connected to the CIDER SPY HUB"])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest connect-to-hub-handles-no-config
  (reset! *nrepl-session* {#'*hub-connection-buffer-id* "hub-buffer-id"
                           #'*cider-spy-transport* *transport*})
  (binding [settings/hub-host-and-port (constantly nil)]
    (handle-msg {:op "some-random-op"}))

  (test-utils/assert-async-msgs *transport-chan* ["No CIDER-SPY-HUB host and port specified."])

  (is (first (alts!! [(timeout 2000) *handler-chan*]))))

(deftest register-alias-on-hub
  (testing "Vanilla case of an existing connection and session, user just wants to change their alias"
    (reset! *nrepl-session* {#'*hub-client* [nil nil (MockChannel. true)]
                             #'*hub-connection-buffer-id* "hub-buffer-id"
                             #'*cider-spy-transport* *transport*})
    (binding [settings/hub-host-and-port (constantly nil)]
      (handle-msg {:op "cider-spy-hub-alias" :alias "foobar"}))

    (test-utils/assert-async-msgs *transport-chan* ["Setting alias on CIDER SPY HUB to foobar"])
    (is (= {:op :register, :alias "foobar", :session-id nil} (first (alts!! [(timeout 2500) *hub-channel-chan*]))))))

(deftest prepare-alias-on-hub-through-connect-message
  (reset! *nrepl-session* {#'*desired-alias* "foobar2"
                           #'*cider-spy-transport* *transport*})
  (handle-msg {:op "cider-spy-hub-register-connection-buffer"
               :id "hub-buffer-id"
               :transport *transport*})
  (handle-msg {:op "some-random-op"})

  (test-utils/assert-async-msgs *transport-chan* ["Connecting to SPY HUB"
                                                  "You are connected to the CIDER SPY HUB"
                                                  "Setting alias on CIDER SPY HUB to foobar2"]))
