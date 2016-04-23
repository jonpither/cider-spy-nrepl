(defproject cider-spy/cider-spy-nrepl "0.2.2-SNAPSHOT"
  :description "Multi-person Repl, Code sharing and more."
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
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [environ "1.0.2"]]
  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}
  :profiles {:hub {:dependencies [[org.clojure/tools.logging "0.3.1"]
                                  [ch.qos.logback/logback-classic "1.1.1"]]
                   :resource-paths ["resources"]}
             :dev [:hub {:dependencies [[org.clojure/core.async "0.2.374"]]
                         :source-paths ["dev"]
                         :resource-paths ["test-resources"]}]
             :test [:dev]}
  :main cider-spy-nrepl.hub.main)
