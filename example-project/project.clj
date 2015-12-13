(defproject example-project "0.1.0-SNAPSHOT"
  :description "Example project for CIDER-SPY-NREPL"
  :url "https://github.com/jonpither/cider-spy-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :profiles {:dev {:dependencies [[cider-spy/cider-spy-nrepl "0.2.0-SNAPSHOT"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.11"]]
                   :repl-options {:nrepl-middleware [cider-spy-nrepl.middleware.cider-spy/wrap-cider-spy
                                                     cider-spy-nrepl.middleware.cider-spy-hub/wrap-cider-spy-hub
                                                     cider-spy-nrepl.middleware.cider-spy-multi-repl/wrap-multix-repl]}}})
