(ns cider-spy-nrepl.middleware.summary-builder
  (:import [org.joda.time LocalDateTime Seconds]))

;; TODO: keep summary and tracking encapsulated together
;; Form a protocol

(defn- seconds-between [msg1 msg2]
  (.getSeconds (Seconds/secondsBetween (:dt msg1) (:dt msg2))))

(defn enrich-with-duration [msgs]
  (when msgs
    (loop [processed '() [msg & the-rest] (reverse msgs)]
      (if (not-empty the-rest)
        (recur (cons (assoc msg :seconds (seconds-between msg (first the-rest))) processed)
               the-rest)
        (cons msg processed)))))

(defn summary
  "Build a summary of the users REPL session."
  [{:keys [session-started registrations tracking]}]
  (let [{:keys [ns-trail commands nses-loaded]} tracking]
    {:ns-trail (->> ns-trail enrich-with-duration (map #(dissoc % :dt)))
     :nses-loaded nses-loaded
     :fns commands
     :devs registrations
     :session {:started (.toString session-started "hh:mm:ss")
               :seconds (.getSeconds (Seconds/secondsBetween session-started (LocalDateTime.)))}}))
