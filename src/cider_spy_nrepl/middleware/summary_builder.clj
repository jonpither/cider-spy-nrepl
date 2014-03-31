(ns cider-spy-nrepl.middleware.summary-builder
  (:import [org.joda.time LocalDateTime Seconds]))

;; TODO: keep summary and tracking encapsulated together
;; Form a protocol

(defn- summary-hackers
  "Summarise the hackers currently with sessions in CIDER HUB."
  [registrations]
  (when (not-empty registrations)
    (format "Devs hacking:\n  %s"
            (clojure.string/join ", " registrations))))

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
  (when session-started
    (format "Session Started %s, uptime: %s seconds."
            (.toString session-started "hh:mm:ss")
            (.getSeconds (Seconds/secondsBetween session-started (LocalDateTime.))))))

(defn summary
  "Build a summary of the users REPL session."
  [{:keys [session-started ns-trail commands files-loaded registrations]}]
  (let [tracking-data (remove empty? [(summary-nses ns-trail)
                             (summary-frequencies "Your function calls:" commands)
                             (summary-frequencies "Your files loaded:" files-loaded)])]
    (if (not-empty tracking-data)
      (clojure.string/join "\n\n"
                           (remove empty?
                                   (concat [(summary-session session-started)
                                            (summary-hackers registrations)] tracking-data)))
      "No Data for Cider Spy.")))
