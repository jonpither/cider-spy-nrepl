(ns cider-spy-nrepl.middleware.summary
  (:require [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [cider.nrepl.middleware.util.cljs :as cljs]
            [clojure.pprint]
            [cider-spy-nrepl.tracker]
            [cider-spy-nrepl.hub.client-facade :as hub-client]
            [cider-spy-nrepl.hub.client-events :as client-events])
  (:import [org.joda.time LocalDateTime Seconds]))

;; TODO Abstract into a session
(def summary-msg (atom nil))

(defn- summary-hackers
  "Summarise the hackers currently with sessions in CIDER HUB."
  []
  (format "Devs also hacking:\n  %s"
          (clojure.string/join "\n  " @client-events/registrations)))

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
            (clojure.string/join "\n  " (->> ns-trail
                                             enrich-with-duration
                                             (map #(format "%s %s" (:ns %)
                                                           (or (and (:seconds %)
                                                                    (format "(%s seconds)" (:seconds %))) "(Am here)"))))))))

(defn- summary-frequencies [label m]
  (when (not-empty m)
    (format "%s\n  %s"
            label
            (clojure.string/join "\n  " (->> m
                                             (sort-by val)
                                             reverse
                                             (map (fn [[k v]] (format "%s (%s times)" k v))))))))

(defn- summary-session [session-started]
  (format "Session Started %s, uptime: %s seconds."
          (.toString session-started "hh:mm:ss")
          (.getSeconds (Seconds/secondsBetween session-started (LocalDateTime.)))))

(defn sample-summary
  "Print out the trail of where the user has been."
  [session-started ns-trail command-frequencies files-loaded]
  (let [data (remove empty? [(summary-nses ns-trail)
                             (summary-frequencies "Your function calls:" command-frequencies)
                             (summary-frequencies "Your files loaded:" files-loaded)
                             (summary-hackers)])]
    (if (not-empty data)
      (clojure.string/join "\n\n" (cons (summary-session session-started) data))
      "No Data for Cider Spy.")))

(defn- send-summary [transport msg]
  (transport/send transport (response-for msg :value
                                          (sample-summary cider-spy-nrepl.tracker/session-started
                                                          @cider-spy-nrepl.tracker/trail-atom
                                                          @cider-spy-nrepl.tracker/commands-atom
                                                          @cider-spy-nrepl.tracker/files-loaded))))

(defn summary-reply
  "Reply to request for summary information."
  [{:keys [transport hub-host hub-port hub-alias] :as msg}]
  (hub-client/connect-to-hub! hub-host (Integer/parseInt hub-port) hub-alias)
  (reset! summary-msg msg)
  (send-summary transport msg)
  (transport/send transport (response-for msg :status :done)))

(defn- wrap-handler [handler {:keys [transport] :as msg}]
  (let [r (handler msg)]
    (cider-spy-nrepl.tracker/track-msg! msg)
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
