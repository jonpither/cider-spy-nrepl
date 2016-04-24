(ns cider-spy-nrepl.middleware.tracker-test
  (:require [cider-spy-nrepl.middleware.tracker :refer :all]
            [cider-spy-nrepl.middleware.session-vars :refer [*tracking* *cider-spy-session* cs-session]]
            [clojure.test :refer :all]))

(defmacro tracker-harness [& forms]
  `(let [~'session (atom {#'*cider-spy-session* (atom {})})]
     ~@forms))

(deftest test-track-namespace
  (tracker-harness
   (track-msg! {:ns "foo-ns"} session)
   (track-msg! {:ns "foo-ns2"} session)
   (is (= (list "foo-ns2" "foo-ns")
          (map :ns (get-in @(cs-session session) [#'*tracking* :ns-trail]))))))

(deftest test-track-command
  (tracker-harness
   (let [code "(println \"bob\")"]
     (track-msg! {:code code :ns "user"} session)
     (is (= 1 (get-in @(cs-session session) [#'*tracking* :commands "clojure.core/println"])))
     (track-msg! {:code code :ns "user"} session)
     (is (= 2 (get-in @(cs-session session) [#'*tracking* :commands "clojure.core/println"]))))))

(deftest test-do-not-track-nil-fn
  (tracker-harness
   (let [code "(+ 1 1)"]
     (track-msg! {:code code :ns "user"} session)
     (is (nil? (get-in @(cs-session session) [#'*tracking* :commands]))))))

(deftest test-track-ns-loaded
  (tracker-harness
   (let [file "(ns foo.bar) (println \"hi\")"]
     (track-msg! {:op "load-file" :file file} session)
     (is (= 1 (get-in @(cs-session session) [#'*tracking* :nses-loaded "foo.bar"])))
     (is (= (list "foo.bar") (map :ns (get-in @(cs-session session) [#'*tracking* :ns-trail]))))
     (track-msg! {:op "load-file" :file file} session)
     (is (= 2 (get-in @(cs-session session) [#'*tracking* :nses-loaded "foo.bar"]))))))

(deftest test-track-namespace-and-loaded
  (tracker-harness
   (track-msg! {:ns "foo-ns"} session)
   (track-msg! {:op "load-file" :file "(ns foo-ns) (println \"hi\")"} session)
   (is (= (list "foo-ns" "foo-ns")
          (map :ns (get-in @(cs-session session) [#'*tracking* :ns-trail]))))))
