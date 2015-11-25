(ns dev
  (:require [clojure.tools.namespace.repl :refer (refresh)]))

(defn run-all-my-tests []
  (refresh)
  (clojure.test/run-all-tests #"cider-spy-nrepl.*test$"))
