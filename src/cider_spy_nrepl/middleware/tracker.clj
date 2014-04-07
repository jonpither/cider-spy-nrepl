(ns cider-spy-nrepl.middleware.tracker
  (:import [org.joda.time LocalDateTime]
           [java.io PushbackReader])
  (:require [clojure.tools.namespace.parse]))

(defn- safe-inc [v]
  (if v (inc v) 1))

(defn- add-to-ns-trail [tracking ns]
  (if ns
    (update-in tracking [:ns-trail] conj {:dt (LocalDateTime.) :ns ns})
    tracking))

;; TODO need a decent way of recording where people are "at", a smart trail
(defn- track-namespace
  "Add message to supplied tracking.
   The user namespace is ignored. If the same ns is given as currently
   at the head, it is also ignored."
  [tracking {:keys [ns] :as msg}]
  (if (and ns (not= "user" (:ns msg))
           (not= (:ns msg) (-> tracking :ns-trail first :ns)))
    (add-to-ns-trail tracking ns)
    tracking))

;; TODO commands can be simply changed to functions. Args etc are bullshit.
(defn- track-command
  "Add message to supplied tracking."
  [tracking {:keys [code op] :as msg}]
  (if (and code (not= "load-file" op)
           (not (re-find #"^\(try\n?\s*\(:arglists\n?\s*\(clojure\.core/meta" code))
           (not (re-find #"^\(try\n?\s*\(eval\n?\s*\(quote\n?\s*\(clojure.repl/doc" code))
           (not (re-find #"^\(defn?-? " code))
           (read-string (format "(%s)" code)))
    (update-in tracking [:commands code] safe-inc)
    tracking))

(defn- track-load-file [tracking {:keys [op file] :as msg}]
  (if-let [ns (and (= "load-file" op)
                   (second (clojure.tools.namespace.parse/read-ns-decl
                            (PushbackReader. (java.io.StringReader. file)))))]
    (-> tracking
        (update-in [:nses-loaded ns] safe-inc)
        (add-to-ns-trail ns))
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
  (swap! session apply-trackers msg))
