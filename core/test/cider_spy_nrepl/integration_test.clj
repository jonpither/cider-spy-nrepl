(ns cider-spy-nrepl.integration-test
  (:require [clojure.tools.nrepl.server :as nrserver]
            [cider-spy-nrepl.middleware.cider-spy]
            [cider-spy-nrepl.hub.server :as hub-server]
            [clojure.tools.nrepl.transport :as transport]
            [cider-spy-nrepl.middleware.cider-spy-multi-repl]
            [cider-spy-nrepl.middleware.cider-spy-hub]
            [cider-spy-nrepl.middleware.hub-settings :as hub-settings]
            [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [cheshire.core :as json]))

(defn- start-up-repl-server []
  (let [server
        (nrserver/start-server
         :port 7777
         :handler (nrserver/default-handler
                   ;; #'cider-spy-nrepl.middleware.cider-spy-multi-repl/wrap-multi-repl
                   ;; #'cider-spy-nrepl.middleware.cider-spy-hub/wrap-cider-spy-hub
                   #'cider-spy-nrepl.middleware.cider-spy/wrap-cider-spy))]
    server))

(defn- stop-repl-server [server]
  (nrserver/stop-server server))

(defn- wrap-setup-once [f]
  (let [server (start-up-repl-server)]
    (f)
    (stop-repl-server server)))

(use-fixtures :once wrap-setup-once)

(defn- nrepl-message
  ([timeout tr payload]
   (nrepl/message (nrepl/client tr timeout) payload))
  ([tr payload]
   (nrepl-message 5000 tr payload)))

(deftest test-stc-eval
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        response (nrepl-message transport {:ns "user" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id 14})]
    (is (= ["done"] (:status (second response))))))

(defn- send-and-seq [transport msg]
  (-> (transport/send transport msg)
      (nrepl/response-seq 5000)))

(deftest test-display-a-summary
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        session-id (:new-session (first (nrepl/response-seq (transport/send transport {:op "clone" :id "session-create-id"}) 5000)))]
    (assert session-id)

    (is (= [:devs nil] (-> (transport/send transport {:session session-id :id "session-msg-ig" :op "cider-spy-summary"})
                           (nrepl/response-seq 5000)
                           first
                           :value
                           (json/parse-string keyword)
                           (find :devs))))

    (let [responses (->> {:session session-id :ns "clojure.string" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id 14}
                         (send-and-seq transport))]
      (is (= [{:id 14,
               :ns "clojure.string",
               :session session-id
               :value "2"}
              {:id 14,
               :session session-id,
               :status ["done"]}]
             (take 2 (filter #(= 14 (:id %)) responses))))

      (is (= "clojure.string" (-> responses
                                  (nth 2)
                                  :value
                                  (json/parse-string keyword)
                                  :ns-trail
                                  first
                                  :ns))))))
