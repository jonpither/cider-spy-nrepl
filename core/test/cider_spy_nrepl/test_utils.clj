(ns cider-spy-nrepl.test-utils
  (:refer-clojure :exclude [sync])
  (:require [cheshire.core :as json]
            [cider-spy-nrepl.hub
             [client :as client]
             [server :as hub-server]
             [server-events :as server-events]]
            [cider-spy-nrepl.middleware cider-spy-hub-close
             [cider-spy :as spy-middleware]
             [cider-spy-hub :as hub-middleware]
             [hub-settings :as hub-settings]]
            [clojure.core.async :refer [>! alts!! buffer chan go go-loop timeout]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl
             [server :as nrserver]
             [transport :as transport]])
  (:import io.netty.channel.ChannelHandlerContext))

(defn wrap-setup-alias [alias]
  (fn [f]
    (with-redefs [cider-spy-nrepl.middleware.alias/alias-from-env (constantly "foodude")]
      (f))))

(defn wrap-startup-hub [f]
  (with-redefs [hub-settings/hub-host-and-port (constantly ["localhost" 7778])]
    (let [hub-server (hub-server/start 7778)]
      (try
        (f)
        (finally
          (hub-server/shutdown hub-server))))))

(defn start-up-repl-server
  ([] (start-up-repl-server 7777))
  ([port]
   (let [server
         (nrserver/start-server
          :port port
          :handler (nrserver/default-handler
                    #'cider-spy-nrepl.middleware.cider-spy-session/wrap-cider-spy-session
                    #'cider-spy-nrepl.middleware.cider-spy-multi-repl/wrap-multi-repl
                    #'cider-spy-nrepl.middleware.cider-spy-hub/wrap-cider-spy-hub
                    #'cider-spy-nrepl.middleware.cider-spy/wrap-cider-spy
                    #'cider-spy-nrepl.middleware.cider-spy-hub-close/wrap-close))]
     server)))

(defn stop-repl-server [server]
  (try
    (nrserver/stop-server server)
    (catch Throwable t
      (println "Couldn't stop nrepl server"))))

(defn wrap-startup-nrepl-server [f]
  (let [server (start-up-repl-server)]
    (try
      (f)
      (finally
        (stop-repl-server server)))))

(defn assert-async-msgs
  "Take msg patterns, asserts all accounted for in chan"
  [c msgs]
  (let [received-msgs (for [_ msgs]
                        (first (alts!! [(timeout 2500) c])))]
    (doseq [m msgs]
      (is (some (partial re-find (re-pattern m))
                (remove nil? received-msgs))
          (str "Could not find msg: " m " got:\n" (clojure.string/join " " received-msgs))))))

(defmacro spy-harness [& body]
  `(do
     (reset! cider-spy-nrepl.hub.register/sessions {})
     ~@body))

(defn some-eval [session-id]
  {:session session-id :ns "clojure.string" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id "eval-msg"})

(defn msg->summary [msg]
  (when msg
    (-> msg
        :value
        (json/parse-string keyword))))

(defn msgs-by-id [id msgs]
  (filter #(= id (:id %)) msgs))

(defn alias-and-dev [summary-msg]
  ((juxt (comp set (partial map :alias) vals :devs) (comp :alias :hub-connection)) summary-msg))

(defn messages-chan! [transport]
  (let [c (chan (buffer 100))]
    (go-loop []
      (when-let [v (transport/recv transport Long/MAX_VALUE)]
        (>! c v)
        (recur)))
    c))

(defn take-from-chan! [n seconds c]
  (let [s (atom '[])]
    (loop [n n]
      (when-let [v (and (pos? n) (first (alts!! [c (timeout seconds)])))]
        (swap! s conj v)
        (recur (dec n))))
    @s))
