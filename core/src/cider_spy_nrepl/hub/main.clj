(ns cider-spy-nrepl.hub.main
  (:gen-class))

(defn -main [& args]
  (let [port (Integer/parseInt (or (first args) "7771"))]
    ;; We eval so that we don't AOT anything beyond this class
    (eval '(do (require 'cider-spy-nrepl.hub.server)

               (let [[b] (start port)]
                 (.sync b))
               (println "Exiting CIDER-SPY HUB")))))
