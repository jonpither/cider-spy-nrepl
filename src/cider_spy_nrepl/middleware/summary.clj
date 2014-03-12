(ns cider-spy-nrepl.middleware.summary
  (:require [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [cider.nrepl.middleware.util.cljs :as cljs]
            [clojure.pprint])
  (:import [org.joda.time LocalDateTime Seconds]))

(def trail-atom (atom '()))
(def commands-atom (atom '{}))

(defn- safe-inc [v]
  (and (v (inc v)) 1))

(defn track-namespace
  "Add message to supplied tracking."
  [trail {:keys [ns] :as msg}]
  (if (and ns (not= (:ns msg) (-> trail first :ns)))
    (conj trail {:dt (LocalDateTime.) :ns ns})
    trail))

(defn track-command
  "Add message to supplied tracking."
  [command-frequencies {:keys [code] :as msg}]
  (let [forms (read-string (format "(%s)" code))]
    (when (= (count forms) 1)
      (update-in command-frequencies [(first forms)] #(or (and % (inc %)) 1)))))

(defn track-msg! [msg]
  (swap! trail-atom track-namespace msg)
  (swap! commands-atom track-command msg))

(defn- seconds-between [msg1 msg2]
  (.getSeconds (Seconds/secondsBetween (:dt msg1) (:dt msg2))))

(defn enrich-with-duration [msgs]
  (loop [processed '() [msg & the-rest] (reverse msgs)]
    (if (not-empty the-rest)
      (recur (cons (assoc msg :seconds (seconds-between msg (first the-rest))) processed)
             the-rest)
      (cons msg processed))))

(defn- summary-nses [ns-trail]
  (when (not-empty ns-trail)
    (format "Your namespace trail:\n  %s"
            (clojure.string/join "\n  " (map #(format "%s %s" (:ns %)
                                                      (or (and (:seconds %)
                                                               (format "(%s seconds)" (:seconds %))) "(Am here)"))
                                             (enrich-with-duration ns-trail))))))

(defn- summary-functions [command-frequencies]
  (when (not-empty command-frequencies)
    (format "Your function calls:\n  %s"
            (clojure.string/join "\n  " (map (fn [[k v]] (format "%s (%s times)" k v)) command-frequencies)))))

(defn sample-summary
  "Print out the trail of where the user has been."
  [ns-trail command-frequencies]
  (clojure.string/join "\n" (remove empty? [(summary-nses ns-trail) (summary-functions command-frequencies)])))

(defn summary-reply
  [{:keys [transport] :as msg}]
  (transport/send transport (response-for msg :value (sample-summary @trail-atom @commands-atom)))
  (transport/send transport (response-for msg :status :done)))

(defn wrap-info
  "Middleware that looks up info for a symbol within the context of a particular namespace."
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= "summary" op)
      (summary-reply msg)
      (do
        (track-msg! msg)
        (handler msg)))))

(set-descriptor!
 #'wrap-info
 (cljs/maybe-piggieback
  {:handles
   {"summary"
    {:doc "Return a summary of hacking information about the nrepl session."
     :returns {"status" "done"}}}}))
