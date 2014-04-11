(ns cider-spy-nrepl.middleware.tracker-test
  (:require [cider-spy-nrepl.middleware.tracker :refer :all]
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
     (is (= (list msg2 msg1)  (-> @session :tracking :messages))))))

(deftest test-track-namespace
  (tracker-harness
   (track-msg! {:ns "foo-ns"} session)
   (track-msg! {:ns "foo-ns2"} session)
   (is (= (list "foo-ns2" "foo-ns") (map :ns (-> @session :tracking :ns-trail))))))

(deftest test-track-command
  (tracker-harness
   (let [code "(println \"bob\")"]
     (track-msg! {:code code :ns "user"} session)
     (is (= 1 (get-in @session [:tracking :commands "clojure.core/println"])))
     (track-msg! {:code code :ns "user"} session)
     (is (= 2 (get-in @session [:tracking :commands "clojure.core/println"]))))))

(deftest test-track-ns-loaded
  (tracker-harness
   (let [file "(ns foo.bar) (println \"hi\")"]
     (track-msg! {:op "load-file" :file file} session)
     (is (= 1 (get-in @session [:tracking :nses-loaded "foo.bar"])))
     (is (= (list "foo.bar") (map :ns (-> @session :tracking :ns-trail))))
     (track-msg! {:op "load-file" :file file} session)
     (is (= 2 (get-in @session [:tracking :nses-loaded "foo.bar"]))))))

(deftest test-track-namespace-and-loaded
  (tracker-harness
   (track-msg! {:ns "foo-ns"} session)
   (track-msg! {:op "load-file" :file "(ns foo-ns) (println \"hi\")"} session)
   (is (= (list "foo-ns") (map :ns (-> @session :tracking :ns-trail))))))
