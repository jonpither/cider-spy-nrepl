(ns cider-spy-nrepl.middleware.summary-builder-test
  (:require [cider-spy-nrepl.middleware.summary-builder :refer :all]
            [cider-spy-nrepl.middleware.session-vars :refer :all]
            [clojure.test :refer :all])
  (:import (org.joda.time LocalDateTime)))

(deftest test-show-namespace-summary
  (let [expected-list (list {:ns "bob" :seconds nil} {:ns "user" :seconds 29})
        initial-list (list {:dt (LocalDateTime. 2010 1 1 0 0 30)
                            :ns "bob"}
                           {:dt (LocalDateTime. 2010 1 1 0 0 5)
                            :ns "user"}
                           {:dt (LocalDateTime. 2010 1 1 0 0 1)
                            :ns "user"})]
    (is (= expected-list
           (:ns-trail (summary {#'*cider-spy-session* (atom {#'*session-started* (LocalDateTime.)
                                                             #'*tracking* {:ns-trail initial-list}})}))))))

(deftest test-return-empty-list-for-no-namespace-activity
  (testing "Had a problem with (nil) being returned."
    (is (= '()
           (:ns-trail (summary {#'*cider-spy-session* (atom {#'*session-started* (LocalDateTime.)
                                                             #'*tracking* {}})}))))))

(deftest test-show-function-summary
  (let [code "(println \"hi\")"]
    (is (= {code 1}
           (:fns (summary {#'*cider-spy-session* (atom {#'*session-started* (LocalDateTime.)
                                                        #'*tracking* {:commands {code 1}}})}))))))

(deftest test-enrich-with-duration
  (is (= '(nil 29)
         (map :seconds (enrich-with-duration
                        (list {:dt (LocalDateTime. 2010 1 1 0 0 30)}
                              {:dt (LocalDateTime. 2010 1 1 0 0 1)}))))))
