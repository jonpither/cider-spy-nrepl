(ns cider-spy-nrepl.hub.main
  (:gen-class))

(defn -main [& args]
  (def port (Integer/parseInt (or (first args) "7771")))
  ;; We eval so that we don't AOT anything beyond this class, for REPL reloaded reasons
  (eval '(do (require 'cider-spy-nrepl.hub.server)
             (-> cider-spy-nrepl.hub.main/port
                 cider-spy-nrepl.hub.server/start
                 first
                 (.sync)
                 (.channel)
                 (.closeFuture)
                 (.sync))
             (println "Exiting CIDER-SPY HUB"))))
