(ns cider-spy-nrepl.middleware.tracker
  (:require [cider-spy-nrepl.hub.client :refer [send-async!]]
            [clojure.tools.analyzer :as ana]
            [cider-spy-nrepl.middleware.session-vars :refer [*tracking* cs-session]]
            [clojure.tools.analyzer.jvm :as ana-jvm]
            [clojure.tools.analyzer.env :as env]
            [clojure.tools.namespace.parse :as clj-tools-namespace-parse]
            [clojure.tools.reader.edn :as edn]
            [environ.core])
  (:import (java.io PushbackReader)
           (org.joda.time LocalDateTime)))

(defn- safe-inc [v]
  (if v (inc v) 1))

(defn- ignore-ns? [ns]
  (some-> :cider-spy-ignore-path environ.core/env re-pattern (re-find ns)))

(defn- add-to-ns-trail
  "The user namespace is ignored. If the same ns is given as currently
   at the head, it is also ignored."
  [tracking ns]
  (if (and ns (not= "user" ns) (not (ignore-ns? ns)))
    (update-in tracking [:ns-trail] conj {:dt (LocalDateTime.) :ns ns})
    tracking))

(defn- track-namespace
  "Add message to supplied tracking."
  [tracking {:keys [ns]}]
  (add-to-ns-trail tracking ns))

(defn- get-ast [ns code-str]
  (binding [ana/macroexpand-1 ana-jvm/macroexpand-1
            ana/create-var    ana-jvm/create-var
            ana/parse         ana-jvm/parse
            ana/var?          var?]
    (env/ensure (ana-jvm/global-env)
                (ana/analyze (edn/read-string code-str)
                             (assoc (ana-jvm/empty-env) :ns (symbol ns))))))

(defn- is-trackeable-msg? [op ns code]
  (and (not-empty ns) (not-empty code) (not= "load-file" op)
       (not (re-find #"^clojure\.core/apply clojure.core/require" code))))

(defn- track-command
  "Add invoked fn to supplied tracking."
  [tracking {:keys [op ns code]}]
  (if-let [fn (and (is-trackeable-msg? op ns code)
                   (:fn (get-ast ns code)))]
    (update-in tracking [:commands (format "%s/%s"
                                           (-> fn :var meta :ns)
                                           (-> fn :var meta :name))] safe-inc)
    tracking))

(defn- track-load-file [tracking {:keys [op file] :as msg}]
  (if-let [ns (and (= "load-file" op)
                   (second (clj-tools-namespace-parse/read-ns-decl
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
;;   #'track-msg
   ])

(defn- apply-trackers [session msg]
  (update-in session [#'*tracking*]
             #(reduce (fn [tracking f] (f tracking msg))
                      %
                      trackers)))

(defn track-msg! [msg session]
  (let [old-session @(cs-session session)
        session-tracked (swap! (cs-session session) apply-trackers msg)
        [old-session-msgs new-session-msgs] (map #(get-in % [#'*tracking* :ns-trail])
                                                 [old-session session-tracked])
        messages-searched (drop (count old-session-msgs)
                                (reverse new-session-msgs))]
    (doseq [{:keys [ns dt]} messages-searched :when ns]
      (send-async! session {:op :location :ns ns :dt (.toDate dt)}))))
