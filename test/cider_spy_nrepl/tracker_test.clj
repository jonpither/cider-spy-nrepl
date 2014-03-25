(ns cider-spy-nrepl.tracker-test
  (:require [cider-spy-nrepl.tracker :refer :all]
            [clojure.test :refer :all]))

(defmacro tracker-harness [& forms]
  `(let [~'session (atom {})]
     ~@forms))

;; Even though I'll remove tracking of whole messages, I'll test for now.
(deftest test-messages
  (tracker-harness
   (let [msg1 {:ns "foo-ns"}
         msg2 {:ns "foo2-ns"}]
     (track-msg! msg1 session)
     (track-msg! msg2 session)
     (is (= (list msg2 msg1) (:messages @session))))))

(deftest test-track-namespace
  (tracker-harness
   (track-msg! {:ns "foo-ns"} session)
   (track-msg! {:ns "foo-ns2"} session)
   (is (= (list "foo-ns2" "foo-ns") (map :ns (:ns-trail @session))))))

(deftest test-track-command
  (tracker-harness
   (let [code "(println \"bob\")"]
     (track-msg! {:code code} session)
     (is (= 1 (get (:commands @session) code)))
     (track-msg! {:code code} session)
     (is (= 2 (get (:commands @session) code))))))

(deftest test-track-file-loaded
  (tracker-harness
   (let [file-path "some-file"]
     (track-msg! {:op "load-file" :file-path file-path} session)
     (is (= 1 (get (:files-loaded @session) file-path)))
     (track-msg! {:op "load-file" :file-path file-path} session)
     (is (= 2 (get (:files-loaded @session) file-path))))))
