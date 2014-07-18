(ns cider-spy-nrepl.interactions-test
  (:use [clojure.core.async :only [chan timeout >!! <!! buffer alts!! go-loop >! close! go]])
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.middleware.cider :as cider]
            [cider-spy-nrepl.hub.client :as client]
            [cider-spy-nrepl.middleware.cider-spy-hub :as hub-middleware]
            [cider-spy-nrepl.middleware.cider-spy :as spy-middleware]
            [clojure.tools.nrepl.transport :as transport]
            [cider-spy-nrepl.hub.register]
            [cheshire.core :as json]
            [clojure.edn])
  (:import [org.joda.time LocalDateTime]
           [io.netty.channel ChannelHandlerContext]))

;; todo reuse code across this and connections test
;; todo get close connection working in the registrations example. This probable needs a close feature implemented.
;; todo clj-refactor clean up the require
;; todo add tests for sending msgs between sessions

(defprotocol CanBeOpen
  (isOpen [this])
  (writeAndFlush [this m])
  (sync [this]))

(defmacro spy-harness [& body]
  `(do
     (reset! cider-spy-nrepl.middleware.sessions/sessions {})
     (reset! cider-spy-nrepl.hub.register/sessions {})
     ~@body))

(defn- stub-connect-to-hub [_ _ nrepl-session]
  (let [hub-session (atom {:channel (reify ChannelHandlerContext
                                      (writeAndFlush [this msg]
                                        (client-events/process nrepl-session (clojure.edn/read-string msg))
                                        nil))})]
    [nil nil (reify CanBeOpen
               (isOpen [this] true)
               (writeAndFlush [this m]
                 (server-events/process nil hub-session (clojure.edn/read-string m))
                 nil)
               (sync [this]
                 this))]))

(defn- foo [session-id alias]
  (let [cider-chan (chan)
        cider-transport (reify transport/Transport
                          (send [_ r]
                            (when-not (= "connection-buffer-msg" (:id r))
                              (go (>! cider-chan (:value r))))))]

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
  (when-let [value (first (alts!! [(timeout 2000) cider-chan]))]
    (json/parse-string value true)))

(deftest alias-should-bubble-to-cider
  (spy-harness
   (let [cider-chan (foo 1 "Jon")]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan))))

     ((hub-middleware/wrap-cider-spy-hub nil)
      {:op "cider-spy-hub-alias"
       :alias "Jon2"
       :session 1})

     (is (= {:1 {:alias "Jon2" :nses []}} (:devs (cider-msg cider-chan)))))))

(deftest user-registrations
  (spy-harness
   (let [cider-chan1 (foo 1 "Jon")]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan1))))
     (let [cider-chan2 (foo 2 "Dave")]
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}} (:devs (cider-msg cider-chan1))))
       (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}} (:devs (cider-msg cider-chan2)))))

     ;; (>!! (:hub-chan fixture2) {:op :unregister})
     ;; (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg (:cider-chan fixture1)))))
)))

(deftest dev-locations
  (spy-harness
   (let [cider-chan (foo 1 "Jon")]
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan))))

     ((spy-middleware/wrap-cider-spy (constantly nil))
      {:op "load-file"
       :file "(ns foo.bar) (println \"hi\")"
       :session 1})

     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan))))
     (is (= {:1 {:alias "Jon" :nses ["foo.bar"]}} (:devs (cider-msg cider-chan)))))))
