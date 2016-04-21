(ns cider-spy-nrepl.test-utils
  (:refer-clojure :exclude [sync])
  (:require [cheshire.core :as json]
            [cider-spy-nrepl.hub.server :as hub-server]
            [cider-spy-nrepl.middleware cider-spy-hub-close
             [cider-spy :as spy-middleware]
             [cider-spy-hub :as hub-middleware]
             [hub-settings :as hub-settings]]
            [clojure.core.async :refer [<! >! alts!! buffer chan go-loop timeout]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.session]
            [clojure.tools.nrepl.middleware.interruptible-eval]
            [cider-spy-nrepl.middleware.cider-spy-hub-close]
            [clojure.tools.nrepl
             [server :as nrserver]
             [transport :as transport]]))

(defn wrap-fix-inner-evals [handler]
  (fn [{:keys [session] :as msg}]
    (when session
      (swap! session dissoc #'clojure.tools.nrepl.middleware.interruptible-eval/*msg*))
    (handler msg)))

(set-descriptor!
 #'wrap-fix-inner-evals
 {:requires #{#'clojure.tools.nrepl.middleware.session/session}
  :expects #{#'clojure.tools.nrepl.middleware.interruptible-eval/interruptible-eval}
  :handles {"fix-inner-evals" {:doc "" :returns {} :requires {}}}})

(defn wrap-nuke-sessions [f]
  (reset! cider-spy-nrepl.hub.register/sessions {})
  (f))

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

(deftype TrackingOutboundTransport [underlying]
  clojure.tools.nrepl.transport/Transport
  (send [this msg]
    (when (= (:id msg) "eval-msg")
      (clojure.tools.logging/error "SENDING" (System/identityHashCode this) msg))
    (clojure.tools.nrepl.transport/send underlying msg))
  (recv [this]
    (clojure.tools.nrepl.transport/recv underlying))
  (recv [this timeout]
    (clojure.tools.nrepl.transport/recv underlying timeout))
  java.io.Closeable
  (close [this] (.close underlying)))

(defn debug-stack []
  (println (clojure.tools.nrepl.middleware/linearize-middleware-stack (concat nrserver/default-middlewares
                                                                              [#'wrap-fix-inner-evals
                                                                               #'cider-spy-nrepl.middleware.cider-spy-session/wrap-cider-spy-session
                                                                               #'cider-spy-nrepl.middleware.cider-spy-multi-repl/wrap-multi-repl
                                                                               #'cider-spy-nrepl.middleware.cider-spy-hub/wrap-cider-spy-hub
                                                                               #'cider-spy-nrepl.middleware.cider-spy/wrap-cider-spy
                                                                               #'cider-spy-nrepl.middleware.cider-spy-hub-close/wrap-close]))))

(defn start-up-repl-server
  ([] (start-up-repl-server 7777))
  ([port]
   (let [server
         (nrserver/start-server
          :port port
          :handler (nrserver/default-handler
                    #'wrap-fix-inner-evals
                    #'cider-spy-nrepl.middleware.cider-spy-session/wrap-cider-spy-session
                    #'cider-spy-nrepl.middleware.cider-spy-multi-repl/wrap-multi-repl
                    #'cider-spy-nrepl.middleware.cider-spy-hub/wrap-cider-spy-hub
                    #'cider-spy-nrepl.middleware.cider-spy/wrap-cider-spy
                    #'cider-spy-nrepl.middleware.cider-spy-hub-close/wrap-close)
;;          :transport-fn (fn [socket] (TrackingOutboundTransport. (clojure.tools.nrepl.transport/bencode socket)))
          )]
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

(defn register-user-on-hub-with-summary [port expected-alias]
  (let [transport (nrepl/connect :port port :host "localhost")
        all-msgs-chan (messages-chan! transport)
        hub-chan (chan 100)
        summary-chan (chan 100)
        other-chan (chan 100)]

    (go-loop []
      (when-let [{:keys [id] :as m} (<! all-msgs-chan)]
        (>! (case id
              "session-msg-ig" summary-chan
              "hub-connection-buffer-id" hub-chan
              other-chan) m)
        (recur)))

    ;; Create the session:
    (transport/send transport {:op "clone" :id "session-create-id"})

    (let [session-id (->> other-chan (take-from-chan! 1 1000) first :new-session)]
      (assert session-id)

      ;; Register connection buffer for status messages
      (transport/send transport {:session session-id :id "hub-connection-buffer-id" :op "cider-spy-hub-register-connection-buffer"})
      (assert (->> hub-chan (take-from-chan! 1 1000)))

      ;; Register the summary message
      (transport/send transport {:session session-id :id "session-msg-ig" :op "cider-spy-summary"})
      (assert (->> summary-chan (take-from-chan! 1 1000) first msg->summary :session :started))

      ;; Connect to the hub:
      (transport/send transport {:session session-id :id "connect-msg-id" :op "cider-spy-hub-connect"})

      ;; Ensure connected:
      (let [msgs (->> hub-chan (take-from-chan! 5 10000))]
        (is (= 5 (count msgs)))
        (is (= 4 (count (->> msgs (filter :value)))))
        (is (= expected-alias (->> msgs (filter :hub-registered-alias) first :hub-registered-alias))))

      (let [[msg] (->> summary-chan (take-from-chan! 1 10000))]
        ;; 1 summary message after registration happens
        (is (= msg))
        (let [registered-devs (->> msg msg->summary :devs vals (map :alias) set)]
          (assert (registered-devs expected-alias))
          [transport session-id hub-chan summary-chan other-chan registered-devs]))


      ;; the eval: the user may not be registered when location bit is done, hence competition
      ;; can we not do the eval? Send through an innocous describe (but pull this into a separate test)

      ;; TODO MAKE A SPECIFIC EVAL TEST, KEEP THE SETUP SIMPLE
      ;; Do an eval to prompt connection to the hub:
      #_(transport/send transport (some-eval session-id))
      #_(let [msgs (->> msgs-chan (take-from-chan! 10 10000))]
        (is (= 10 (count msgs)) (count msgs))
        (is (= 2 (count (msgs-by-id "eval-msg" msgs))))
        (is (= 4 (count (->> msgs (msgs-by-id "hub-connection-buffer-id") (filter :value)))))
        ;; 1 after the eval, 1 after location comes back, 1 after registration
        (is (= 3 (count (->> msgs (msgs-by-id "session-msg-ig")))) (count (->> msgs (msgs-by-id "session-msg-ig"))))
        (is (= expected-alias (->> msgs (filter :hub-registered-alias) first :hub-registered-alias)))
        (let [registered-devs (->> msgs
                                   (msgs-by-id "session-msg-ig")
                                   (map msg->summary)
                                   (map :devs)
                                   (mapcat vals)
                                   (map :alias)
                                   (set))]
          (assert (registered-devs expected-alias))
          [transport msgs-chan session-id registered-devs])))))
