(ns cider-spy-nrepl.utils
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [chan timeout >!! <!! buffer alts!! go-loop >! close! go]]))

(defn assert-async-msgs
  "Take msg patterns, asserts all accounted for in chan"
  [c msgs]
  (let [received-msgs (for [_ msgs]
                        (first (alts!! [(timeout 2000) c])))]
    (doseq [m msgs]
      (is (some (partial re-find (re-pattern m)) (remove nil? received-msgs))
          (str "Could not find msg: " m)))))
