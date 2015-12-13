(ns dev
  (:require [clojure.tools.namespace.repl :refer (refresh)]
            [cider-spy-nrepl.hub.server :as server]))

(def server nil)

(defn start []
  (alter-var-root #'server
                  (constantly (server/start 7771))))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (when server (server/shutdown server)))

(defn run-all-my-tests []
  (refresh)
  (clojure.test/run-all-tests #"cider-spy-nrepl.*test$"))
