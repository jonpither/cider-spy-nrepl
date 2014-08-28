(ns cider-spy-nrepl.middleware.sessions-test
  (:require [cider-spy-nrepl.middleware.sessions :refer :all]
            [clojure.test :refer :all]))

(deftest foo-test
  (session! {:session "1"})
  (is (= "1" (:id @(get @sessions "1")))))
