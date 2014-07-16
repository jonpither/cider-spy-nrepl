(ns cider-spy-nrepl.interactions-test
  (:use [clojure.core.async :only [chan timeout >!! <!! buffer alts!! go-loop >! close! go]])
  (:require [clojure.test :refer :all]
            [cider-spy-nrepl.hub.server-events :as server-events]
            [cider-spy-nrepl.hub.client-events :as client-events]
            [cider-spy-nrepl.middleware.cider :as cider]
            [clojure.tools.nrepl.transport :as transport]
            [cider-spy-nrepl.hub.register]
            [cheshire.core :as json]
            [clojure.edn])
  (:import [org.joda.time LocalDateTime]
           [io.netty.channel ChannelHandlerContext]))

;; TODO - this bypasses all the handler code which is fair enough. Perhaps need a dedicated test?
;; TODO - wider scope so that everything is client initiated rather than triggering server events

(defmacro spy-harness [& body]
  `(do
     (reset! cider-spy-nrepl.hub.register/sessions {})
     ~@body))

(defn- end-to-end-fixture []
  (let [nrepl-server-chan (chan)
        cider-chan (chan)
        hub-chan (chan)
        hub-session (atom {:channel (reify ChannelHandlerContext
                                      (writeAndFlush [this msg]
                                        (go (>! nrepl-server-chan msg))
                                        nil))})
        nrepl-session (atom {;; :summary-message-id "summary-msg-id" todo use this to ignore connection msgs?
                             :session-started (LocalDateTime.)
                             :transport (reify transport/Transport
                                          (send [_ r]
                                            (go (>! cider-chan r))
                                            nil))})]

    (go-loop []
      (when-let [msg (<! nrepl-server-chan)]
        (client-events/process nrepl-session (clojure.edn/read-string msg))
        (recur))
      (close! cider-chan))

    (go-loop []
      (when-let [m (<! hub-chan)]
        (server-events/process nil hub-session m)
        (recur))
      (close! nrepl-server-chan))

    {:hub-session hub-session
     :nrepl-session nrepl-session
     :cider-chan cider-chan
     :hub-chan hub-chan}))

(defn- cider-msg
  "Utility to extract string message sent to CIDER."
  [cider-chan]
  (when-let [{:keys [value]} (first (alts!! [(timeout 2000) cider-chan]))]
    (json/parse-string value true)))

;; Test to ensure correctness of message sent to CIDER
(deftest registration-should-bubble-to-cider
  (spy-harness
   (let [{:keys [hub-chan cider-chan]} (end-to-end-fixture) ]
     (>!! hub-chan {:op :register :alias "Jon" :session-id 1})

     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan)))))))

(deftest user-registrations
  (spy-harness
   (let [fixture1 (end-to-end-fixture)
         fixture2 (end-to-end-fixture)]

     (>!! (:hub-chan fixture1) {:session-id 1 :op :register :alias "Jon"})
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg (:cider-chan fixture1)))))

     (>!! (:hub-chan fixture2) {:session-id 2 :op :register :alias "Dave"})
     (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}} (:devs (cider-msg (:cider-chan fixture1)))))
     (is (= {:1 {:alias "Jon" :nses []} :2 {:alias "Dave" :nses []}} (:devs (cider-msg (:cider-chan fixture2)))))

     (>!! (:hub-chan fixture2) {:op :unregister})
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg (:cider-chan fixture1))))))))

(deftest dev-locations
  (spy-harness
   (let [{:keys [hub-chan cider-chan]} (end-to-end-fixture)]

     (>!! hub-chan {:session-id 1 :op :register :alias "Jon"})
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan))))

     (>!! hub-chan {:op :location :alias "Jon" :ns "foo" :dt (java.util.Date.)})
     (is (= {:1 {:alias "Jon" :nses ["foo"]}} (:devs (cider-msg cider-chan)))))))

(deftest change-alias
  (spy-harness
   (let [{:keys [hub-chan cider-chan]} (end-to-end-fixture)]
     (>!! hub-chan {:session-id 1 :op :register :alias "Jon"})
     (is (= {:1 {:alias "Jon" :nses []}} (:devs (cider-msg cider-chan))))
     (>!! hub-chan {:session-id 1 :op :register :alias "Jon2"})
     (is (= {:1 {:alias "Jon2" :nses []}} (:devs (cider-msg cider-chan)))))))
