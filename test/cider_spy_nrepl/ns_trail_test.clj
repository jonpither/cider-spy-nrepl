(ns cider-spy-nrepl.middleware.ns-trail-test
  (:require [cider-spy-nrepl.ns-trail :refer :all]
            [clojure.test :refer :all])
  (import [org.joda.time LocalDateTime Seconds]))

(deftest footest
  (= [["boo" 2.7] ["boo2" 1.0] ["boo3" 0.7]]
     (top-nses (LocalDateTime. 2010 1 1 0 30 0)
               [{:dt (LocalDateTime. 2010 1 1 0 14 0) :ns "boo3"}
                {:dt (LocalDateTime. 2010 1 1 0 5 0) :ns "boo"}
                {:dt (LocalDateTime. 2010 1 1 0 26 0) :ns "boo"}
                {:dt (LocalDateTime. 2010 1 1 0 27 0) :ns "boo"}
                {:dt (LocalDateTime. 2010 1 1 0 27 0) :ns "boo2"}])))
