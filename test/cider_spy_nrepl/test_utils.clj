(ns cider-spy-nrepl.test-utils
  (:require [clojure.core.async :refer [alts!! timeout]]
            [clojure.test :refer :all]))

(defn assert-async-msgs
  "Take msg patterns, asserts all accounted for in chan"
  [c msgs]
  (let [received-msgs (for [_ msgs]
                        (first (alts!! [(timeout 2000) c])))]
    (doseq [m msgs]
      (is (some (partial re-find (re-pattern m))
                (remove nil? received-msgs))
          (str "Could not find msg: " m)))))
