(ns cider-spy-nrepl.integration-test
  (:require [cheshire.core :as json]
            [cider-spy-nrepl
             [test-utils :refer :all]]
            [clojure.test :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.transport :as transport]))

(use-fixtures :each wrap-startup-nrepl-server)

(defn- nrepl-message
  ([timeout tr payload]
   (nrepl/message (nrepl/client tr timeout) payload))
  ([tr payload]
   (nrepl-message 5000 tr payload)))

(deftest test-stc-eval
  (let [transport (nrepl/connect :port 7777 :host "localhost")
        response (nrepl-message transport {:ns "user" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id 14})]
    (is (= ["done"] (:status (second response))))))

(deftest test-display-a-summary
  (let [transport (nrepl/connect :port 7777 :host "localhost" :transport-fn non-error-spewing-transport)
        msgs-chan (messages-chan! transport)]

    (transport/send transport {:op "clone" :id "session-create-id"})

    (let [session-id (->> msgs-chan (take-from-chan! 1 1000) first :new-session)]
      (assert session-id)

      (transport/send transport {:session session-id :id "session-msg-ig" :op "cider-spy-summary"})

      (is (= [:devs nil] (-> (->> msgs-chan (take-from-chan! 1 1000))
                             first
                             :value
                             (json/parse-string keyword)
                             (find :devs))))

      (transport/send transport {:session session-id :ns "clojure.string" :op "eval" :code "( + 1 1)" :file "*cider-repl blog*" :line 12 :column 6 :id 14})

      (let [responses (->> msgs-chan (take-from-chan! 3 2000))]
        (is (= [{:id 14,
                 :ns "clojure.string",
                 :session session-id
                 :value "2"}
                {:id 14,
                 :session session-id,
                 :status ["done"]}]
               (take 2 (filter #(= 14 (:id %)) responses))))

        ;; Still intermittent, when the NS doesn't contain anything, think there's a high chance of #21 causing this, would fix that first,
        ;; then try this 20 times or so in succession.

        (is (= "clojure.string" (-> (msgs-by-id "session-msg-ig" responses)
                                    first
                                    :value
                                    (json/parse-string keyword)
                                    :ns-trail
                                    first
                                    :ns)))))))
