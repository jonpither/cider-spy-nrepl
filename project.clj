(defproject cider-spy/cider-spy-nrepl "0.1.0-SNAPSHOT"
  :description "Spy on CIDER to get useful REPL summary information."
  :url "https://github.com/jonpither/cider-spy-nrepl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[cheshire "5.2.0"]
                 [cider/cider-nrepl "0.1.0-SNAPSHOT"]
                 [io.netty/netty-all "5.0.0.Alpha1"]
                 [joda-time "2.3"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.analyzer "0.1.0-beta10"]
                 [org.clojure/tools.analyzer.jvm "0.1.0-beta10"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [org.clojure/tools.reader "0.8.4"]]
  :profiles {:dev {:dependencies [[org.clojure/core.async "0.1.278.0-76b25b-alpha"]]}}
  :main cider-spy-nrepl.hub.server)
