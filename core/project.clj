(defproject cider-spy/cider-spy-nrepl "0.2.0-SNAPSHOT"
  :description "Spy on CIDER to get useful REPL summary information."
  :url "https://github.com/jonpither/cider-spy-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.5.0"]
                 [cider/cider-nrepl "0.9.1"]
                 [io.netty/netty-all "5.0.0.Alpha2"]
                 [joda-time "2.9.1"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.analyzer "0.6.7"]
                 [org.clojure/tools.analyzer.jvm "0.6.9"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]]
  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}
  :profiles {:dev {:dependencies [[org.clojure/core.async "0.2.374"]]
                   :source-paths ["dev"]}}
  :main cider-spy-nrepl.hub.main)
