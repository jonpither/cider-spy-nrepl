(ns cider-spy-nrepl.middleware.summary-test
  (:require [cider-spy-nrepl.middleware.summary :refer :all]
            [clojure.test :refer :all])
  (:import [org.joda.time LocalDateTime]))

(deftest test-show-namespace-summary
  (is (= "Your namespace trail:\n  bob (Am here)\n  user (29 seconds)"
         (sample-summary {:ns-trail (list {:dt (LocalDateTime. 2010 1 1 0 0 30) :ns 'bob}
                                          {:dt (LocalDateTime. 2010 1 1 0 0 1) :ns 'user})}))))

(deftest test-show-function-summary
  (let [code "(println \"hi\")"]
    (is (= (format "Your function calls:\n  %s (1 times)" code)
           (sample-summary {:commands {code 1}})))))

(deftest test-enrich-with-duration
  (is (= '(nil 29) (map :seconds (enrich-with-duration (list {:dt (LocalDateTime. 2010 1 1 0 0 30)}
                                                             {:dt (LocalDateTime. 2010 1 1 0 0 1)}))))))
