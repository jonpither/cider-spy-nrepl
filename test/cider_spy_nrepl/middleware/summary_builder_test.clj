(ns cider-spy-nrepl.middleware.summary-builder-test
  (:require [cider-spy-nrepl.middleware.summary-builder :refer :all]
            [clojure.test :refer :all])
  (:import [org.joda.time LocalDateTime]))

(deftest test-show-namespace-summary
  (is (= (list {:ns "bob"} {:ns "user" :seconds 29})
         (:ns-trail (summary {:session-started (LocalDateTime.)
                              :tracking {:ns-trail (list {:dt (LocalDateTime. 2010 1 1 0 0 30) :ns "bob"}
                                                         {:dt (LocalDateTime. 2010 1 1 0 0 5) :ns "user"}
                                                         {:dt (LocalDateTime. 2010 1 1 0 0 1) :ns "user"})}})
                    :session))))

(deftest test-return-empty-list-for-no-namespace-activity
  (testing "Had a problem with (nil) being returned."
    (is (= '()
           (:ns-trail (summary {:session-started (LocalDateTime.)
                                :tracking {}})
                      :session)))))


(deftest test-show-function-summary
  (let [code "(println \"hi\")"]
    (is (= {code 1}
           (:fns (summary {:session-started (LocalDateTime.)
                           :tracking {:commands {code 1}}}))))))

(deftest test-enrich-with-duration
  (is (= '(nil 29)
         (map :seconds (enrich-with-duration
                        (reverse (list {:dt (LocalDateTime. 2010 1 1 0 0 30)}
                                       {:dt (LocalDateTime. 2010 1 1 0 0 1)})))))))
