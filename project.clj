(defproject cider-spy/cider-spy-nrepl "0.1.0-SNAPSHOT"
  :description "Spy on CIDER to get useful REPL summary information."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [cider/cider-nrepl "0.1.0-SNAPSHOT"]
                 [joda-time "2.3"]
                 [io.netty/netty-all "5.0.0.Alpha1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [org.clojure/tools.reader "0.8.4"]
                 [org.clojure/tools.analyzer "0.1.0-beta10"]
                 [org.clojure/tools.analyzer.jvm "0.1.0-beta10"]
                 [cheshire "5.2.0"]]
  :profiles {:dev {:dependencies [[org.clojure/core.async "0.1.278.0-76b25b-alpha"]]}}
  :main cider-spy-nrepl.hub.server
  ;;:profiles
  ;; {:dev {:repl-options {:nrepl-middleware [cider-spy-nrepl.middleware.summary/wrap-info]}
  ;;        }}
)
