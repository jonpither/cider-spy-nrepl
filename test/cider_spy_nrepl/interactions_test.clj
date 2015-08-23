(ns cider-spy-nrepl.interactions-test
  (:require [cheshire.core :as json]
            [cider-spy-nrepl.hub.client :as client]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.middleware.cider-spy :as spy-middleware]
            [cider-spy-nrepl.middleware.cider-spy-hub :as hub-middleware]
            [clojure.core.async :refer [alts!! chan go timeout]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl.transport :as transport])
  (:import (io.netty.channel ChannelHandlerContext))
  (:refer-clojure :exclude [sync]))

;; todo reuse code across this and connections test

(defprotocol CanBeOpen
  (isOpen [this])
  (writeAndFlush [this m])
  (sync [this])
  (close [this]))

(defmacro spy-harness [& body]
  `(do
     (reset! cider-spy-nrepl.middleware.sessions/sessions {})
     (reset! cider-spy-nrepl.hub.register/sessions {})
     ~@body))

(defn- stub-connect-to-hub [_ _ nrepl-session]
  (let [hub-session
        (atom {:channel
               (reify ChannelHandlerContext
                 (writeAndFlush [this msg]
                   (client-events/process nrepl-session (clojure.edn/read-string msg))
                   nil))})]
    [nil nil (reify CanBeOpen
               (isOpen [this] true)
               (writeAndFlush [this m]
                 (server-events/process nil hub-session (clojure.edn/read-string m))
                 nil)
               (sync [this]
                 this)
               (close [this]
                 (server-events/unregister! hub-session)
                 this))]))

(defn raw-cider-msg
  "Utility to extract string message sent to CIDER."
  [cider-chan]
  (first (alts!! [(timeout 2000) cider-chan])))

(defn- foo [session-id alias]
  (let [cider-chan (chan)
        cider-transport (reify transport/Transport
                          (send [_ r]
                            (when (or (not (= "connection-buffer-msg" (:id r)))
                                      (:msg r))
                              (go (>! cider-chan r)))))]

    ((spy-middleware/wrap-cider-spy nil)
     {:id "summary-buffer-msg"
      :op "cider-spy-summary"
      :session session-id
      :transport cider-transport})

    ;; drain the first summary message
    (raw-cider-msg cider-chan)

    ;; Handle a middleware request to connect to CIDER SPY HUB
    ((hub-middleware/wrap-cider-spy-hub nil)
     {:id "connection-buffer-msg"
      :op "cider-spy-hub-connect"
      :hub-alias alias
      :session session-id
      :transport cider-transport})

    (binding [client/connect stub-connect-to-hub]
      ((hub-middleware/wrap-cider-spy-hub (constantly nil))
       {:op "random-op"
        :session session-id}))

    cider-chan))

(defn- cider-msg
  "Utility to extract string message sent to CIDER."
  [cider-chan]
  (when-let [msg (:value (raw-cider-msg cider-chan))]
    (json/parse-string msg true)))

(deftest alias-should-bubble-to-cider
  (spy-harness
   (let [cider-chan (foo "1" "Jon")]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan))))

     ((hub-middleware/wrap-cider-spy-hub nil)
      {:op "cider-spy-hub-alias"
       :alias "Jon2"
       :session "1"})

     (is (= {:1 {:alias "Jon2" :nses []}} (:devs (cider-msg cider-chan)))))))

(deftest user-registrations
  (spy-harness
   (let [cider-chan1 (foo "1" "Jon")]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan1))))
     (let [cider-chan2 (foo "2" "Dave")]
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}}
              (:devs (cider-msg cider-chan1))))
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}}
              (:devs (cider-msg cider-chan2)))))

     ((hub-middleware/wrap-cider-spy-hub nil)
      {:op "cider-spy-hub-disconnect"
       :session "2"})

     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan1)))))))

(deftest dev-locations
  (spy-harness
   (let [cider-chan (foo "1" "Jon")]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan))))

     ((spy-middleware/wrap-cider-spy (constantly nil))
      {:op "load-file"
       :file "(ns foo.bar) (println \"hi\")"
       :session "1"})

     (is (= {:1 {:alias "Jon" :nses []}}
            (:devs (cider-msg cider-chan))))
     (is (= {:1 {:alias "Jon" :nses ["foo.bar"]}}
            (:devs (cider-msg cider-chan)))))))

(deftest send-messages
  (spy-harness
   (let [cider-chan1 (foo "1" "Jon")]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan1))))
     (let [cider-chan2 (foo "2" "Dave")]
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}}
              (:devs (cider-msg cider-chan1))))
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}}
              (:devs (cider-msg cider-chan2))))

       ((hub-middleware/wrap-cider-spy-hub nil)
        {:op "cider-spy-hub-send-msg"
         :recipient "Dave"
         :from "Jon"
         :message "Hows it going?"
         :session "1"})

       (is (= {:msg "Hows it going?" :from "Jon"}
              (select-keys (raw-cider-msg cider-chan2) [:msg :from])))

       ((hub-middleware/wrap-cider-spy-hub nil)
        {:op "cider-spy-hub-send-msg"
         :recipient "Jon"
         :from "Dave"
         :message "Not bad dude."
         :session "2"})

       (is (= {:msg "Not bad dude." :from "Dave"}
              (select-keys (raw-cider-msg cider-chan1) [:msg :from])))))))
