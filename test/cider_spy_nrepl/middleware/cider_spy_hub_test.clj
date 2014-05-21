(ns cider-spy-nrepl.middleware.cider-spy-hub-test
  (:require [cider-spy-nrepl.middleware.cider-spy-hub :refer :all]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [cider-spy-nrepl.middleware.hub-settings :as settings]
            [cider-spy-nrepl.hub.client :as client]
            [clojure.core.async :refer [chan timeout >!! <!! buffer alts!! go-loop >! close! go]]
            [clojure.tools.nrepl.transport :as transport]
            [clojure.test :refer :all]))

(defprotocol CanBeOpen
  (isOpen [this])
  (writeAndFlush [this m])
  (sync [this]))

(deftype MockChannel [open?]
  CanBeOpen
  (isOpen [this] open?)
  (writeAndFlush [this m]
    this)
  (sync [this]
    this))

(def ^:dynamic *handler-chan*)

(defn- handler-fn [msg]
  (go (>! *handler-chan* msg)))

(defn- handler-fixture [f]
  (binding [*handler-chan* (chan)
            client/connect (constantly [nil nil (MockChannel. true)])
            settings/hub-host-and-port (constantly ["some-host" 999])]
    (reset! sessions/sessions {})
    (try
      (f)
      (finally (close! *handler-chan*)))))

(use-fixtures :each handler-fixture)

(deftest register-hub-buffer-msg
  (testing "Registering the buffer ID for displaying connection messages in CIDER"
    ((wrap-cider-spy-hub handler-fn) {:op "cider-spy-hub-connect"
                                      :id "hub-buffer-id"
                                      :session "bob-id"})
    (is (= "hub-buffer-id" (:hub-connection-buffer-id @(@sessions/sessions "bob-id"))))))

(defn- assert-expected-cider-connection-msgs
  "Take msg patterns, asserts all accounted for in chan"
  [c msgs]
  (let [received-msgs (for [_ msgs]
                        (first (alts!! [(timeout 2000) c])))]
    (doseq [m msgs]
      (is (some (partial re-find (re-pattern m)) received-msgs)))))

(deftest connect-to-hub
  (testing "Vanilla situation: a connection to hub is established"
    (let [c (chan)]
      ((wrap-cider-spy-hub handler-fn) {:op "some-random-op"
                                        :session "bob-id"
                                        :transport (reify transport/Transport
                                                     (send [_ r]
                                                       (go
                                                         (>! c (:value r)))))})
      (assert-expected-cider-connection-msgs c ["Connecting to SPY HUB"
                                                "You are connected to the CIDER SPY HUB"])
      (is (first (alts!! [(timeout 2000) *handler-chan*]))))))
