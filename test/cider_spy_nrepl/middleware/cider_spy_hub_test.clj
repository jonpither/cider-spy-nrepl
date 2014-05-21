(ns cider-spy-nrepl.middleware.cider-spy-hub-test
  (:require [cider-spy-nrepl.middleware.cider-spy-hub :refer :all]
            [cider-spy-nrepl.middleware.sessions :as sessions]
            [clojure.core.async :refer [chan timeout >!! <!! buffer alts!! go-loop >! close! go]]
            [clojure.test :refer :all]))

(def ^:dynamic *handler-chan*)

(defn- handler-fn [msg]
  (go (>! *handler-chan* msg)))

(defn- handler-fixture [f]
  (binding [*handler-chan* (chan)]
    (try
      (f)
      (finally (close! *handler-chan*)))))

(use-fixtures :each handler-fixture)

(deftest register-hub-buffer-msg
  (testing "Registering the buffer ID for displaying connection messages in CIDER"
    ((wrap-cider-spy-hub *handler-fn*) {:op "cider-spy-hub-connect"
                                        :id "hub-buffer-id"
                                        :session "bob-id"})
    (is (= "hub-buffer-id" (:hub-connection-buffer-id @(@sessions/sessions "bob-id"))))
;;    (is (first (alts!! [(timeout 2000) *handler-chan*])))
    ))
