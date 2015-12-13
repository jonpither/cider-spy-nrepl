(ns cider-spy-nrepl.middleware.tooling-session-test
  (:require [cider-spy-nrepl.middleware.tooling-session :refer :all]
            [clojure.test :refer :all]))

(deftest test-tooling-session
  (testing "CIDER defvar nrepl-repl-requires-sexp"
    (is
     (tooling-msg?
      {:op "eval"
       :code "(clojure.core/apply clojure.core/require '[[clojure.repl :refer (source apropos dir pst doc find-doc)] [clojure.java.javadoc :refer (javadoc)] [clojure.pprint :refer (pp pprint)]])"})))

  (testing "CIDER defun cider-completion-complete-core-fn"
    (is
     (tooling-msg?
      {:op "eval"
       :code "(clojure.core/require 'complete.core)"})))

  (testing "CIDER clojure test mode"
    (is
     (tooling-msg?
      {:op "eval"
       :code "(ns clojure.test.mode"})))

  (testing "Non tooling op returns false"
    (is
     (not (tooling-msg?
           {:op "eval"
            :code "(println \"hi\")"})))))
