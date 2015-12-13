(ns cider-spy-nrepl.ns-trail-test
  (:require [cider-spy-nrepl.ns-trail :refer :all]
            [clojure.test :refer :all])
  (:import (org.joda.time LocalDateTime)))

(deftest ordered-ns-trail
  (let [now (LocalDateTime. 2010 1 1 0 30 0)
        ns-trail [{:dt (LocalDateTime. 2010 1 1 0 14 0) :ns "boo3"}
                  {:dt (LocalDateTime. 2010 1 1 0 5 0) :ns "boo"}
                  {:dt (LocalDateTime. 2010 1 1 0 26 0) :ns "boo"}
                  {:dt (LocalDateTime. 2010 1 1 0 27 0) :ns "boo"}
                  {:dt (LocalDateTime. 2010 1 1 0 27 0) :ns "boo2"}]]
    (testing "Ns score is assigned based on latest activity"
      (is (= {"boo3" 0.7 "boo" 2.7 "boo2" 1.0}
             (nses-with-score now ns-trail))))
    (testing "Top nseses are those with the highest score"
      (is (= ["boo" "boo2" "boo3"]
             (top-nses now ns-trail))))))
