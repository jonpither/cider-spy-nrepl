(ns cider-spy-nrepl.middleware.tracker
  (:import [org.joda.time LocalDateTime]
           [java.io PushbackReader])
  (:require [clojure.tools.namespace.parse]
            [clojure.tools.reader.edn :as edn]
            [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [cider-spy-nrepl.hub.client-facade :as hub-client]
            [clojure.data]))

(defn- safe-inc [v]
  (if v (inc v) 1))

(defn- add-to-ns-trail
  "The user namespace is ignored. If the same ns is given as currently
   at the head, it is also ignored."
  [tracking ns]
  (if (and ns (not= "user" ns))
    (update-in tracking [:ns-trail] conj {:dt (LocalDateTime.) :ns ns})
    tracking))

(defn- track-namespace
  "Add message to supplied tracking."
  [tracking {:keys [ns] :as msg}]
  (add-to-ns-trail tracking (:ns msg)))

(defn- get-ast [ns code-str]
  (binding [ana/macroexpand-1 ana.jvm/macroexpand-1
            ana/create-var    ana.jvm/create-var
            ana/parse         ana.jvm/parse
            ana/var?          var?]
    (ana/analyze (edn/read-string code-str)
                 (assoc (ana.jvm/empty-env) :ns (symbol ns)))))

(defn- track-command
  "Add message to supplied tracking."
  [tracking {:keys [op ns code] :as msg}]
  (let [{:keys [op fn]} (and (not= "load-file" op) ns code
                             (not (re-find #"^clojure\.core/apply clojure.core/require" code))
                             (get-ast ns code))]
    (if fn
      (let [command (format "%s/%s"
                            (-> fn :var meta :ns)
                            (-> fn :var meta :name))]
        (update-in tracking [:commands command] safe-inc))
      tracking)))

(defn- track-load-file [tracking {:keys [op file] :as msg}]
  (if-let [ns (and (= "load-file" op)
                   (second (clojure.tools.namespace.parse/read-ns-decl
                            (PushbackReader. (java.io.StringReader. file)))))]
    (-> tracking
        (update-in [:nses-loaded (str ns)] safe-inc)
        (add-to-ns-trail (str ns)))
    tracking))

(defn- track-msg
  "We track all messages.
   This is helpful for debugging CIDER-SPY-NREPL and may be removed."
  [tracking msg]
  (update-in tracking [:messages] conj msg))

(def trackers
  [#'track-load-file
   #'track-namespace
   #'track-command
   #'track-msg])

(defn- apply-trackers [session msg]
  (update-in session [:tracking]
             #(reduce (fn [tracking f] (f tracking msg)) % trackers)))

(defn track-msg! [msg session]
  (let [old-session @session
        new-session (swap! session apply-trackers msg)]
    (doseq [{:keys [ns dt]}
            (drop (count (get-in old-session [:tracking :ns-trail]))
                  (reverse (get-in new-session [:tracking :ns-trail])))
            :when ns]
      (hub-client/update-location session ns dt))))
