(ns cider-spy-nrepl.middleware.summary
  (:require [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [cider.nrepl.middleware.util.cljs :as cljs]
            [clojure.pprint])
  (:import [org.joda.time LocalDateTime Seconds]))

(def messages (atom '())) ;; Used for debugging

(def summary-msg (atom nil))
(def files-loaded (atom '{}))
(def trail-atom (atom '()))
(def commands-atom (atom '{}))
(def session-started (LocalDateTime.))

(defn- safe-inc [v]
  (if v (inc v) 1))

(defn track-namespace
  "Add message to supplied tracking."
  [trail {:keys [ns] :as msg}]
  (if (and ns (not= (:ns msg) (-> trail first :ns)))
    (conj trail {:dt (LocalDateTime.) :ns ns})
    trail))

(defn track-command
  "Add message to supplied tracking."
  [command-frequencies {:keys [code] :as msg}]
  (let [forms (and code
                   (not (re-find #"^\(try\n?\s*\(:arglists\n?\s*\(clojure\.core/meta" code))
                   (not (re-find #"^\(try\n?\s*\(eval\n?\s*\(quote\n?\s*\(clojure.repl/doc" code))
                   (not (re-find #"^\(defn? " code))
                   (read-string (format "(%s)" code)))]
    (if (= (count forms) 1)
      (update-in command-frequencies [(first forms)] safe-inc)
      command-frequencies)))

;; TODO Need to extract the namespace out of the file being loaded to make this more useful
(defn- track-load-file [files-loaded {:keys [op file-path] :as msg}]
  (if (= "load-file" op)
    (update-in files-loaded [file-path] safe-inc)
    files-loaded))

(defn track-msg! [msg]
  (swap! messages conj msg)
  (swap! trail-atom track-namespace msg)
  (swap! commands-atom track-command msg)
  (swap! files-loaded track-load-file msg))

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

(defn- summary-frequencies [label m]
  (when (not-empty m)
    (format "%s\n  %s"
            label
            (clojure.string/join "\n  " (->> m
                                             (sort-by val)
                                             reverse
                                             (map (fn [[k v]] (format "%s (%s times)" k v))))))))

(defn- summary-functions [command-frequencies]
  (summary-frequencies "Your function calls:" command-frequencies))

(defn- summary-files-loaded [files-loaded]
  (summary-frequencies "Your files loaded:" files-loaded))

(defn- summary-session [session-started]
  (format "Session Started %s, uptime: %s seconds."
          (.toString session-started "hh:mm:ss")
          (.getSeconds (Seconds/secondsBetween session-started (LocalDateTime.)))))

(defn sample-summary
  "Print out the trail of where the user has been."
  [session-started ns-trail command-frequencies files-loaded]
  (let [data (remove empty? [(summary-nses ns-trail)
                             (summary-functions command-frequencies)
                             (summary-files-loaded files-loaded)])]
    (if (not-empty data)
      (clojure.string/join "\n\n" (cons (summary-session session-started) data))
      "No Data for Cider Spy.")))

(defn- send-summary [transport msg]
  (transport/send transport (response-for msg :value (sample-summary session-started @trail-atom @commands-atom @files-loaded))))

(defn summary-reply
  [{:keys [transport] :as msg}]
  (reset! summary-msg msg)
  (send-summary transport msg)
  (transport/send transport (response-for msg :status :done)))

(defn- wrap-handler [handler {:keys [transport] :as msg}]
  (let [r (handler msg)]
    (track-msg! msg)
    (when (Boolean/valueOf (:auto-refresh @summary-msg))
      (send-summary transport @summary-msg))
    r))

(defn wrap-info
  "Middleware that looks up info for a symbol within the context of a particular namespace."
  [handler]
  (fn [{:keys [op] :as msg}]
    (if (= "summary" op)
      (summary-reply msg)
      (wrap-handler handler msg))))

(set-descriptor!
 #'wrap-info
 (cljs/maybe-piggieback
  {:handles
   {"summary"
    {:doc "Return a summary of hacking information about the nrepl session."
     :returns {"status" "done"}}}}))
